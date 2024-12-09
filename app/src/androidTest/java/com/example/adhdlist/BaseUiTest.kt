package com.example.adhdlist

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.database.lists.ClearDatabaseBeforeTestsUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

@HiltAndroidTest
open class BaseUiTest {

    private var stepIndex = 0

    @Inject
    lateinit var listRepository: ListRepository

    @Before
    fun init() {
        hiltRule.inject()
        ClearDatabaseBeforeTestsUseCase(listRepository).execute()
    }

    @Rule
    @JvmField
    val hiltRule = HiltAndroidRule(this)

    @Rule
    @JvmField
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    fun step(message: String, content: () -> Unit){
        Log.d("Ui Test","${++stepIndex}. $message")
        content()
    }
}