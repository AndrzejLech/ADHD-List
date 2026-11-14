package com.example.adhdlist

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.data.room.list.ListRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import javax.inject.Inject

@HiltAndroidTest
open class BaseUiTest {

    private var stepIndex = 0

    @Inject
    lateinit var listRepository: ListRepository

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Rule(order = 0)
    @JvmField
    val hiltRule = HiltAndroidRule(this)

    // Before the activity starts (order 1 < 2): clearing afterwards let the first frames show the
    // previous test's lists, and a test could tap one of those stale rows.
    @Rule(order = 1)
    @JvmField
    val cleanDatabase = object : ExternalResource() {
        override fun before() {
            TaskDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext).clearAllTables()
        }
    }

    @Rule(order = 2)
    @JvmField
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    fun step(message: String, content: () -> Unit){
        Log.d("Ui Test","${++stepIndex}. $message")
        content()
    }
}
