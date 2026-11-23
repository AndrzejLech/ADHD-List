package com.example.adhdlist

import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.room.category.CategoryRepository
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
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private lateinit var vm: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { taskRepository.insertTask(any()) } coAnswers { delay(100); Result.Success(true) }
        coEvery { taskRepository.updateTask(any()) } returns Result.Success(true)
        coEvery { listRepository.updateList(any()) } coAnswers { delay(100); Result.Success(true) }
        coEvery { categoryRepository.setExpanded(any(), any()) } returns Result.Success(true)
        coEvery { categoryRepository.createCategory(any(), any()) } returns Result.Success(true)
        coEvery { categoryRepository.renameCategory(any()) } returns Result.Success(true)
        coEvery { categoryRepository.deleteCategory(any()) } returns Result.Success(true)
        vm = TasksViewModel(taskRepository, listRepository, navigationRepository, categoryRepository)
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

        vm.moveTask(fromId = 1, toId = 3)
        vm.triggerCommand(Command.SaveOrder)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.reorderTasks(1, match { it.map(Task::id) == listOf(2, 3, 1) }) }
    }

    @Test
    fun `check all ticks every item in this list`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.setAllTasks(1, true) } returns Result.Success(true)
        vm.triggerCommand(Command.CheckAll)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.setAllTasks(1, true) }
    }

    @Test
    fun `delete checked removes the ticked items of this list`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.deleteCheckedTasks(1) } returns Result.Success(true)
        vm.triggerCommand(Command.DeleteChecked)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.deleteCheckedTasks(1) }
    }

    private val vegetables = Category(1, "Warzywa", id = 5, expanded = false)

    @Test
    fun `after tapping + on a category, new items go into it and the category opens`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.AddTo(vegetables))
        vm.newTaskMessage.value = "cukinia"
        vm.handleAction(Action.CreateTask)
        advanceUntilIdle()

        coVerify(exactly = 1) { categoryRepository.setExpanded(vegetables, true) }
        coVerify(exactly = 1) { taskRepository.insertTask(match { it.message == "cukinia" && it.categoryId == 5 }) }
        assertEquals(5, vm.addTargetId.value) // stays, so several items can go in one after another
    }

    @Test
    fun `clearing the target sends new items back to the top`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.AddTo(vegetables))
        vm.triggerCommand(Command.AddTo(null))
        vm.newTaskMessage.value = "chleb"
        vm.handleAction(Action.CreateTask)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.insertTask(match { it.categoryId == null }) }
    }

    @Test
    fun `when the target category disappears, adding goes back to the top`() = runTest(dispatcher) {
        val categories = MutableStateFlow(listOf(vegetables))
        every { categoryRepository.getCategories(1) } returns Result.Success(categories)
        vm.getCategories(1)
        vm.addTargetId.value = 5

        categories.value = emptyList() // deleted here or by someone sharing the list
        advanceUntilIdle()

        assertEquals(null, vm.addTargetId.value)
    }

    @Test
    fun `editing can move an item to another category, or just change its text`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.editTask(any()) } returns Result.Success(true)
        val item = Task(1, "cukinia", id = 7, categoryId = null)
        vm.triggerCommand(Command.EditItem(item, "cukinia", categoryId = 5))
        vm.triggerCommand(Command.EditItem(item, "cukinia", categoryId = null)) // nothing changed
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.editTask(any()) }
        coVerify { taskRepository.editTask(item.copy(categoryId = 5)) }
    }

    private fun feed(vararg items: Task) {
        every { taskRepository.getTasks(1) } returns Result.Success(MutableStateFlow(items.toMutableList()))
        vm.getTasks(1)
    }

    private fun shown() = vm.feedList.value.map { it.id to it.categoryId }

    @Test
    fun `dragging onto an item in another category moves it there`() = runTest(dispatcher) {
        feed(Task(1, "chleb", id = 1), Task(1, "cukinia", id = 2, categoryId = 5), Task(1, "papryka", id = 3, categoryId = 5))

        vm.moveTask(fromId = 1, toId = 3)

        assertEquals(listOf(2 to 5, 3 to 5, 1 to 5), shown())
    }

    @Test
    fun `dragging down past a header makes it that category's first item`() = runTest(dispatcher) {
        feed(Task(1, "chleb", id = 1), Task(1, "cukinia", id = 2, categoryId = 5))

        vm.moveTaskToSection(fromId = 1, categoryId = 5, first = true)

        assertEquals(listOf(1 to 5, 2 to 5), shown())
    }

    @Test
    fun `dragging up past its own header moves it to the end of the section above`() = runTest(dispatcher) {
        feed(Task(1, "chleb", id = 1), Task(1, "cukinia", id = 2, categoryId = 5), Task(1, "mleko", id = 3))

        vm.moveTaskToSection(fromId = 2, categoryId = null, first = false)

        assertEquals(listOf(1 to null, 3 to null, 2 to null), shown())
    }

    @Test
    fun `dragging into a closed category opens it so the item stays visible`() = runTest(dispatcher) {
        every { categoryRepository.getCategories(1) } returns Result.Success(MutableStateFlow(listOf(vegetables)))
        vm.getCategories(1)
        feed(Task(1, "chleb", id = 1))

        vm.moveTaskToSection(fromId = 1, categoryId = 5, first = true)
        advanceUntilIdle()

        assertTrue(vm.categories.value.single().expanded)
        coVerify(exactly = 1) { categoryRepository.setExpanded(vegetables, true) }
    }

    @Test
    fun `dropping saves the new order together with the new category`() = runTest(dispatcher) {
        collectActions()
        coEvery { taskRepository.reorderTasks(1, any()) } returns Result.Success(true)
        feed(Task(1, "chleb", id = 1), Task(1, "cukinia", id = 2, categoryId = 5))

        vm.moveTaskToSection(fromId = 1, categoryId = 5, first = true)
        vm.triggerCommand(Command.SaveOrder)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.reorderTasks(1, match { list -> list.map { it.id to it.categoryId } == listOf(1 to 5, 2 to 5) }) }
    }

    @Test
    fun `categories need a name, and renaming to the same name writes nothing`() = runTest(dispatcher) {
        collectActions()
        vm.triggerCommand(Command.NewCategory("  "))
        vm.triggerCommand(Command.RenameCategory(vegetables, " Warzywa "))
        vm.triggerCommand(Command.NewCategory(" Owoce "))
        advanceUntilIdle()

        coVerify(exactly = 0) { categoryRepository.renameCategory(any()) }
        coVerify(exactly = 1) { categoryRepository.createCategory(any(), any()) }
        coVerify { categoryRepository.createCategory(1, "Owoce") }
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
        coEvery { taskRepository.setAllTasks(1, false) } returns Result.Success(true)

        vm.getTasks(1)
        vm.triggerCommand(Command.RefreshTasks)
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.setAllTasks(1, false) }
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
