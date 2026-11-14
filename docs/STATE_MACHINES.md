# ADHD List: state machines

This file covers every state machine in the app, whether explicit (a sealed class) or implicit (spread across flags, channels, Room and Firestore). For each one it lists the states, the invariants and the transitions. It also records the invalid states the code could actually reach, the evidence for each, and how each was fixed and tested.

Paths are relative to `app/src/main/java/com/example/adhdlist/` (written `M/`). Functions are named instead of line numbers so the references stay valid as the code changes.

History:
* **Bug-fix pass:** commit `80c5731` on branch `fix/state-machine-bugs`.
* **Feature pass:** branch `feature/shared-lists-redesign` added shared lists, reordering, and the AuDHD redesign. That pass found and fixed more state bugs (§1 N4, §2 F1, §4 D3/M1, §8 R1/R2/R6/R7).

Status legend: **FIXED** (regression test named), **OPEN** (documented, not fixed), **NOT A BUG** (checked and safe).

---

## 0. The event pipeline every screen shares

```
UI callback ─► vm.triggerCommand(Command)        validates, viewModelScope.launch
            ─► _actions: Channel (rendezvous)    received by a LaunchedEffect in the screen
            ─► vm.handleAction(Action)           viewModelScope.launch
            ─► private fun / use case            ─► repository ─► Room (local list) or Firestore (shared list)
```

* A command is **validated at one moment and executed at a later one**. Every executor that relies on a precondition checks it again when it runs.
* Drag steps are the one exception to this pipeline. `moveList` and `moveTask` are called directly, because the reorder library needs the list to change in the same frame. Only the drop goes through the pipeline (`Command.SaveOrder`).
* Errors and confirmations go through their own channels, and each is shown as a long-duration snackbar.

---

## 1. Navigation back stack (implicit; NavController)

`M/MainActivity.kt`, `M/data/navigation/NavigationRepository.kt`

Invariants:
* **N-INV1:** `lists` is always at the bottom of the back stack.
* **N-INV2:** at most one `tasks` entry exists at a time.
* **N-INV3:** navigating never changes the `TaskList` object that is displayed.
* **N-INV4:** one activity and one NavController. `NavigationRepository` is a process singleton holding a single controller, so `MainActivity` is `singleTask`, and join links and shared texts arrive through `onNewIntent` instead of starting a second copy on top of the sending app.

| From | Event | Guard | To |
|---|---|---|---|
| Lists | tap a list card | `currentDestination.route == "lists"` | Tasks(L) |
| Tasks | ← arrow or system back | `previousBackStackEntry != null` | Lists |
| Lists | system back | — | the app closes (normal Android behavior) |
| any | `adhdlist://join/<code>` link or invite text shared into the app | — | Lists + the join dialog (§8) |
| Tasks(L) | L is deleted or left | `getListData` sees `null` → back | Lists |

The route argument is the whole `TaskList` serialized with Gson and passed through `Uri.encode`. Transitions are 200 ms fades.

**Fixed:**
* **N4:** with `singleTop`, opening a join link from another app could create a second `MainActivity`, so taps in one copy drove the other copy's navigation. Now `singleTask`.
* **N1:** a double tap on ← emptied the back stack, leaving a blank screen.
* **N2:** a double tap on a list pushed two tasks screens.
* **N3:** navigating mutated the displayed list's name.

Tests: `NavigationRepositoryTest` (3), and the UI tests `doubleTapOnBackArrowLandsOnLists` and `nameWithUrlCharactersSurvivesNavigation`.

---

## 2. Text input (implicit)

`M/presentation/common/AddTextField.kt`, `CommonUi.kt` (`TextInputDialog`)

There are three independent text buffers, and none of them shares state with another:

| Buffer | Owner | Cleared when |
|---|---|---|
| New list name | `ListsViewModel.newListName` | the create action takes it, before the insert; it is put back if the insert fails |
| New task text | `TasksViewModel.newTaskMessage` | the same: taken before the insert, put back on failure |
| Rename / edit item / join code | the dialog's own `remember` state | the dialog closes |

Invariants:
* **T-INV1:** the add field shows exactly what will be submitted, because it is controlled by `value`.
* **T-INV2:** a rename never reads or writes the add-task buffer.
* **T-INV3:** list names are never blank and are trimmed, on create and on rename.
* **T-INV4:** one submit produces at most one write.

| From | Event | Effect |
|---|---|---|
| add field, blank | + (or keyboard Done for list names) | "List name cannot be empty." / "Type something first." snackbar |
| add field, text | + (or keyboard Done for list names) | take the text, clear the field, insert. Item text can span lines: Enter adds a new line there. Test: `multiLineItemsKeepTheirLinesWhenAddedAndEdited` |
| Tasks | ✎ | rename dialog opens, prefilled with the current name, with focus in the field; Save is disabled while blank |
| rename dialog | Save | `RenameList(name)`: validated, trimmed, written; the title updates from Room |
| rename dialog | Cancel or back | nothing changes |
| item row | tap | ticks or unticks it |
| item row | press and hold | edit dialog with the item's text, plus a short vibration; Save sends `EditItem(task, text)`: blank, too long or unchanged text writes nothing |
| list card | press and hold | rename dialog for that list (`ListsViewModel.Command.RenameList`) |

**Fixed:**
* **T1:** cancelling a rename created a task named after the list. The old shared buffer (Edit mode) is gone, so this can't happen by construction.
* **T2:** a draft task was lost when starting a rename.
* **T3:** a blank list name was accepted.
* **T4 and L1:** a double tap on + inserted twice.
* **T5:** rotating during a rename reset the edit to a stale name. Edit mode no longer exists.
* **F1 (feature pass):** opening the rename or join dialog crashed the app with "FocusRequester is not initialized". Focus was requested before the dialog window had composed its field. The request now lives inside the dialog's own composition.

Tests: `TasksViewModelTest` (rename ×3, edit item ×2, create ×2, too long ×1), `ListsViewModelTest` (create, rename), and the UI tests `renameHappensInADialogAndNeverLeaksIntoTheAddField` (it found F1), `pressAndHoldAnItemEditsItsText`, `pressAndHoldAListRenamesIt` and `multiLineItemsKeepTheirLinesWhenAddedAndEdited`.

---

## 3. Lists screen (implicit)

| Event | Effect |
|---|---|
| Tap a card | opens the list |
| Press and hold a card | rename dialog |
| Swipe a card past 60% of its width | the card springs back and a dialog asks first: **Delete** for a local list, **Leave** for a shared one |
| Confirm | `DeleteList`; a shared list is left instead (§8) |
| Drag the handle | the visible order changes live; the drop saves positions (§9) |
| Join list | the join dialog opens (§8) |

Each card shows the name, a shared icon, "x of y ticked", and a progress bar. All of it comes from a single Room query (`ListDao.getAllLists`, which returns `ListWithProgress`).

---

## 3b. List screen and its ⋮ menu (implicit)

`M/presentation/view/tasks/layout/TasksScreen.kt`, `TaskRow.kt`

| Event | Effect |
|---|---|
| Tap an item | ticks or unticks it |
| Press and hold an item | edit dialog (§2) |
| Swipe an item past 60% | deletes it, with Undo (§6) |
| Drag the handle | reorder (§9) |
| ✎ | rename dialog (§2) |
| 👤+ (only when Firebase is configured) | share (§8) |

The first ⋮ entry is a small state machine driven by the items on screen:

| State | Condition | Entry shown | Tap |
|---|---|---|---|
| SomeTicked | at least one item ticked | **Uncheck all** | confirm dialog "Uncheck everything?" (the ticked count is in the text). Cancel changes nothing. Confirm unticks every item → NoneTicked |
| NoneTicked | items exist, none ticked | **Check all** | ticks every item at once, without a dialog → SomeTicked |
| Empty | no items | neither entry | — |

The second entry is always **Delete everything**, which asks first.

* **Invariant:** progress (ticks) is never lost on a single tap. Unticking everything always goes through a dialog.
* Both entries write one value to every item: `TaskRepository.setAllTasks(listId, checked)`, a single `UPDATE` in Room or one Firestore batch for a shared list. Repeating either is harmless.

Tests: `TasksViewModelTest.check all ticks every item in this list`, the refresh test, and the UI test `uncheckAllAsksFirstThenTheMenuOffersCheckAll`. The UI test covers Cancel keeping the tick, Confirm unticking, the entry flipping to "Check all", and Check all ticking.

---

## 4. Persisted data (Room v4)

`M/data/model/*.kt`, `M/data/room/**`

```
List:  (absent) ─create─► Local ─share─► Shared(remoteId) ─leave─► (absent here; it stays for the others)
       Local ─delete─► (absent)  ⇒ cascade-deletes its tasks
Task:  (absent) ─create─► Unchecked ⇄ Checked ─delete─► (absent) ─undo─► restored with the same id and position
       any ─edit text─► same state, new message    all items ─Check all / Uncheck all (confirmed)─► all Checked / all Unchecked
```

Columns added in v4: `position` (both tables) and `remote_id` (both tables).

**Migration 3→4** (`TaskDatabase.MIGRATION_3_4`):
* It adds the columns, then seeds `position` from the old ids, which keeps today's insertion order.
* Before this, the database used `fallbackToDestructiveMigration()` with no migrations, so a version bump would have **wiped every user's data**.
* The schema is now exported to `app/schemas/` for future migrations.
* `fallbackToDestructiveMigration()` is removed. Git history shows v3 is the oldest schema ever shipped, so there is no older version to fall back from. A future migration that is forgotten now crashes in testing instead of silently deleting live users' lists.
* **Rule for future changes:** bump `version`, add a `Migration`, and add a case to `MigrationTest`.

Invariants:
* **D-INV1:** every task belongs to a live list (foreign key with CASCADE).
* **D-INV2:** equality covers every column. All columns are constructor properties of the data classes.
* **D-INV3:** list and task order comes from `position`, with `id` as the tiebreaker.

**Fixed:**
* **D1:** a fast double tap on a checkbox could lose a toggle.
  * Now the row shows the new state at once, through `remember(task.state)` local state.
  * It sends a *new* row with `task.copy(state = ...)` instead of mutating Room's object.
  * Test: `checking a task writes a new row and leaves the shown one alone`.
* **D2:** opening a list that was being deleted crashed the app with an NPE.
  * Now `getListData` navigates back when the row disappears.
  * Test: `list deleted while its screen is open...`.
* **D3 (feature pass): reachable now.**
  * `id`, `position` and `remoteId` were declared in the class body, so `equals()` ignored them. StateFlow and Compose drop values that are `==` to the current one, so a list that only gained a `remoteId` kept looking unshared, and a reorder could be dropped.
  * Found by `SharedListSyncTest`: the "Shared list" label never appeared.
  * Fix: those fields moved into the constructors. The schema stays the same, and `copy()` now keeps ids, which also removes the old trap where `copy()` reset `id` to 0.
* **M1:** existing users would lose all their data on the version bump. The migration above fixes this. Test: `MigrationTest`, which builds the exact v3 schema Room generated, fills it, and migrates without any destructive fallback.

### Retries and repeated delivery

Nothing in the app retries a write automatically. Firestore queues writes to shared lists itself and sends them when the device is back online. Only the sync *listeners* retry (§8).

| Write | Safe to repeat? | User retry after a failure |
|---|---|---|
| Create list / task | one write per submit | the text is put back in the field |
| Rename | the same value written twice is harmless | the dialog opens with the current name |
| Edit item text | yes: unchanged text writes nothing, and the same text twice is harmless | press and hold again |
| Delete task | yes, and Undo restores the same id and position | swipe again |
| Delete / leave list | yes | swipe again |
| Check / uncheck | last write wins | tap again |
| Uncheck all / check all / delete all | yes: each sets the same value on every item | menu again |
| Reorder | yes: it writes the full order | drag again |
| Join | yes: `arrayUnion` | the dialog again |

---

## 5. Feed subscriptions (implicit)

| Feed | Source | Guard |
|---|---|---|
| Lists screen | `ListDao.getAllLists` → `StateFlow` | `feedJob`: one collector per ViewModel |
| Tasks | `TaskDao.getAllTasksFromList` → `StateFlow` | `tasksJob` |
| List title and shared flag | `ListDao.getList` → `StateFlow<TaskList?>` | `listJob` |

**Fixed:**
* **S1:** a new collector started on every return to the lists screen, and the old ones were never stopped.
* **S2:** refresh could overwrite newer state with a stale snapshot.
* **S3:** a list-data collector was added on every rotation. Fixed by the `StateFlow` and the guard.

Tests: the ViewModel subscription and refresh tests.

---

## 6. Swipe rows (Material 3 `SwipeToDismissBox`)

| Row | Released past the threshold | Afterwards |
|---|---|---|
| List card | the card springs back and the confirm dialog opens | nothing is deleted until the user confirms |
| Task row | dismissed, task deleted | an "Undo" snackbar shows for the long duration; Undo re-inserts the task with the same id and position |

"Past the threshold" means **60% of the row's width** (`rememberDeliberateSwipeState` in `CommonUi.kt`).
* Material's default is a fixed 56 dp, which an accidental brush reaches.
* Material also dismisses on a quick flick of any length, so the distance is checked again in `confirmValueChange`.
* The background stays muted until the swipe is far enough, then turns the delete colour, so it is visible when letting go will delete.

Test: `aShortOrQuickSwipeDoesNotDelete`.

The undo snackbar runs in the **screen's** coroutine scope. A row's own scope ends when the row is removed, which would cancel the snackbar.

Tests:
* UI: `swipingAListAsksBeforeDeleting`, `deletedTaskComesBackWithUndo`.
* The existing `DeleteListTest` now confirms the dialog.

---

## 7. Errors (implicit)

`handleError` sets `errorMessage`, and a long snackbar shows it. Every error now has a specific message:
* empty name or item
* item too long (over 1000 characters, the shared-list limit)
* invalid join code
* join failed
* share failed
* "Something went wrong" for anything else

Load errors in `getTasks` and `getListData` are now reported instead of swallowed (formerly E3). A rename is no longer optimistic: the title only changes when Room changes (formerly E2).

---

## 8. Shared lists: local ⇄ Firestore (new)

`M/data/remote/SharedListsRemote.kt`, `M/data/sync/SharedListsSync.kt`, `M/data/sync/MirrorPlan.kt`, `firebase/firestore.rules`

**Model:**
* Firestore stores `lists/{code}` as `{name, members:[uid]}` and `lists/{code}/tasks/{id}` as `{message, state, position}`.
* The 20-character random document id is the join code.
* Each person uses an anonymous Firebase account, so there is no sign-in screen. The account persists across launches.

**Single source for the UI:** the screens read only Room.
* Writes to a shared list go **only to Firestore**.
* `SharedListsSync` mirrors Firestore snapshots back into Room.
* Firestore applies writes to its local cache immediately, so the mirror also updates the UI while offline.
* Writing to Room as well would race the mirror and could insert a task twice.

```
Local ─Share─► [Room: remoteId set] ─► [Firestore batch: list+tasks, pending] ─server ack─► Shared(confirmed)
(absent) ─Join(code)─► arrayUnion(me) awaited ─► myLists snapshot ─► mirror inserts list ─► tasks listener
Shared ─Leave─► markLeft(code) + arrayRemove(me) + delete locally
Shared ─removed elsewhere / list gone─► mirror deletes local copy (only if seen before)
```

Invariants:
* **R-INV1:** every shared list has exactly one local row (keyed by `remote_id`), and every remote task has exactly one local task row.
* **R-INV2:** a list shared a moment ago is never deleted by an older snapshot. Deletion requires the id to be in `seen`, and only ids **confirmed by the server** are added to `seen`.
* **R-INV2b:** a share is all or nothing. `createList` is awaited; if the server refuses it, `shareList` clears the `remoteId`s again in one transaction, so the list stays local and intact.
* **R-INV2c:** task text is at most 1000 characters, the same limit as the rules. Otherwise a long task in a shared list would appear and then vanish.
* **R-INV3:** a list that was just left is never re-inserted by an older snapshot. Lists in `left` are ignored until the user joins that list again (`markJoined`).
* **R-INV4:** a tasks listener starts only when the list is `confirmed`, meaning the server has it and not just the local cache.
* **R-INV5:** a listener that fails is retried every 5 s while its list is still in `myLists`.

| Transition | Code | Order matters because… |
|---|---|---|
| Share | `ListRepository.shareList` | Room gets the `remoteId`s *before* the Firestore batch. Otherwise the snapshot arrives first and the mirror inserts duplicates |
| Join | `ListRepository.joinList` | the write is awaited (15 s timeout) so the user gets a clear success or failure |
| Leave | `ListRepository.deleteList` | `markLeft` comes first (R-INV3) |
| Task writes | `TaskRepository.*` | each write chooses Room or Firestore by the list's `remoteId`; a check sends only `state` and an edit only `message`, so two people editing different fields don't overwrite each other |
| Reorder | `TaskRepository.reorderTasks` | Room *and* Firestore are written, so the drop shows at once. This is safe because it updates existing rows only |

**Fixed during the feature pass:**
* **R1: a just-shared list's tasks never synced.**
  * Cause: the `myLists` snapshot included the list while it was still pending. The tasks listener started at once, and the server rejected it with `PERMISSION_DENIED` because the list didn't exist there yet. Firestore then stopped that listener for good.
  * Fix: R-INV4 (listen with `MetadataChanges.INCLUDE` and wait for `confirmed`), plus R-INV5.
  * Found by `SharedListSyncTest`.
* **R2: sharing a list with tasks was rejected by the rules.**
  * Cause: task rules used `get()`, which sees the database *before* the batch, so the list didn't exist yet.
  * Fix: write rules use `getAfter()`.
  * Test: rules test `sharing: the list and its tasks can be created in one batch`.

* **R6: a share the server refused deleted the list and all its tasks.**
  * Sequence: `remoteId` was set in Room, and the batch was sent without waiting. The pending doc appeared in the cache, and its id was added to `seen`. The server refused the batch and the doc left the cache. The mirror then treated it as "deleted elsewhere" and deleted the list, which cascaded to its tasks.
  * Realistic triggers: Firestore created before the rules are deployed, or a task over 1000 characters.
  * Fix: R-INV2, R-INV2b and R-INV2c.
  * Test: `SharedListSyncTest.aShareTheServerRejectsLeavesTheListLocalAndIntact`.
* **R7: after leaving, joining the same list again showed nothing until the app restarted.** The `left` set was never cleared.
  * Fix: `markJoined` in `joinList`.
  * Test: `joiningUnderTheRealRulesThenLeavingAndRejoining`. This is also the first test of the joiner's side under the real rules.

**Security rules** (`firebase/firestore.rules`, 9 tests in `firebase/test/rules.test.mjs`):
* Signed-out users can do nothing.
* Creating a list is allowed only with yourself as the sole member.
* Queries return only your own lists.
* Reading a single list by its code is allowed; that is how joining works.
* A non-member may only add *themselves* to `members`.
* A member may rename the list or remove themselves. A member cannot remove others or delete the list.
* Tasks are readable and writable only by members, and must have the right shape: message ≤ 1000 characters, a bool state, an int position.

**OPEN:**
* **R3: no way to revoke a code.**
  * Anyone with the code can join, and members can't remove others.
  * A list nobody belongs to any more stays in Firestore as an orphan.
  * Fix if needed: an owner field, member removal, or a Cloud Function that cleans up empty lists.
* **R4: sharing and joining need a connection.** Anonymous sign-in has to reach the server once, and both actions wait for the server's answer. After that, edits work offline.
* **R8: a slow share can make a duplicate.** If the share times out (15 s) but the server still accepts it later, the list stays local and *also* arrives as a shared copy. Nothing is lost.
* **R5: the `adhdlist://` link is not clickable in some messengers (WhatsApp).** The recipient can share the message into the app or type the code instead. A tappable `https://` link would need Android App Links on a domain you own.

Tests:
* `MirrorPlanTest` (3): the Firestore → Room diff.
* `SharedListSyncTest` (3): two people end to end against the emulator.
  * Share, upload, a remote add and rename arriving, a local check reaching the cloud, a local edit reaching the cloud with the check intact, and a remote delete arriving.
  * A rejected share.
  * Join, leave and rejoin under the real rules.
* Join codes are read from the text after `join/` or `code:` first, so a 20-character list name in the invite text isn't mistaken for the code. Test: `ListsViewModelTest`.

---

## 9. Reorder (new)

```
Idle ─handle drag─► Dragging (VM list reordered live: moveList / moveTask) ─drop─► SaveOrder ─► positions = index
```

* List order is per device and never synced.
* Task order is shared: Room and Firestore are both written.
* A remote order change while someone is dragging is overwritten by that person's drop. Last drop wins.

Tests:
* `drag then drop saves the new order` (both ViewModels).
* UI `dragHandleReordersTasksAndTheOrderIsSaved`: drags with the real handle, leaves the screen and comes back, then checks the order is loaded from the database.

---

## 10. Design decisions for AuDHD (what changed and why)

| Decision | Why |
|---|---|
| The original ADHD List palette: yellow (#FDD835) top bar, + buttons and checkboxes with near-black content (≈13:1 contrast), neutral grey surfaces; progress bars use a deeper gold in light mode, where plain yellow on grey is hard to see; no wallpaper-based colors | familiar to existing users; the app looks the same every time |
| Larger text and line height | easier to scan |
| A progress bar and "x of y ticked" on every list and at the top of each list; "All ticked. Nice work." | visible progress gives motivation without noise |
| The whole item row toggles the checkbox; press and hold edits (with a short vibration); 48 dp handles; a 56 dp + button. Items can span several lines, so Enter adds a new line and + adds the item; keyboard Done adds list names | big, forgiving tap targets; one gesture per meaning (tap ticks, hold edits) |
| Confirm before deleting or leaving a list; Undo after deleting an item; "Delete everything" asks first; swipe-to-delete only fires past 60% of the row | mistakes can be undone and nothing destructive happens by surprise |
| Text-labelled overflow menu instead of unlabeled icons. One entry flips between "Uncheck all" (shown while anything is ticked; asks first, because it throws away progress) and "Check all" (shown once nothing is ticked). "Delete everything" also asks first | clear meaning, no guessing; nothing that loses progress happens on one tap |
| On-screen words say "item" / "something", never "task" | it is a list for anything (shopping, packing, things to remember), not only to-dos. Code still calls rows `Task` |
| Short fades instead of 500 ms slides | movement that is easy to follow |
| Long snackbars | enough time to read and react |

---

## Tests

* **JVM**, `./gradlew :app:testDebugUnitTest`: 29 tests. `TasksViewModelTest` (14), `ListsViewModelTest` (7), `MirrorPlanTest` (3), `NavigationRepositoryTest` (3), `ExampleUnitTest` (2).
* **Instrumented**, `./gradlew :app:connectedDebugAndroidTest`: 18 tests.
  * `StateMachineRegressionTest` (11), `SharedListSyncTest` (3), `MigrationTest`, the existing `DeleteListTest` and `DeleteTaskTest`, plus the template `ExampleInstrumentedTest`.
  * Run them on an emulator only (`ANDROID_SERIAL=emulator-5554`). They clear the app's data and uninstall it on **every** connected device, including a plugged-in phone.
  * `SharedListSyncTest` runs only with `-PfirebaseEmulatorHost=10.0.2.2` and `npm run emulators`; otherwise it is skipped.
  * The test APK excludes Espresso's old `protobuf-lite`, which otherwise shadows Firestore's protobuf and crashes the run.
* **Security rules**, `cd firebase && npm test`: 9 tests.
* **Harness flake (fixed):** `BaseUiTest` cleared the DB after the activity had started, so a test could tap the previous test's stale row. Now a rule ordered before the activity rule clears it first.
* **Emulator isolation:** with `firebaseEmulatorHost` set, the app uses a separate named Firebase app for the `demo-adhdlist` project, even when `google-services.json` is present. Test data can't reach the real project.
* **Real Firebase:** the automated sync tests run only against the local emulator. Sharing on the real `adhdlist` project was checked by hand: a phone and an emulator shared "Ćwiczenia".
* **Not covered:** rotation, TalkBack, and accessible reordering. The drag handle has a content description but no accessibility actions, so screen-reader users can't reorder yet.
