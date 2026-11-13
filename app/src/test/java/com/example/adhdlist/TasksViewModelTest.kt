package com.example.adhdlist

import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.data.room.task.TaskRepository
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel.Action
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel.Command
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

// Regression tests for docs/STATE_MACHINES.md issues 2 and 3 on the tasks screen.
class TasksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val taskRepository: TaskRepository = mockk()
    private val listRepository: ListRepository = mockk()
    private val navigationRepository: NavigationRepository = mockk(relaxed = true)
    private lateinit var vm: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { taskRepository.insertTask(any()) } coAnswers { delay(100); Result.Success(true) }
        coEvery { listRepository.updateList(any()) } coAnswers { delay(100); Result.Success(true) }
        vm = TasksViewModel(taskRepository, listRepository, navigationRepository)
        vm.list = TaskList("Groceries").apply { id = 1 }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Same wiring as TasksScreen: actions are handled by whoever collects the channel.
    private fun TestScope.collectActions() {
        backgroundScope.launch { vm.actions.collect { vm.handleAction(it) } }
        backgroundScope.launch { vm.errors.collect {} }
    }

    @Test
    fun `cancelling edit does not turn the prefilled list name into a task`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.ChangeUiState(UiState.Edit))
        assertEquals(UiState.Edit, vm.uiState.value)
        vm.newTaskMessage.value = "Groceries" // TasksScreen prefills the list name on entering Edit
        vm.triggerCommand(Command.ChangeUiState(UiState.Normal))
        vm.triggerCommand(Command.AddButtonClick)
        advanceUntilIdle()

        assertEquals("", vm.newTaskMessage.value)
        coVerify(exactly = 0) { taskRepository.insertTask(any()) }
    }

    @Test
    fun `two create actions queued before the insert finishes insert once`() = runTest(dispatcher) {
        vm.newTaskMessage.value = "milk"
        vm.handleAction(Action.CreateTask)
        vm.handleAction(Action.CreateTask)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.insertTask(match { it.message == "milk" && it.listId == 1 }) }
        assertEquals("", vm.newTaskMessage.value)
    }

    @Test
    fun `failed insert gives the text back`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.insertTask(any()) } returns Result.Error(Exception("db"))
        vm.newTaskMessage.value = "milk"
        vm.handleAction(Action.CreateTask)
        advanceUntilIdle()

        assertEquals("milk", vm.newTaskMessage.value)
    }

    @Test
    fun `blank rename is rejected`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.ChangeUiState(UiState.Edit))
        vm.newTaskMessage.value = "   "
        vm.triggerCommand(Command.UpdateListName)
        advanceUntilIdle()

        coVerify(exactly = 0) { listRepository.updateList(any()) }
        assertEquals("Groceries", vm.list.name)
    }

    @Test
    fun `second queued rename does not blank the list name`() = runTest(dispatcher) {
        collectActions()
        vm.newTaskMessage.value = " Shopping "
        vm.handleAction(Action.UpdateListName)
        vm.handleAction(Action.UpdateListName)
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.updateList(any()) }
        assertEquals("Shopping", vm.list.name)
        assertEquals(UiState.Normal, vm.uiState.value)
    }

    @Test
    fun `restarting the screen effect keeps one task subscription`() = runTest(dispatcher) {
        every { taskRepository.getTasks(1) } returns Result.Success(MutableStateFlow(mutableListOf()))
        vm.getTasks(1)
        vm.getTasks(1)

        verify(exactly = 1) { taskRepository.getTasks(1) }
    }

    @Test
    fun `refresh leaves the feed to the subscription`() = runTest(dispatcher) {
        collectActions()
        val live = MutableStateFlow(mutableListOf(Task(1, "milk", state = true).apply { id = 7 }))
        val staleSnapshot = flowOf(mutableListOf(Task(1, "milk", state = false).apply { id = 7 }))
        every { taskRepository.getTasks(1) } returnsMany listOf(Result.Success(live), Result.Success(staleSnapshot))
        coEvery { taskRepository.resetAllTasks(1) } returns Result.Success(true)

        vm.getTasks(1)
        vm.triggerCommand(Command.RefreshTasks)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.resetAllTasks(1) }
        verify(exactly = 1) { taskRepository.getTasks(1) }
        assertTrue(vm.feedList.value.single().state)
    }

    @Test
    fun `list deleted while its screen is open navigates back instead of showing null`() = runTest(dispatcher) {
        val list = MutableStateFlow<TaskList?>(TaskList("Groceries").apply { id = 1 })
        every { listRepository.getList(1) } returns Result.Success(list)
        val shown = mutableListOf<TaskList>()
        backgroundScope.launch { vm.listData.collect { shown += it } }

        vm.getListData(1)
        list.value = null // Room's emission after the row is deleted
        advanceUntilIdle()

        verify(exactly = 1) { navigationRepository.popNavigation() }
        assertEquals(listOf("Groceries"), shown.map { it.name })
    }
}
