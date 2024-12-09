package com.example.adhdlist.utils

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag

fun ComposeTestRule.waitOnComponent(
    testTag: String,
    timeOutMillis: Long
) {
    this.waitUntil(timeOutMillis) {
        this.onAllNodesWithTag(testTag).fetchSemanticsNodes().isNotEmpty()
    }
    repeat(3){
        runCatching {
            this.onAllNodesWithTag(testTag)[0].assertIsDisplayed()
        }.onSuccess { return }
    }
}