# ADHD List: state machines

This file covers every state machine in the app, whether explicit (a sealed class) or implicit (spread across flags, channels and the DB). For each one it lists the states, the invariants and the transitions. It also records the invalid states the code could actually reach, the evidence for each, and how each was fixed and tested.

Paths are relative to `app/src/main/java/com/example/adhdlist/` (written `M/`). Line numbers refer to the fixed code. "Original" means commit `ad96453`.

Status legend: **FIXED** (regression test named), **OPEN** (documented, not fixed), **NOT A BUG** (checked and safe).

---

## 0. The event pipeline every screen shares

Each user event goes through the same asynchronous chain:

```
UI callback ─► vm.triggerCommand(Command)        validates, viewModelScope.launch
            ─► _actions: Channel (rendezvous)    received by a LaunchedEffect in the screen
            ─► vm.handleAction(Action)           viewModelScope.launch
            ─► private fun (createTask, ...)     viewModelScope.launch ─► suspend Room call
```

Consequences that every machine below inherits:

* A command is **validated at one moment and executed at a later one**, with at least one main-looper hop in between, plus the Room round trip. Two fast taps can both pass validation before either of them executes. Every executor that relies on a precondition must therefore check it again when it runs.
* Commands sent while the screen is not collecting `actions` (it is away, or between compositions) wait suspended in `send` and run later.
* Errors go through a second rendezvous channel. `handleError` sets `errorMessage`, and the screen then shows a snackbar.

---

## 1. Navigation back stack (implicit; NavController)

`M/MainActivity.kt`, `M/data/navigation/NavigationRepository.kt`

| State | Back stack |
|---|---|
| `Lists` | `[lists]` (start destination) |
| `Tasks(L)` | `[lists, tasks/{json(L)}]` |
| `Empty` (**invalid**) | `[]`: NavHost renders nothing, so the app shows a blank screen |
| `Tasks(L)·Tasks(L)` (**invalid**) | two task entries stacked |

Invariants:
* **N-INV1:** the back stack always contains `lists` at its bottom.
* **N-INV2:** at most one `tasks` entry exists at a time.
* **N-INV3:** navigating never changes the `TaskList` object that the lists screen displays.

| From | Event | Guard (fixed code) | To |
|---|---|---|---|
| Lists | tap row (`NavigateToList`) | `currentDestination.route == "lists"` (`NavigationRepository.kt:24`) | Tasks(L) |
| Tasks | tap row again (a second tap while the first navigation is in flight) | guard fails → ignored | Tasks |
| Tasks(Normal) | system back | `BackHandler` → `NavigateBack` | Lists |
| Tasks(Edit) | system back | `BackHandler` → `ChangeUiState(Normal)` | Tasks(Normal) |
| Tasks | toolbar ← arrow | `previousBackStackEntry != null` (`NavigationRepository.kt:31`) | Lists |
| Lists | ← arrow pressed again while the tasks screen is still sliding out | guard fails → ignored | Lists |
| Lists | system back | the activity finishes (normal Android behavior) | (app closed) |

How the argument travels: `Gson(list)` is encoded with `Uri.encode` and becomes the route path segment. Navigation then calls `Uri.decode`, and `MainActivity.kt:77` parses the result with `gson.fromJson`. The `TaskList` received on the tasks screen is a snapshot of the list at navigation time.

### Bugs

**N1: a double tap on the ← arrow empties the back stack (blank screen). FIXED, issue 1. Severity: high.**
* Evidence: originally `popNavigation()` called `popBackStack()` without a guard. The arrow `IconButton` (`TasksScreen.kt:69`) remains composed and clickable while the 500 ms exit transition runs. Its `onClick` is not lifecycle-aware.
* What happens: the second tap pops `lists`, which leaves `[]` and a white screen. Only killing the app recovers from it.
* Reproduced on the emulator with the original code: the UI test fails with "could not find ListsTextField".
* System back does **not** trigger this. `BackHandler` registers against the nav entry's lifecycle, so it unregisters as soon as the entry is popped. I confirmed this in logcat: only one `NavigatingBack` was logged, and the second press finished the activity.
* Fix: pop only when an entry exists below the current one.
* Tests: `NavigationRepositoryTest.double back press does not pop the start destination`, and the UI test `StateMachineRegressionTest.doubleTapOnBackArrowLandsOnLists`.

**N2: a double tap on a list row pushes two tasks screens. FIXED, issue 1. Severity: medium.**
* Evidence: originally `navigateToList` had no guard. The row's `clickable` (`TaskListListElement.kt:122`) stays live during the transition.
* Fix: navigate only from `lists`.
* Test: `NavigationRepositoryTest.double tap on a list navigates once`.

**N3: navigating changed the displayed list's name in place (double encoding). FIXED, issue 1. Severity: medium.**
* Evidence: originally the code ran `list.name = Uri.encode(list.name)` on the item object from `ListsViewModel`'s feed.
* Result: the row in the lists screen showed `a%20b` while sliding out. A second navigation (N2) encoded the name twice, so the tasks title showed `a%20b`.
* Fix: encode the serialized JSON and leave the object alone.
* Test: `NavigationRepositoryTest.navigating does not mutate the list shown on the lists screen`.

---

## 2. Tasks screen mode × input buffer (explicit `UiState` + implicit text buffer)

`M/presentation/view/tasks/viewmodel/TasksViewModel.kt`, `M/presentation/view/tasks/layout/TasksScreen.kt`, `M/presentation/common/AddTextField.kt`

State = (`uiState ∈ {Normal, Edit}`, `newTaskMessage: String`, the text the field displays).

Invariants:
* **T-INV1:** the field displays exactly `newTaskMessage`, so what the user sees is what gets submitted. This now holds by construction: `AddTextField` is controlled by `value`.
* **T-INV2:** in `Normal`, the buffer holds only text the user typed as a task, never the prefilled list name.
* **T-INV3:** a list name, once stored, is never blank and is trimmed. This matches list creation.
* **T-INV4:** one submit produces at most one write.

| From | Event | Effect (fixed code) | To |
|---|---|---|---|
| Normal, buf | type | `buf = text` | Normal |
| Normal, buf blank | + (`AddButtonClick`) | `EmptyNewTask` error → snackbar | Normal |
| Normal, buf | + | `createTask`: take the buffer, clear it, insert; on error put it back if still empty (`TasksViewModel.kt:221`) | Normal, "" |
| Normal, buf | ✎ (`ChangeUiState(Edit)`) | the screen effect prefills `buf = current list name` (`TasksScreen.kt:221`) and requests focus | Edit, name |
| Edit, buf | type | `buf = text` | Edit |
| Edit, buf blank | + (`UpdateListName`) | `EmptyListName` error (`TasksViewModel.kt:90`) | Edit |
| Edit, buf | + | `updateListName`: check `trim()` again, write, clear, `Normal` (`TasksViewModel.kt:292`) | Normal, "" |
| Edit, buf | system back | `changeState(Normal)` **clears buf** (`TasksViewModel.kt:314`) | Normal, "" |
| Edit | ← arrow | navigate back; the ViewModel is destroyed with its entry | — |

### Bugs

**T1: cancelling a rename turned the list name into a task. FIXED, issue 2. Severity: high (creates data the user never typed, every time).**
* Evidence: originally `AddTextField` held `remember { TextFieldValue(value) }` without a key, so it never showed later changes to `value`. The Edit prefill wrote into the ViewModel's buffer, but the field still looked empty. The `Normal` branch of the effect was the no-op expression `viewModel.newTaskMessage.value`.
* Repro: ✎ → back → + on the empty-looking field creates a task named after the list.
* Fix: a controlled text field, and clearing the buffer on Edit→Normal.
* Tests: `TasksViewModelTest.cancelling edit does not turn the prefilled list name into a task`, and the UI test `cancellingRenameDoesNotCreateTaskNamedAfterList`. On the original code that UI test fails with `expected:<Groceries> but was:<>`, which is the field/buffer divergence itself.

**T2: a draft was silently replaced by the list name. FIXED, issue 2. Severity: medium.**
* Repro: type "milk", ✎, +. The field showed "milk", but the rename used the old name, so nothing changed and "milk" was lost.
* Fixed by T-INV1: the field now shows the prefilled name.
* Covered by the same UI test, which asserts the field shows the name in Edit.

**T3: a blank list name was accepted. FIXED, issue 2. Severity: medium.**
* Evidence: originally `Command.UpdateListName` had no validation and `updateListName` did no `trim`, while `ListsViewModel` rejects blank names on create.
* Repro: ✎, clear the field, +. The list gets the name `""`.
* Tests: `TasksViewModelTest.blank rename is rejected`, and `second queued rename does not blank the list name`. The second test covers the double-tap form: the first rename clears the buffer, then the second rename, which passed validation earlier, used to write `""`.

**T4: a double tap on + created duplicate tasks. FIXED, issue 2. Severity: medium.**
* Evidence: originally the buffer was cleared only after the insert returned. A second `CreateTask` that ran before that point read the same text.
* Fix: take the text and clear the buffer before suspending.
* Test: `TasksViewModelTest.two create actions queued before the insert finishes insert once`.
* Test: `failed insert gives the text back` checks that the new early clear cannot lose text when the insert fails.

**T5: rotation while in Edit resets the edit to the stale name. OPEN. Severity: low.**
* The uiState effect starts again on recomposition and prefills `listData.value.name`. `listData` is `collectAsState(initial = list)`, and `list` is the navigation-time snapshot.
* Result: after a rename in this session, a rotation in Edit prefills the old name and discards what was typed.
* Fix if needed: move the prefill into `changeState(Edit)`, using a name held by the ViewModel.

---

## 3. Lists screen input buffer (implicit)

`M/presentation/view/lists/viewmodel/ListsViewModel.kt`

States: `newListName` (now `mutableStateOf`, so the controlled field recomposes).

| From | Event | Effect | To |
|---|---|---|---|
| buf blank | + | `EmptyListName` error | buf |
| buf | + | `createList`: `trim`, check, clear, insert; on error put it back if still empty (`ListsViewModel.kt:84`) | "" |

**L1: a double tap on + created duplicate lists. FIXED, issue 2.** This has the same mechanism as T4. Test: `ListsViewModelTest.two create actions queued before the insert finishes insert once`.

---

## 4. Persisted data: list and task lifecycles (Room)

`M/data/model/*.kt`, `M/data/room/**`

```
List:  (absent) ─create─► Live(name≠blank) ─rename─► Live ─delete─► Deleted ⇒ cascade-deletes its tasks
Task:  (absent) ─create(list Live)─► Unchecked ⇄ Checked ─delete─► (absent)
       Unchecked/Checked ─refresh(list)─► Unchecked      ─clear(list)─► (absent)
```

Invariants:
* **D-INV1:** every task belongs to a live list. The FK uses `CASCADE`, and inserting a task into a missing list fails the FK check and shows the error snackbar.
* **D-INV2:** list names are non-blank (T3).
* **D-INV3:** the UI feed equals the latest Room emission (see §5).

| Transition | Trigger | Code |
|---|---|---|
| List create | Lists + | `ListDao.insert` |
| List rename | Tasks ✎ + | `ListDao.update` (an optimistic UI update follows, see E2) |
| List delete | swipe a list row ≥ 500 px | `ListDao.delete`; tasks cascade |
| Task create / delete | Tasks + / swipe a task row | `TaskDao.insert` / `delete` |
| Task check ⇄ uncheck | checkbox | the item is mutated in place (`TasksScreen.kt:179`), then `TaskDao.update` |
| Refresh | ↺ | `TaskDao.resetAllTasks` (now `suspend`) |
| Clear | 🗑 (no confirmation) | `TaskDao.clearAllTaskFromList` |

### Retries and repeated delivery

Nothing in the app retries automatically: no repository, use case or ViewModel has a retry loop. A repeat happens in only two ways:
* The user taps again, usually after an error snackbar.
* A command waits suspended in a channel `send` while no screen collects it, and runs later (see §0).

| Write | Safe to repeat? | User retry after a failure |
|---|---|---|
| Create list / task | One write per submit (L1, T4). A deliberate second submit is a second item, by design | The text is put back in the field, so tapping + again retries |
| Rename list | Writing the same name twice is harmless; blank names are guarded (T3) | The buffer is already cleared and the UI shows the new name (E2), so the user has to type it again |
| Delete list / task | Yes: `@Delete` on a missing row affects 0 rows | Swipe again |
| Check / uncheck | Last write wins, and writes stay in order (Room's serial executor). See D1 for the lost-toggle window | Tap again |
| Refresh / clear tasks | Yes: both statements are idempotent | Tap again |
| Navigate to list / back | Yes, now (N1, N2) | — |

The one harmful case of a deferred `send` being delivered later was the leaked feed collectors (S1). A deferred swipe-delete that is delivered after the user has opened that list is now handled by D2's navigate-back path.

**D1: the checkbox state is mutated in place. A double tap can lose a toggle. OPEN. Severity: low.**
* `item.state = it` writes a plain field, not snapshot state, so the checkbox does not redraw until Room emits again.
* A second tap inside that window (a few ms) sends `true` again instead of `false`.
* Do not fix this with `item.copy(state = ...)`. `Task.id` is declared in the class body, so `copy()` resets it to 0 and the update affects no row.

**D2: the tasks screen for a deleted list crashed the app (NPE). FIXED. Severity: high (crash).**
* Evidence: `ListDao_Impl.getList` (generated code) emits `null` when there are no rows, even though the declared type was `Flow<TaskList>` (non-null). `TasksScreen.kt:91` then dereferenced `listData.value.name`.
* Repro, confirmed on the emulator with the code before this fix:
  1. Swipe a list row past 500 px.
  2. Within the 500 ms dismiss animation, tap the part of the row still on screen.
  3. The tap opens the tasks screen, and `onItemSwiped` (`TaskListListElement.kt:173`) deletes the list.
  4. Result: `FATAL EXCEPTION ... TaskList.getName() on a null object reference at TasksScreen.kt:91`.
* The N2 guard does not block this, because the navigation happens before the delete.
* Fix:
  * `getList` is typed `Flow<TaskList?>` through the DAO, repository and use case. Return-type nullability does not change the schema.
  * `getListData` navigates back on `null` instead of publishing it (`TasksViewModel.kt:210`).
* Tests:
  * `TasksViewModelTest.list deleted while its screen is open navigates back instead of showing null`
  * UI test `StateMachineRegressionTest.tapDuringSwipeDismissReturnsToLists`: it crashed the process before the fix and passed 2 out of 2 runs after it.

**D3: `TaskList` equality ignores `id`. OPEN, latent.**
* `id` is declared in the class body, so `data class` equality compares only `name`. Compose `State` and `StateFlow` both drop a new emission that is `==` to the current one.
* If Room coalesced a delete and a re-create of the same name into one emission, the row would keep the old object, whose id no longer exists. Opening it now lands in D2's navigate-back path instead of crashing.
* Not reachable at human speed. Moving `id` into the constructor would not change the schema, but it changes every constructor call and `copy()` call site.

---

## 5. Feed subscription lifecycle (implicit)

`ListsViewModel.getTaskList`, `TasksViewModel.getTasks`, `TasksViewModel.getListData`

```
NotSubscribed ─(screen LaunchedEffect)─► Subscribed(Room collector in viewModelScope) ─(VM cleared)─► Cancelled
```

Invariants:
* **S-INV1:** at most one Room collector per feed per ViewModel.
* **S-INV2:** when the screen is re-entered, it shows the latest feed without waiting for a new DB change.
* **S-INV3:** only the subscription writes the feed.

| Event | Original | Fixed |
|---|---|---|
| Lists screen entered (first time, back from Tasks, rotation) | `LaunchedEffect(Unit)` started **another** permanent collector each time and sent into a rendezvous `Channel` | the second and later calls return early (`ListsViewModel.kt:118`); the feed is a `StateFlow`, so it replays to the new screen |
| Tasks screen effect restarts (rotation) | another `getTasks` collector | early return (`TasksViewModel.kt:182`) |
| Refresh | reset, then `getTasks().first()` written to `_feedList` on `Dispatchers.IO`, racing the collector | reset only; the collector's re-emission updates the feed (confirmed on device by `refreshUnchecksTasksThroughTheRoomSubscription`) |

**S1: collectors leaked on every return to the lists screen. FIXED, issue 3. Severity: medium (unbounded growth during a session).**
* Each leaked collector re-ran the query on every DB change.
* Collectors from earlier visits stayed suspended in `send` with old snapshots and delivered them when the screen came back.
* A plain "already started" guard on the original `Channel` would have left the screen empty after returning, because channels do not replay. That is why the feed became a `StateFlow`.
* Tests: `ListsViewModelTest.returning to the screen reuses the subscription and still shows the lists`, and `TasksViewModelTest.restarting the screen effect keeps one task subscription`.

**S2: refresh could overwrite newer state with a stale snapshot. FIXED, issue 3. Severity: low.**
* Scenario: the snapshot is read right after the reset, the user checks a task, the collector emits it as checked, and then the refresh writes the older snapshot. The UI then shows the task unchecked while the DB has it checked.
* Test: `TasksViewModelTest.refresh leaves the feed to the subscription`.

**S3: `getListData` still starts a collector per rotation. OPEN. Severity: low.** It sends into a rendezvous `Channel`, so a guard alone would leave the title stale after rotation. The fix is the same as for the lists feed: a `StateFlow<TaskList?>`.

---

## 6. Swipe row (implicit, per row; `TaskListListElement.kt`, `TaskListElement.kt`)

States, from `offset: Animatable` and the `left/rightIconVisibility` flags:

| State | Condition |
|---|---|
| Idle | offset 0 |
| Dragging(dir) | a drag is in progress; the background icon side is set on drag start |
| Settling | released at \|offset\| < 500 → animate to 0, clear the flags |
| Dismissing | released at \|offset\| ≥ 500 → animate to ±1200 → `onItemSwiped()` → snap to 0 |

| From | Event | To |
|---|---|---|
| Idle | drag | Dragging |
| Dragging | release, \|x\| < 500 | Settling → Idle |
| Dragging | release, \|x\| ≥ 500 | Dismissing → delete → Idle (the row goes when Room emits) |
| Dismissing | new drag (`snapTo` cancels `animateTo`) | Dragging; **the delete never fires**, which works as an "undo by grabbing" |

**NOT A BUG / cosmetic:**
* The lists `LazyColumn` has no `key` (`ListsScreen.kt:79`), so the next row inherits the slot's icon flags after a delete.
* Dismiss does not reset the flags.
* Both are invisible because the foreground row is opaque (`surfaceContainer` background).
* The tasks list is keyed by `task.id`.
* After `onItemSwiped`, the dismissed row snaps back into view until Room removes it. This causes a brief flash, and it is what makes D2's tap window possible.

---

## 7. Error / snackbar state (implicit)

`errorMessage` is a plain var that `handleError` sets before `showSnackbar(errorMessage)`.

* **E1: `UnknownError` showed an empty or stale message. FIXED (two lines, no test; only reachable on a DB failure).** Originally the branch only logged, so the snackbar showed `""` or the previous "cannot be empty" text. It now sets "Something went wrong."
* **E2: rename is optimistic without rollback. OPEN, low.** `updateListName` changes the in-memory list, publishes it to the title and leaves Edit before it checks the result. On failure the title shows a name the DB does not have, until the next emission.
* **E3: load errors are swallowed. OPEN, low.** The `Error` branches in `getTasks` and `getListData` construct `Error.UnknownError(...)` and drop it.

---

## Issue groups and fix status

| Issue | Machines | Bugs | Status |
|---|---|---|---|
| **1. Navigation transitions must be idempotent** | §1 | N1 (high), N2, N3 | FIXED in `NavigationRepository` |
| **2. Input buffer is the single source of truth; submits are idempotent** | §2, §3, §7 | T1 (high), T2, T3, T4, L1, E1 | FIXED in `AddTextField`, both ViewModels |
| **3. One subscription per feed; only it writes the feed** | §5 | S1, S2 | FIXED; S3 OPEN |
| **4. A screen must survive its list being deleted** | §4 | D2 (crash) | FIXED in `ListDao`, `ListRepository`, `GetListUseCase`, `TasksViewModel.getListData` |
| 5. Follow-ups (not fixed) | §2, §4, §5, §7 | T5, D1, D3, S3, E2, E3 | OPEN |

Supporting change: `resetAllTasks` is now `suspend` through the DAO, the repository and the use case. That lets refresh run on `viewModelScope` without `Dispatchers.IO`. Room still runs it off the main thread.

Constraint for future fixes: do not change the entities. The DB is at `version = 3` with `fallbackToDestructiveMigration()`. A schema change without a version bump crashes the app, and a version bump wipes user data.

## Verified non-bugs

* `LIKE :list_id` on integer columns: no wildcards, so it behaves as `=`.
* `TaskDatabase.getInstance` is unsynchronized, but both repositories are Hilt singletons built on the main thread.
* Suspend writes go through Room's serialized transaction executor, so toggles stay in order.
* The cascade FK from tasks to lists is correct.

## Tests

* JVM, `./gradlew :app:testDebugUnitTest`: `NavigationRepositoryTest` (3), `TasksViewModelTest` (8), `ListsViewModelTest` (2), plus the original 2.
  * 9 of the first 10 navigation and tasks tests failed on the original code. The tenth, `failed insert gives the text back`, guards the new behavior.
  * The D2 test was added after the fix. The UI repro covers the failure on the old code.
  * `ListsViewModelTest` does not compile against the original code, because its feed was a `Channel`.
* Instrumented, `./gradlew :app:connectedDebugAndroidTest`, run on the `Pixel_6_API_30` emulator: `StateMachineRegressionTest` (5), plus the existing suite.
  * `nameWithUrlCharactersSurvivesNavigation` checks that the whole-JSON route encoding (N3 fix) round-trips `50% a/b "c" & d?#`.
  * This needed `utils/CustomTestRunner.kt`. `build.gradle.kts` already referenced that class but it did not exist, so no instrumented test could run before.
  * Both bug tests (`cancellingRename...`, `doubleTapOnBackArrow...`) fail on the original code.
* Known harness flake, not caused by these fixes: `BaseUiTest` clears the DB in `@Before`, but `createAndroidComposeRule` has already launched the activity. The first frame can therefore show the previous test's rows. `DeleteListTest` failed once in a full-suite run this way and passed 4 out of 4 runs on its own.
* Not covered by any test: D1, E2, and the swipe-row machine apart from the D2 path.
