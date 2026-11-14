package com.example.adhdlist

import com.example.adhdlist.data.model.ListWithProgress
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel.Action
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel.Command
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Regression tests for docs/STATE_MACHINES.md on the lists screen.
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

    private fun TestScope.collectActions(errors: MutableList<Any> = mutableListOf()) {
        backgroundScope.launch { vm.actions.collect { vm.handleAction(it) } }
        backgroundScope.launch { vm.errors.collect { errors += it } }
        backgroundScope.launch { vm.messages.collect {} }
    }

    private fun lists(vararg names: String) =
        names.mapIndexed { i, name -> ListWithProgress(TaskList(name).apply { id = i + 1 }, 0, 0) }.toMutableList()

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
        val lists = lists("Groceries")
        every { listRepository.getLists() } returns Result.Success(MutableStateFlow(lists))

        vm.getTaskList() // first visit
        vm.getTaskList() // back from the tasks screen

        verify(exactly = 1) { listRepository.getLists() }
        assertEquals(lists, vm.feedList.value) // replayed to the re-entered screen without a new DB emission
    }

    @Test
    fun `drag then drop saves the new order`() = runTest(dispatcher) {
        collectActions()
        every { listRepository.getLists() } returns Result.Success(MutableStateFlow(lists("a", "b", "c")))
        coEvery { listRepository.reorderLists(any()) } returns Result.Success(true)
        vm.getTaskList()

        vm.moveList(2, 0)
        vm.triggerCommand(Command.SaveOrder)
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.reorderLists(match { it.map(TaskList::name) == listOf("c", "a", "b") }) }
    }

    @Test
    fun `join finds the code inside a pasted invite message`() = runTest(dispatcher) {
        collectActions()
        coEvery { listRepository.joinList(any()) } returns Result.Success(true)
        val code = "AbCdEfGhIjKlMnOpQrSt"
        vm.triggerCommand(Command.Join("Let's do “Groceries” together.\nJoin code: $code\nOr open: adhdlist://join/$code"))
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.joinList(code) }
    }

    @Test
    fun `a 20-letter list name in the invite is not mistaken for the code`() = runTest(dispatcher) {
        collectActions()
        coEvery { listRepository.joinList(any()) } returns Result.Success(true)
        val code = "AbCdEfGhIjKlMnOpQrSt"
        vm.triggerCommand(Command.Join("Let's do “Weekendshoppinglist2” together.\nJoin code: $code"))
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.joinList(code) }
    }

    @Test
    fun `renaming a list from the lists screen trims and keeps everything else`() = runTest(dispatcher) {
        collectActions()
        coEvery { listRepository.updateList(any()) } returns Result.Success(true)
        val list = TaskList("Groceries", id = 3, position = 1, remoteId = "r3")
        vm.triggerCommand(Command.RenameList(list, " Shopping "))
        vm.triggerCommand(Command.RenameList(list, "  "))
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.updateList(any()) }
        coVerify { listRepository.updateList(list.copy(name = "Shopping")) }
    }

    @Test
    fun `join without a valid code reports it and calls nothing`() = runTest(dispatcher) {
        val errors = mutableListOf<Any>()
        collectActions(errors)
        vm.triggerCommand(Command.Join("abc123"))
        advanceUntilIdle()

        coVerify(exactly = 0) { listRepository.joinList(any()) }
        assertTrue(errors.single() == ListsViewModel.Error.InvalidJoinCode)
    }
}
