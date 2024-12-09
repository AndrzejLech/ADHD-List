package com.example.adhdlist.tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.test.espresso.Espresso
import com.example.adhdlist.BaseUiTest
import com.example.adhdlist.screens.LazyListOfListItemNode
import com.example.adhdlist.screens.ListsScreen
import com.example.adhdlist.screens.TasksScreen
import com.example.adhdlist.utils.waitOnComponent
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Test

@HiltAndroidTest
class DeleteTaskTest : BaseUiTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteTaskTest() {
        onComposeScreen<ListsScreen>(composeTestRule) {
            step("Type testList") {
                addTextField.performTextInput("testList")
                addTextField.assertTextEquals("testList")
            }
            step("Confirm creation of test list") {
                addButton.performClick()
                Espresso.pressBack()
            }
            list.childAt<LazyListOfListItemNode>(position = 0) {
                step("Check if created list has expected name") {
                    assertIsDisplayed()
                    hasText("testList")
                }

                step("Click into the list") {
                    performClick()
                }
            }
        }
        onComposeScreen<TasksScreen>(composeTestRule) {
            step("Check if title matches") {
                title {
                    composeTestRule.waitOnComponent("Title", timeOutMillis = 8_000)
                    hasText("testList")
                }
            }

            step("Type testList") {
                addTextField.performTextInput("testTask")
                addTextField.assertTextEquals("testTask")
            }
            step("Confirm creation of test list") {
                addButton {
                    performClick()
                }
            }
        }
    }
}