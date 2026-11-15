package com.example.adhdlist.tests

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import com.example.adhdlist.BaseUiTest
import com.example.adhdlist.utils.waitOnComponent
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// UI-level regressions for docs/STATE_MACHINES.md.
@HiltAndroidTest
class StateMachineRegressionTest : BaseUiTest() {

    private fun createList(name: String) {
        composeTestRule.onNodeWithTag("ListsTextField").performTextInput(name)
        composeTestRule.onNodeWithTag("AddButton").performClick()
        // Field empty first: until then the typed text itself would satisfy textShown().
        composeTestRule.waitUntil(5_000) { fieldText("ListsTextField").isEmpty() }
        Espresso.closeSoftKeyboard() // the keyboard shrinks the list; rows below it aren't composed
        composeTestRule.waitUntil(5_000) { textShown(name) }
    }

    private fun openNewList(name: String) {
        createList(name)
        composeTestRule.onNodeWithText(name).performClick()
        composeTestRule.waitOnComponent("Title", timeOutMillis = 5_000)
        composeTestRule.waitForIdle() // finish the transition so only the tasks screen's AddButton exists
    }

    private fun addTask(message: String) {
        composeTestRule.onNodeWithTag("TasksTextField").performTextInput(message)
        composeTestRule.onNodeWithTag("AddButton").performClick()
        // Field empty first: until then the typed text itself would satisfy textShown().
        composeTestRule.waitUntil(5_000) { fieldText("TasksTextField").isEmpty() }
        Espresso.closeSoftKeyboard() // the keyboard shrinks the list; rows below it aren't composed
        composeTestRule.waitUntil(5_000) { textShown(message) }
    }

    private fun textShown(text: String) =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String) = composeTestRule.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun taskTexts() = composeTestRule.onAllNodesWithTag("TaskText", useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString() }

    @Test
    fun renameHappensInADialogAndNeverLeaksIntoTheAddField() {
        openNewList("Groceries")

        composeTestRule.onNodeWithContentDescription("Rename").performClick()
        assertEquals("Groceries", fieldText("DialogTextField"))
        composeTestRule.onNodeWithTag("DialogTextField").performTextClearance()
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("Shopping")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitUntil(5_000) { textShown("Shopping") }
        assertEquals("", fieldText("TasksTextField"))
        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Type something first.") }
        composeTestRule.onNodeWithTag("TaskListElement0").assertDoesNotExist()
    }

    @Test
    fun nameWithUrlCharactersSurvivesNavigation() {
        val name = "50% a/b \"c\" & d?#"
        openNewList(name)

        composeTestRule.onNodeWithTag("Title").assertTextEquals(name)
        composeTestRule.onNodeWithContentDescription("Rename").performClick()
        assertEquals(name, fieldText("DialogTextField"))
    }

    @Test
    fun swipingAListAsksBeforeDeleting() {
        createList("Groceries")

        composeTestRule.onNodeWithTag("TaskListListElement0").performTouchInput { swipeRight() }
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Groceries").assertExists()

        composeTestRule.onNodeWithTag("TaskListListElement0").performTouchInput { swipeRight() }
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitUntil(5_000) { textShown("No lists yet") }
    }

    @Test
    fun deletedItemComesBackWithUndoFromSwipeOrPopup() {
        openNewList("Groceries")
        addTask("milk")
        addTask("eggs")

        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput { swipeRight() }
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("eggs") }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("milk", "eggs") }

        // The same from the item's popup: Delete, then Undo.
        composeTestRule.onNodeWithTag("TaskListElement1").performTouchInput { longClick() }
        inDialog("Delete").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("milk") }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("milk", "eggs") }
    }

    @Test
    fun dragHandleReordersTasksAndTheOrderIsSaved() {
        openNewList("Groceries")
        addTask("first")
        addTask("second")
        addTask("third")

        // Unmerged: the handle sits inside the clickable row, whose merged node centres elsewhere.
        composeTestRule.onNodeWithContentDescription("Drag to reorder first", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(25) { moveBy(Offset(0f, 20f), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(5_000) { taskTexts().last() == "first" }

        // Leave and come back: the order must come from the database, not from screen state.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(5_000) { textShown("ADHD List") }
        composeTestRule.onNodeWithText("Groceries").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts().size == 3 }
        assertEquals(listOf("second", "third", "first"), taskTexts())
    }

    @Test
    fun aShortOrQuickSwipeDoesNotDelete() {
        openNewList("Groceries")
        addTask("milk")

        // A third of the row, done fast: Material alone would treat the flick as a dismiss.
        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput {
            swipeRight(startX = left + 10f, endX = left + width * 0.35f, durationMillis = 80)
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("milk"), taskTexts())
        composeTestRule.onNodeWithText("Undo").assertDoesNotExist()

        // Most of the row: deletes.
        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput { swipeRight() }
        composeTestRule.waitUntil(5_000) { taskTexts().isEmpty() }
    }

    @Test
    fun pressAndHoldAnItemEditsItsText() {
        openNewList("Groceries")
        addTask("milk")

        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput { longClick() }
        assertEquals("milk", fieldText("DialogTextField"))
        composeTestRule.onNodeWithTag("DialogTextField").performTextClearance()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("oat milk")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("oat milk") }
        assertTrue("holding must not also tick the item", !isChecked())
    }

    @Test
    fun multiLineItemsKeepTheirLinesWhenAddedAndEdited() {
        openNewList("Workout")
        val sets = "Biceps\nX15\nX12 +1"
        composeTestRule.onNodeWithTag("TasksTextField").performTextInput(sets)
        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitUntil(5_000) { fieldText("TasksTextField").isEmpty() }
        Espresso.closeSoftKeyboard()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf(sets) }

        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput { longClick() }
        assertEquals(sets, fieldText("DialogTextField"))
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("\nX10 +1")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("$sets\nX10 +1") }
    }

    private fun inDialog(text: String) =
        composeTestRule.onAllNodesWithText(text).filterToOne(hasAnyAncestor(isDialog()))

    @Test
    fun categoriesFoldAndAreDeletedWithTheirItems() {
        openNewList("Zakupy")
        addTask("chleb")

        // New category from the menu.
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("New category").performClick()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("Warzywa")
        inDialog("Add").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Warzywa") }

        // + on the header: the field says where items go, and they land in the category.
        composeTestRule.onNodeWithContentDescription("Add to Warzywa").performClick()
        composeTestRule.onNodeWithTag("AddTarget").assertExists()
        addTask("cukinia")
        assertEquals(listOf("chleb", "cukinia"), taskTexts())

        // Tap the header to close it; the item inside is hidden, the one at the top isn't.
        composeTestRule.onNodeWithText("Warzywa").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("chleb") }

        // Closed stays closed after leaving and coming back.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(5_000) { textShown("ADHD List") }
        composeTestRule.onNodeWithText("Zakupy").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Warzywa") }
        assertEquals(listOf("chleb"), taskTexts())
        composeTestRule.onNodeWithText("Warzywa").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("chleb", "cukinia") }

        // Move "chleb" into Warzywa from its edit dialog; closing Warzywa then hides both.
        composeTestRule.onNodeWithTag("TaskListElement0").performTouchInput { longClick() }
        inDialog("Warzywa").performClick()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Warzywa").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts().isEmpty() }

        // Swiping the header only asks; Cancel keeps everything.
        composeTestRule.onNodeWithTag("CategoryHeader").performTouchInput { swipeRight() }
        inDialog("Cancel").performClick()
        composeTestRule.onNodeWithText("Warzywa").assertExists()

        // Deleting from its popup removes the category and its items (both are inside it now).
        composeTestRule.onNodeWithTag("CategoryHeader").performTouchInput { longClick() }
        inDialog("Delete").performClick()
        composeTestRule.onNodeWithText("This also deletes its 2 items. It can't be undone.").assertExists()
        inDialog("Delete").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Nothing here yet") }
        composeTestRule.onNodeWithText("Warzywa").assertDoesNotExist()
    }

    @Test
    fun deleteEverythingIsOnlyAvailableWhenThereIsSomethingToDelete() {
        openNewList("Groceries")

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Delete everything").assertIsNotEnabled()
        Espresso.pressBack() // close the menu

        addTask("milk")
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Delete everything").assertIsEnabled()
    }

    private fun dragHandle(of: String, byY: Float) {
        composeTestRule.onNodeWithContentDescription("Drag to reorder $of", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(25) { moveBy(Offset(0f, byY / 25), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun draggingMovesItemsIntoAndOutOfCategories() {
        openNewList("Zakupy")
        addTask("chleb")
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("New category").performClick()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("Warzywa")
        inDialog("Add").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Warzywa") }
        composeTestRule.onNodeWithContentDescription("Add to Warzywa").performClick()
        addTask("cukinia")

        // Down past the header: "chleb" becomes Warzywa's first item; closing Warzywa hides both.
        dragHandle("chleb", byY = 400f)
        composeTestRule.onNodeWithText("Warzywa").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts().isEmpty() }

        // Saved: still inside after leaving the list and coming back.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(5_000) { textShown("ADHD List") }
        composeTestRule.onNodeWithText("Zakupy").performClick()
        composeTestRule.waitUntil(5_000) { textShown("Warzywa") }
        assertEquals(emptyList<String>(), taskTexts())
        composeTestRule.onNodeWithText("Warzywa").performClick()
        // The 400 px drag can also carry "chleb" past "cukinia", so either order is right.
        composeTestRule.waitUntil(5_000) { taskTexts().sorted() == listOf("chleb", "cukinia") }

        // Up past its header: back to the top of the list.
        dragHandle("chleb", byY = -400f)
        composeTestRule.onNodeWithText("Warzywa").performClick()
        composeTestRule.waitUntil(5_000) { taskTexts() == listOf("chleb") }
    }

    @Test
    fun pressAndHoldAListRenamesIt() {
        createList("Groceries")

        composeTestRule.onNodeWithTag("TaskListListElement0").performTouchInput { longClick() }
        composeTestRule.onNodeWithTag("DialogTextField").performTextClearance()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("Shopping")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitUntil(5_000) { textShown("Shopping") }
        composeTestRule.onNodeWithText("Groceries").assertDoesNotExist()
    }

    @Test
    fun uncheckAllAsksFirstThenTheMenuOffersCheckAll() {
        openNewList("Groceries")
        addTask("milk")
        composeTestRule.onNodeWithTag("TaskCheckBox").performClick()
        composeTestRule.waitUntil(5_000) { isChecked() }

        // Uncheck all asks first; Cancel keeps the tick.
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Uncheck all").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(isChecked())

        // Confirmed: unticked, through the Room subscription.
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Uncheck all").performClick()
        composeTestRule.onNodeWithText("Uncheck all").performClick() // the dialog's confirm button
        composeTestRule.waitUntil(5_000) { !isChecked() }

        // Nothing ticked now, so the same menu entry has become "Check all", without a dialog.
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Uncheck all").assertDoesNotExist()
        composeTestRule.onNodeWithText("Check all").performClick()
        composeTestRule.waitUntil(5_000) { isChecked() }
    }

    private fun isChecked() = composeTestRule.onNodeWithTag("TaskCheckBox")
        .fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On

    @Test
    fun doubleTapOnBackArrowLandsOnLists() {
        openNewList("Groceries")

        // The arrow stays clickable during the exit transition (system back does not:
        // BackHandler-style callbacks unregister with the popped entry's lifecycle).
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ListsTextField").assertExists()
    }
}
