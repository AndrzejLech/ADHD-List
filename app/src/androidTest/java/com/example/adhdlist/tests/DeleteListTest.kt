package com.example.adhdlist.tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.adhdlist.BaseUiTest
import com.example.adhdlist.screens.LazyListOfListItemNode
import com.example.adhdlist.screens.ListsScreen
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeleteListTest : BaseUiTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteListTest() {
        onComposeScreen<ListsScreen>(composeTestRule) {
            step("Type testList") {
                addTextField.performTextInput("testList")
            }
            step("Confirm creation of test list") {
                addButton.performClick()
            }
            list.childAt<LazyListOfListItemNode>(position = 0) {
                step("Check if created list has expected name") {
                    hasText("testList")
                }

                step("Delete list using swipe gesture") {
                    performTouchInput {
                        swipeRight()
                    }
                }
                step("Assert list is deleted") {
                    assertDoesNotExist()
                }
            }
        }
    }
}