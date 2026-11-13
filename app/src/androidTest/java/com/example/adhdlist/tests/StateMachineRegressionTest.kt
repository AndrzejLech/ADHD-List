package com.example.adhdlist.tests

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.adhdlist.BaseUiTest
import com.example.adhdlist.utils.waitOnComponent
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

// UI-level regressions for docs/STATE_MACHINES.md issues 1 and 2.
@HiltAndroidTest
class StateMachineRegressionTest : BaseUiTest() {

    private fun openNewList(name: String) {
        composeTestRule.onNodeWithTag("ListsTextField").performTextInput(name)
        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitOnComponent("TaskListListElement0", timeOutMillis = 5_000)
        composeTestRule.onNodeWithTag("TaskListListElement0").performClick()
        composeTestRule.waitOnComponent("Title", timeOutMillis = 5_000)
        composeTestRule.waitForIdle() // finish the slide so only the tasks screen's AddButton exists
    }

    private fun fieldText(tag: String) = composeTestRule.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun pressBack() = composeTestRule.activityRule.scenario.onActivity {
        it.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun cancellingRenameDoesNotCreateTaskNamedAfterList() {
        openNewList("Groceries")

        composeTestRule.onNodeWithContentDescription("Rename").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Groceries", fieldText("TasksTextField")) // the field shows what will be submitted

        pressBack() // Edit -> Normal
        composeTestRule.waitForIdle()
        assertEquals("", fieldText("TasksTextField"))

        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Task cannot be empty.").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("TaskListElement0").assertDoesNotExist()
    }

    @Test
    fun nameWithUrlCharactersSurvivesNavigation() {
        val name = "50% a/b \"c\" & d?#"
        openNewList(name)

        composeTestRule.onNodeWithTag("Title").assertTextEquals(name)
        composeTestRule.onNodeWithContentDescription("Rename").performClick()
        composeTestRule.waitForIdle()
        assertEquals(name, fieldText("TasksTextField"))
    }

    // D2: tapping the row during its swipe-dismiss animation opened a list that was then deleted (NPE crash).
    @Test
    fun tapDuringSwipeDismissReturnsToLists() {
        composeTestRule.onNodeWithTag("ListsTextField").performTextInput("Groceries")
        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitOnComponent("TaskListListElement0", timeOutMillis = 5_000)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("TaskListListElement0").performTouchInput {
            swipeRight(startX = left + 10f, endX = left + 650f, durationMillis = 200)
        }
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.onNodeWithTag("TaskListListElement0").performTouchInput {
            click(centerRight - Offset(40f, 0f))
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("ListsTextField").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("Title").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("TaskListListElement0").assertDoesNotExist()
    }

    @Test
    fun refreshUnchecksTasksThroughTheRoomSubscription() {
        openNewList("Groceries")
        composeTestRule.onNodeWithTag("TasksTextField").performTextInput("milk")
        composeTestRule.onNodeWithTag("AddButton").performClick()
        composeTestRule.waitOnComponent("TaskCheckBox", timeOutMillis = 5_000)

        composeTestRule.onNodeWithTag("TaskCheckBox").performClick()
        composeTestRule.waitUntil(5_000) { isChecked() }
        composeTestRule.onNodeWithContentDescription("Restore").performClick()
        composeTestRule.waitUntil(5_000) { !isChecked() }
    }

    private fun isChecked() = composeTestRule.onNodeWithTag("TaskCheckBox")
        .fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On

    @Test
    fun doubleTapOnBackArrowLandsOnLists() {
        openNewList("Groceries")

        // The arrow stays clickable during the 500 ms exit transition (system back does not:
        // BackHandler unregisters with the popped entry's lifecycle).
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ListsTextField").assertExists()
    }
}
