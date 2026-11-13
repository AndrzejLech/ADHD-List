package com.example.adhdlist

import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel.Action
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Regression tests for docs/STATE_MACHINES.md issues 2 and 3 on the lists screen.
class ListsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val listRepository: ListRepository = mockk()
    private lateinit var vm: ListsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = ListsViewModel(listRepository, mockk(relaxed = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `two create actions queued before the insert finishes insert once`() = runTest(dispatcher) {
        coEvery { listRepository.insertList(any()) } coAnswers { delay(100); Result.Success(true) }
        vm.newListName = " Groceries "
        vm.handleAction(Action.CreateList)
        vm.handleAction(Action.CreateList)
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.insertList(match { it.name == "Groceries" }) }
        assertEquals("", vm.newListName)
    }

    @Test
    fun `returning to the screen reuses the subscription and still shows the lists`() = runTest(dispatcher) {
        val lists = mutableListOf(TaskList("Groceries"))
        every { listRepository.getLists() } returns Result.Success(MutableStateFlow(lists))

        vm.getTaskList() // first visit
        vm.getTaskList() // back from the tasks screen

        verify(exactly = 1) { listRepository.getLists() }
        assertEquals(lists, vm.feedList.value) // replayed to the re-entered screen without a new DB emission
    }
}
