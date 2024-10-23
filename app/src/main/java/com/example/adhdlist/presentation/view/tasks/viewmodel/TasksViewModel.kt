package com.example.adhdlist.presentation.view.tasks.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewModelScope
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.data.room.task.TaskRepository
import com.example.adhdlist.domain.database.lists.GetListUseCase
import com.example.adhdlist.domain.database.lists.UpdateListUseCase
import com.example.adhdlist.domain.database.tasks.ClearTasksUseCase
import com.example.adhdlist.domain.database.tasks.CreateTaskUseCase
import com.example.adhdlist.domain.database.tasks.DeleteTaskUseCase
import com.example.adhdlist.domain.database.tasks.GetTasksUseCase
import com.example.adhdlist.domain.database.tasks.RefreshTasksUseCase
import com.example.adhdlist.domain.database.tasks.UpdateTaskUseCase
import com.example.adhdlist.domain.navigation.NavigateBackUseCase
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.base.BaseViewModel
import com.example.adhdlist.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
    private val navigationRepository: NavigationRepository
) : BaseViewModel() {
    override val TAG = "TasksViewModel"
    var newTaskMessage: MutableState<String> = mutableStateOf("")
    var errorMessage: String = ""
    lateinit var list: TaskList

    private var _feedList = MutableStateFlow<SnapshotStateList<Task>>(mutableStateListOf())
    var feedList: StateFlow<SnapshotStateList<Task>> = _feedList.asStateFlow()

    private var _listData = Channel<TaskList>()
    var listData: Flow<TaskList> = _listData.receiveAsFlow()

    private var _uiState = MutableStateFlow<UiState>(UiState.Normal)
    var uiState: StateFlow<UiState> = _uiState

    private val _actions = Channel<BaseAction>()
    val actions: Flow<BaseAction> = _actions.receiveAsFlow()

    private val _errors = Channel<BaseError>()
    val errors: Flow<BaseError> = _errors.receiveAsFlow()

    override fun triggerCommand(command: BaseCommand) {
        viewModelScope.launch {
            when (command) {
                Command.AddButtonClick -> {
                    if (newTaskMessage.value.isNotBlank()) {
                        Logger.d(TAG, "Command", "CreatingTask")
                        _actions.send(Action.CreateTask)
                    } else {
                        Logger.d(TAG, "Command", "EmptyTaskError")
                        _errors.send(Error.EmptyNewTask)
                    }
                }

                is Command.ChangeUiState -> {
                    Logger.d(TAG, "Command", "ChangeUiState")
                    _actions.send(Action.ChangeUiState(command.uiState))
                }

                is Command.UpdateListName -> {
                    Logger.d(TAG, "Command", "UpdateListName")
                    _actions.send(Action.UpdateListName)
                }

                is Command.ChangeState -> {
                    Logger.d(TAG, "Command", "ChangingTaskState")
                    _actions.send(Action.UpdateTask(command.task))
                }

                is Command.SwipeTaskDelete -> {
                    Logger.d(TAG, "Command", "DeletingTask")
                    _actions.send(Action.DeleteTask(command.task))
                }

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
                Action.CreateTask -> {
                    Logger.d(TAG, "Action", "Creating Task")
                    createTask()
                }

                Action.ClearTasks -> {
                    Logger.d(TAG, "Action", "Deleting All Task")
                    clearTasks(list.id)
                }

                Action.NavigateBack -> {
                    Logger.d(TAG, "Action", "Navigating Back")
                    navigateBack()
                }

                is Action.DeleteTask -> {
                    Logger.d(TAG, "Action", "Deleting Task")
                    deleteTask(action.task)
                }

                is Action.UpdateTask -> {
                    Logger.d(TAG, "Action", "Updating Task")
                    updateTask(action.task)
                }

                Action.UpdateListName -> {
                    Logger.d(TAG, "Action", "Updating List Name")
                    updateListName()
                }

                is Action.ChangeUiState -> {
                    Logger.d(TAG, "Action", "Changing UiState")
                    changeState(action.uiState)
                }

                Action.RefreshTasks -> {
                    Logger.d(TAG, "Action", "Refreshing All Tasks")
                    refreshTasks()
                }
            }
        }
    }

    override fun handleError(context: Context, error: BaseError) {
        when (error) {
            is Error.EmptyNewTask -> errorMessage = "Task cannot be empty."
            is Error.UnknownError -> Log.e(TAG, error.toString())
        }

    }

    fun getTasks(listId: Int) {
        viewModelScope.launch {
            val result = GetTasksUseCase(taskRepository).execute(listId)
            Log.d(TAG, "Getting tasks result: ${result.javaClass}")
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Getting Task ended with Success")
                    result.data.collect { item ->
                        _feedList.value = item.toMutableStateList()
                    }
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Getting Task ended with Errors")
                    Error.UnknownError(result.error)
                }
            }
        }
    }

    fun getListData(listId: Int) {
        viewModelScope.launch {
            val result = GetListUseCase(listRepository).execute(listId)
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Getting List Data ended with Success")
                    result.data.collect { item ->
                        _listData.send(item)
                    }
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Getting List Data ended with Errors")
                    Error.UnknownError(result.error)
                }
            }
        }
    }

    private fun createTask() {
        viewModelScope.launch {
            val result =
                CreateTaskUseCase(taskRepository).execute(Task(list.id, newTaskMessage.value))
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Creating Task ended with Success")
                    newTaskMessage.value = ""
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Creating Task ended with Errors")
                    _errors.send(Error.UnknownError(result.error))
                }
            }
        }
    }

    private fun updateTask(task: Task) {
        viewModelScope.launch {
            val result = UpdateTaskUseCase(taskRepository).execute(task)
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Updating Task ended with Success")
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Updating Task ended with Errors")
                    _errors.send(Error.UnknownError(result.error))
                }
            }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            val result = DeleteTaskUseCase(taskRepository).execute(task)
            when (result) {
                is Result.Success -> {
                    Logger.d(TAG, "Methode", "Deleting Task ended with Success")
                }

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Deleting Task ended with Errors")
                    _errors.send(Error.UnknownError(result.error))
                }
            }
        }
    }

    private fun clearTasks(listId: Int) {
        viewModelScope.launch {
            val result = ClearTasksUseCase(taskRepository).execute(listId)
            when (result) {
                is Result.Success -> Logger.d(
                    TAG,
                    "Methode",
                    "Deleting All Tasks ended with Success"
                )

                is Result.Error -> {
                    Logger.d(TAG, "Methode", "Deleting All Tasks ended with Errors")
                    _errors.send(Error.UnknownError(result.error))
                }
            }
        }
    }

    private fun updateListName() {
        viewModelScope.launch {
            val updatedList = list
            updatedList.name = newTaskMessage.value
            newTaskMessage.value = ""
            val result = UpdateListUseCase(listRepository).execute(list)
            _listData.send(updatedList)
            _uiState.value = UiState.Normal

            when (result) {
                is Result.Success -> Log.d(TAG, "Update List Name")
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    private fun changeState(uiState: UiState) {
        viewModelScope.launch {
            _uiState.value = uiState
        }
    }

    private fun refreshTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            val result1 = RefreshTasksUseCase(taskRepository).execute(list.id)
            when (result1) {
                is Result.Success -> {
                    val result2 = GetTasksUseCase(taskRepository).execute(list.id)
                    when (result2) {
                        is Result.Success -> {
                            _feedList.value = result2.data.first().toMutableStateList()
                            withContext(Dispatchers.Main) {
                                Logger.d(
                                    TAG,
                                    "refreshTasks",
                                    "Refreshing FeedList Succeeded ${_feedList.value}"
                                )
                            }
                        }

                        is Result.Error -> Logger.e(
                            TAG,
                            "refreshTasks",
                            "Refreshing FeedList ended with Errors: ${result2.error}"
                        )
                    }
                }

                is Result.Error -> Logger.e(
                    TAG,
                    "refreshTasks",
                    "Refreshing Task ended with Errors: ${result1.error}"
                )
            }
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            NavigateBackUseCase(navigationRepository).execute()
        }
    }

    sealed class UiState : BaseUiState() {
        object Edit : UiState()
        object Normal : UiState()
    }

    sealed class Command : BaseCommand() {
        object AddButtonClick : BaseCommand()
        object UpdateListName : BaseCommand()
        object ClearList : BaseCommand()
        object NavigateBack : BaseCommand()
        object RefreshTasks : BaseCommand()
        data class ChangeUiState(val uiState: UiState) : BaseCommand()
        data class ChangeState(val task: Task) : BaseCommand()
        data class SwipeTaskDelete(val task: Task) : BaseCommand()
    }

    sealed class Action : BaseAction() {
        object CreateTask : BaseAction()
        object ClearTasks : BaseAction()
        object NavigateBack : BaseAction()
        object UpdateListName : BaseAction()
        object RefreshTasks : BaseAction()
        data class ChangeUiState(val uiState: UiState) : BaseAction()
        data class DeleteTask(val task: Task) : BaseAction()
        data class UpdateTask(val task: Task) : BaseAction()
    }

    sealed class Error : BaseError() {
        object EmptyNewTask : BaseError()
        data class UnknownError(val cause: Exception) : BaseError()
    }
}