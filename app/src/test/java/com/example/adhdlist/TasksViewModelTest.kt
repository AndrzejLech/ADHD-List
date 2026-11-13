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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Regression tests for docs/STATE_MACHINES.md on the tasks screen.
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
        coEvery { taskRepository.updateTask(any()) } returns Result.Success(true)
        coEvery { listRepository.updateList(any()) } coAnswers { delay(100); Result.Success(true) }
        vm = TasksViewModel(taskRepository, listRepository, navigationRepository)
        vm.list = TaskList("Groceries").apply { id = 1; position = 4; remoteId = "r1" }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Same wiring as TasksScreen: actions are handled by whoever collects the channel.
    private fun TestScope.collectActions() {
        backgroundScope.launch { vm.actions.collect { vm.handleAction(it) } }
        backgroundScope.launch { vm.errors.collect {} }
    }

    @Test
    fun `rename does not touch the add-task text`() = runTest(dispatcher) {
        collectActions()
        vm.newTaskMessage.value = "milk"
        vm.triggerCommand(Command.RenameList("Shopping"))
        advanceUntilIdle()

        coVerify(exactly = 1) { listRepository.updateList(match { it.name == "Shopping" }) }
        assertEquals("milk", vm.newTaskMessage.value)
    }

    @Test
    fun `rename trims and keeps id, position and remote id`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.RenameList("  Shopping "))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            listRepository.updateList(match { it.name == "Shopping" && it.id == 1 && it.position == 4 && it.remoteId == "r1" })
        }
    }

    @Test
    fun `editing an item trims the text and keeps its id, state and position`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.editTask(any()) } returns Result.Success(true)
        val item = Task(1, "milk", state = true, id = 7, position = 2, remoteId = "t7")
        vm.triggerCommand(Command.EditItem(item, "  oat milk "))
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.editTask(item.copy(message = "oat milk")) }
    }

    @Test
    fun `editing an item to blank or to the same text writes nothing`() = runTest(dispatcher) {
        collectActions()
        val item = Task(1, "milk", id = 7)
        vm.triggerCommand(Command.EditItem(item, "   "))
        vm.triggerCommand(Command.EditItem(item, "milk"))
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.editTask(any()) }
    }

    @Test
    fun `blank rename is rejected`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.RenameList("   "))
        advanceUntilIdle()

        coVerify(exactly = 0) { listRepository.updateList(any()) }
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
    fun `task longer than the shared-list limit is rejected, not silently dropped later`() = runTest(dispatcher) {
        collectActions()
        vm.newTaskMessage.value = "x".repeat(1001)
        vm.triggerCommand(Command.AddButtonClick)
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        assertEquals(1001, vm.newTaskMessage.value.length) // kept, so the user can shorten it
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
    fun `checking a task writes a new row and leaves the shown one alone`() = runTest(dispatcher) {
        collectActions()
        val shown = Task(1, "milk", state = false).apply { id = 7; position = 2; remoteId = "t7" }
        vm.triggerCommand(Command.ChangeState(shown, checked = true))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            taskRepository.updateTask(match { it.id == 7 && it.state && it.position == 2 && it.remoteId == "t7" })
        }
        assertFalse(shown.state)
    }

    @Test
    fun `drag then drop saves the new order`() = runTest(dispatcher) {
        collectActions()
        val tasks = (1..3).map { Task(1, "t$it").apply { id = it } }
        every { taskRepository.getTasks(1) } returns Result.Success(MutableStateFlow(tasks.toMutableList()))
        coEvery { taskRepository.reorderTasks(1, any()) } returns Result.Success(true)
        vm.getTasks(1)

        vm.moveTask(0, 2)
        vm.triggerCommand(Command.SaveOrder)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.reorderTasks(1, match { it.map(Task::id) == listOf(2, 3, 1) }) }
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

        vm.getListData(1)
        list.value = null // Room's emission after the row is deleted
        advanceUntilIdle()

        verify(exactly = 1) { navigationRepository.popNavigation() }
        assertEquals("Groceries", vm.listData.value?.name)
    }
}
