package com.example.adhdlist.presentation.view.tasks.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewModelScope
import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.room.category.CategoryRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.data.room.task.TaskRepository
import com.example.adhdlist.domain.database.categories.CreateCategoryUseCase
import com.example.adhdlist.domain.database.categories.DeleteCategoryUseCase
import com.example.adhdlist.domain.database.categories.GetCategoriesUseCase
import com.example.adhdlist.domain.database.categories.RenameCategoryUseCase
import com.example.adhdlist.domain.database.categories.SetCategoryExpandedUseCase
import com.example.adhdlist.domain.database.lists.GetListUseCase
import com.example.adhdlist.domain.database.lists.ShareListUseCase
import com.example.adhdlist.domain.database.lists.UpdateListUseCase
import com.example.adhdlist.domain.database.tasks.CheckAllTasksUseCase
import com.example.adhdlist.domain.database.tasks.ClearTasksUseCase
import com.example.adhdlist.domain.database.tasks.CreateTaskUseCase
import com.example.adhdlist.domain.database.tasks.DeleteCheckedTasksUseCase
import com.example.adhdlist.domain.database.tasks.DeleteTaskUseCase
import com.example.adhdlist.domain.database.tasks.EditTaskUseCase
import com.example.adhdlist.domain.database.tasks.GetTasksUseCase
import com.example.adhdlist.domain.database.tasks.RefreshTasksUseCase
import com.example.adhdlist.domain.database.tasks.ReorderTasksUseCase
import com.example.adhdlist.domain.database.tasks.RestoreTaskUseCase
import com.example.adhdlist.domain.database.tasks.UpdateTaskUseCase
import com.example.adhdlist.domain.navigation.NavigateBackUseCase
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.base.BaseViewModel
import com.example.adhdlist.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
    private val navigationRepository: NavigationRepository,
    private val categoryRepository: CategoryRepository
) : BaseViewModel() {
    override val TAG = "TasksViewModel"
    var newTaskMessage: MutableState<String> = mutableStateOf("")
    var errorMessage: String = ""
    lateinit var list: TaskList
    val canShare: Boolean get() = listRepository.canShare

    private var _feedList = MutableStateFlow<SnapshotStateList<Task>>(mutableStateListOf())
    var feedList: StateFlow<SnapshotStateList<Task>> = _feedList.asStateFlow()
    private var tasksJob: Job? = null

    // Latest list row from Room; null until loaded. The screen falls back to the navigation argument.
    private val _listData = MutableStateFlow<TaskList?>(null)
    val listData: StateFlow<TaskList?> = _listData.asStateFlow()
    private var listJob: Job? = null

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    private var categoriesJob: Job? = null

    // Where the bottom field adds: a category's id after its + was tapped, or null for the top of the list.
    val addTargetId: MutableState<Int?> = mutableStateOf(null)

    private val _shareCodes = Channel<String>()
    val shareCodes: Flow<String> = _shareCodes.receiveAsFlow()

    private val _actions = Channel<BaseAction>()
    val actions: Flow<BaseAction> = _actions.receiveAsFlow()

    private val _errors = Channel<BaseError>()
    val errors: Flow<BaseError> = _errors.receiveAsFlow()

    override fun triggerCommand(command: BaseCommand) {
        viewModelScope.launch {
            when (command) {
                Command.AddButtonClick -> {
                    if (newTaskMessage.value.trim().length > SharedListsRemote.MAX_TASK_LENGTH) {
                        _errors.send(Error.TaskTooLong)
                    } else if (newTaskMessage.value.isNotBlank()) {
                        Logger.d(TAG, "Command", "CreatingTask")
                        _actions.send(Action.CreateTask)
                    } else {
                        Logger.d(TAG, "Command", "EmptyTaskError")
                        _errors.send(Error.EmptyNewTask)
                    }
                }

                is Command.RenameList -> {
                    if (command.name.isNotBlank()) {
                        Logger.d(TAG, "Command", "RenameList")
                        _actions.send(Action.RenameList(command.name.trim()))
                    } else {
                        Logger.d(TAG, "Command", "EmptyListNameError")
                        _errors.send(Error.EmptyListName)
                    }
                }

                is Command.ChangeState -> {
                    Logger.d(TAG, "Command", "ChangingTaskState")
                    // A new row, not a mutation: the one on screen is Room's snapshot.
                    _actions.send(Action.UpdateTask(command.task.copy(state = command.checked)))
                }

                is Command.SwipeTaskDelete -> {
                    Logger.d(TAG, "Command", "DeletingTask")
                    _actions.send(Action.DeleteTask(command.task))
                }

                is Command.RestoreTask -> _actions.send(Action.RestoreTask(command.task))
                is Command.EditItem -> {
                    val text = command.text.trim()
                    when {
                        text.isEmpty() -> _errors.send(Error.EmptyNewTask)
                        text.length > SharedListsRemote.MAX_TASK_LENGTH -> _errors.send(Error.TaskTooLong)
                        text != command.task.message || command.categoryId != command.task.categoryId ->
                            _actions.send(Action.EditItem(command.task.copy(message = text, categoryId = command.categoryId)))
                    }
                }

                is Command.NewCategory -> if (command.name.isNotBlank()) {
                    _actions.send(Action.NewCategory(command.name.trim()))
                } else {
                    _errors.send(Error.EmptyCategoryName)
                }

                is Command.RenameCategory -> when {
                    command.name.isBlank() -> _errors.send(Error.EmptyCategoryName)
                    command.name.trim() != command.category.name ->
                        _actions.send(Action.RenameCategory(command.category.copy(name = command.name.trim())))
                }

                is Command.DeleteCategory -> _actions.send(Action.DeleteCategory(command.category))
                is Command.ToggleCategory -> _actions.send(Action.ToggleCategory(command.category))
                is Command.AddTo -> {
                    // Adding into a closed category opens it, so the new item is visible.
                    command.category?.takeIf { !it.expanded }?.let { _actions.send(Action.ToggleCategory(it)) }
                    addTargetId.value = command.category?.id
                }
                Command.SaveOrder -> _actions.send(Action.SaveOrder)
                Command.Share -> _actions.send(Action.Share)

                Command.CheckAll -> _actions.send(Action.CheckAll)
                Command.DeleteChecked -> _actions.send(Action.DeleteChecked)

                Command.RefreshTasks -> {
                    Logger.d(TAG, "Command", "RefreshingTasks")
                    _actions.send(Action.RefreshTasks)
                }

                Command.ClearList -> {
                    Logger.d(TAG, "Command", "DeletingAllTasks")
                    _actions.send(Action.ClearTasks)
                }

                Command.NavigateBack -> {
                    Logger.d(TAG, "Command", "NavigatingBack")
                    _actions.send(Action.NavigateBack)
                }
            }
        }
    }

    override fun handleAction(action: BaseAction) {
        viewModelScope.launch {
            when (action) {
                Action.CreateTask -> createTask()
                Action.ClearTasks -> clearTasks(list.id)
                Action.NavigateBack -> navigateBack()
                is Action.DeleteTask -> deleteTask(action.task)
                is Action.RestoreTask -> report(RestoreTaskUseCase(taskRepository).execute(action.task))
                is Action.UpdateTask -> report(UpdateTaskUseCase(taskRepository).execute(action.task))
                is Action.EditItem -> report(EditTaskUseCase(taskRepository).execute(action.task))
                is Action.NewCategory -> report(CreateCategoryUseCase(categoryRepository).execute(list.id, action.name))
                is Action.RenameCategory -> report(RenameCategoryUseCase(categoryRepository).execute(action.category))
                is Action.DeleteCategory -> {
                    if (addTargetId.value == action.category.id) addTargetId.value = null
                    report(DeleteCategoryUseCase(categoryRepository).execute(action.category))
                }
                is Action.ToggleCategory -> report(
                    SetCategoryExpandedUseCase(categoryRepository).execute(action.category, !action.category.expanded)
                )
                is Action.RenameList -> renameList(action.name)
                Action.RefreshTasks -> report(RefreshTasksUseCase(taskRepository).execute(list.id))
                Action.CheckAll -> report(CheckAllTasksUseCase(taskRepository).execute(list.id))
                Action.DeleteChecked -> report(DeleteCheckedTasksUseCase(taskRepository).execute(list.id))
                Action.SaveOrder -> report(ReorderTasksUseCase(taskRepository).execute(list.id, _feedList.value.toList()))
                Action.Share -> share()
            }
        }
    }

    override fun handleError(context: Context, error: BaseError) {
        when (error) {
            is Error.EmptyNewTask -> errorMessage = "Type something first."
            is Error.TaskTooLong -> errorMessage = "That's very long. Please keep it under ${SharedListsRemote.MAX_TASK_LENGTH} characters."
            is Error.EmptyListName -> errorMessage = "List name cannot be empty."
            is Error.EmptyCategoryName -> errorMessage = "Category name cannot be empty."
            is Error.ShareFailed -> errorMessage = "Couldn't share. Check your internet connection and try again."
            is Error.UnknownError -> {
                errorMessage = "Something went wrong."
                Log.e(TAG, error.toString())
            }
        }

    }

    private suspend fun report(result: Result<*, Exception>) {
        if (result is Result.Error) _errors.send(Error.UnknownError(result.error))
    }

    fun getTasks(listId: Int) {
        // Called again on every recomposition restart (e.g. rotation); keep a single Room subscription.
        if (tasksJob != null) return
        tasksJob = viewModelScope.launch {
            val result = GetTasksUseCase(taskRepository).execute(listId)
            Log.d(TAG, "Getting tasks result: ${result.javaClass}")
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Getting Task ended with Success")
                    result.data.collect { item ->
                        _feedList.value = item.toMutableStateList()
                    }
                }

                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    fun getCategories(listId: Int) {
        if (categoriesJob != null) return
        categoriesJob = viewModelScope.launch {
            when (val result = GetCategoriesUseCase(categoryRepository).execute(listId)) {
                is Result.Success -> result.data.collect { categories ->
                    _categories.value = categories
                    // The target category was deleted (here or by someone sharing the list): back to the top.
                    if (categories.none { it.id == addTargetId.value }) addTargetId.value = null
                }

                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    fun getListData(listId: Int) {
        if (listJob != null) return
        listJob = viewModelScope.launch {
            val result = GetListUseCase(listRepository).execute(listId)
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Getting List Data ended with Success")
                    result.data.collect { item ->
                        // Room emits null once the list is deleted (e.g. swiped away while this screen opened).
                        if (item == null) navigateBack() else _listData.value = item
                    }
                }

                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    private fun createTask() {
        // Take the text before suspending so a double tap can't insert it twice.
        val message = newTaskMessage.value.trim()
        if (message.isEmpty() || message.length > SharedListsRemote.MAX_TASK_LENGTH) return
        newTaskMessage.value = ""
        viewModelScope.launch {
            val result = CreateTaskUseCase(taskRepository).execute(Task(list.id, message, categoryId = addTargetId.value))
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Creating Task ended with Success")
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Creating Task ended with Errors")
                    if (newTaskMessage.value.isEmpty()) newTaskMessage.value = message
                    _errors.send(Error.UnknownError(result.error))
                }
            }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            report(DeleteTaskUseCase(taskRepository).execute(task))
        }
    }

    private fun clearTasks(listId: Int) {
        viewModelScope.launch {
            report(ClearTasksUseCase(taskRepository).execute(listId))
        }
    }

    /** No optimistic title change: the Room row is the only source, so a failed write shows no fake name. */
    private fun renameList(name: String) {
        val current = _listData.value ?: list
        viewModelScope.launch {
            report(UpdateListUseCase(listRepository).execute(current.copy(name = name)))
        }
    }

    /** Called on every drag step; only reorders what is shown. SaveOrder persists on drop. */
    /** Drag step onto another item: the dragged item takes its place and its category. */
    fun moveTask(fromId: Int, toId: Int) {
        val items = _feedList.value
        val from = items.indexOfFirst { it.id == fromId }
        val to = items.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return
        val categoryId = items[to].categoryId
        items.add(to, items.removeAt(from).copy(categoryId = categoryId))
    }

    /**
     * Drag step onto a category header: the item now belongs to [categoryId] (null: the top of the
     * list), at the start of that section if [first], else at its end. A closed category opens at
     * once, so the item being dragged never disappears under the finger.
     */
    fun moveTaskToSection(fromId: Int, categoryId: Int?, first: Boolean) {
        val items = _feedList.value
        val from = items.indexOfFirst { it.id == fromId }
        if (from < 0) return
        val moved = items.removeAt(from).copy(categoryId = categoryId)
        val section = items.indices.filter { items[it].categoryId == categoryId }
        val at = if (first) section.firstOrNull() ?: items.size else section.lastOrNull()?.plus(1) ?: items.size
        items.add(at, moved)
        _categories.value.firstOrNull { it.id == categoryId && !it.expanded }?.let { closed ->
            _categories.value = _categories.value.map { if (it.id == closed.id) it.copy(expanded = true) else it }
            viewModelScope.launch { report(SetCategoryExpandedUseCase(categoryRepository).execute(closed, true)) }
        }
    }

    private fun share() {
        viewModelScope.launch {
            when (val result = ShareListUseCase(listRepository).execute(list.id)) {
                is Result.Success -> _shareCodes.send(result.data)
                is Result.Error -> {
                    Log.e(TAG, "share", result.error)
                    _errors.send(Error.ShareFailed)
                }
            }
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            NavigateBackUseCase(navigationRepository).execute()
        }
    }

    sealed class Command : BaseCommand() {
        object AddButtonClick : BaseCommand()
        object ClearList : BaseCommand()
        object NavigateBack : BaseCommand()
        object RefreshTasks : BaseCommand()
        object CheckAll : BaseCommand()
        object DeleteChecked : BaseCommand()
        data class NewCategory(val name: String) : BaseCommand()
        data class RenameCategory(val category: Category, val name: String) : BaseCommand()
        data class DeleteCategory(val category: Category) : BaseCommand()
        data class ToggleCategory(val category: Category) : BaseCommand()
        data class AddTo(val category: Category?) : BaseCommand()
        object SaveOrder : BaseCommand()
        object Share : BaseCommand()
        data class RenameList(val name: String) : BaseCommand()
        data class ChangeState(val task: Task, val checked: Boolean) : BaseCommand()
        data class SwipeTaskDelete(val task: Task) : BaseCommand()
        data class RestoreTask(val task: Task) : BaseCommand()
        data class EditItem(val task: Task, val text: String, val categoryId: Int? = task.categoryId) : BaseCommand()
    }

    sealed class Action : BaseAction() {
        object CreateTask : BaseAction()
        object ClearTasks : BaseAction()
        object NavigateBack : BaseAction()
        object RefreshTasks : BaseAction()
        object CheckAll : BaseAction()
        object DeleteChecked : BaseAction()
        data class NewCategory(val name: String) : BaseAction()
        data class RenameCategory(val category: Category) : BaseAction()
        data class DeleteCategory(val category: Category) : BaseAction()
        data class ToggleCategory(val category: Category) : BaseAction()
        object SaveOrder : BaseAction()
        object Share : BaseAction()
        data class RenameList(val name: String) : BaseAction()
        data class DeleteTask(val task: Task) : BaseAction()
        data class RestoreTask(val task: Task) : BaseAction()
        data class UpdateTask(val task: Task) : BaseAction()
        data class EditItem(val task: Task) : BaseAction()
    }

    sealed class Error : BaseError() {
        object EmptyNewTask : BaseError()
        object TaskTooLong : BaseError()
        object EmptyListName : BaseError()
        object EmptyCategoryName : BaseError()
        object ShareFailed : BaseError()
        data class UnknownError(val cause: Exception) : BaseError()
    }
}
