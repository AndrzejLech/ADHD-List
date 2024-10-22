package com.example.adhdlist.presentation.view.tasks.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import com.example.adhdlist.domain.database.tasks.UpdateTaskUseCase
import com.example.adhdlist.domain.navigation.NavigateBackUseCase
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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

    private var _listData = Channel<TaskList>()
    var listData: Flow<TaskList> = _listData.receiveAsFlow()

    private var _uiState = MutableStateFlow<UiState>(UiState.Normal)
    var uiState: StateFlow<UiState> = _uiState

    private var _feedList = Channel<MutableList<Task>>()
    var feedList: Flow<MutableList<Task>> = _feedList.receiveAsFlow()

    private val _actions = Channel<BaseAction>()
    val actions: Flow<BaseAction> = _actions.receiveAsFlow()

    private val _errors = Channel<BaseError>()
    val errors: Flow<BaseError> = _errors.receiveAsFlow()

    override fun triggerCommand(command: BaseCommand) {
        viewModelScope.launch {
            when (command) {
                Command.AddButtonClick -> {
                    Log.d(TAG, "Sending action")
                    if (newTaskMessage.value.isNotBlank()) {
                        _actions.send(Action.CreateTask)
                    } else {
                        _errors.send(Error.EmptyNewTask)
                    }
                }

                is Command.ChangeUiState -> {
                    Log.d("TasksViewModel", "Command: Change state to Edit")
                    _actions.send(Action.ChangeUiState(command.uiState))
                }

                is Command.UpdateListName -> {
                    Log.d("TasksViewModel", "Command: Edit List Name")
                    _actions.send(Action.UpdateListName)
                }

                is Command.ChangeState -> {
                    _actions.send(Action.UpdateTask(command.task))
                }

                is Command.SwipeTaskDelete -> {
                    _actions.send(Action.DeleteTask(command.task))
                }

                Command.ClearList -> {
                    _actions.send(Action.ClearTasks)
                }

                Command.NavigateBack -> {
                    _actions.send(Action.NavigateBack)
                }
            }
        }
    }

    override fun handleAction(action: BaseAction) {
        viewModelScope.launch {
            when (action) {
                Action.CreateTask -> {
                    Log.d(TAG, "creating task")
                    createTask()
                }

                Action.ClearTasks -> {
                    Log.d(TAG, "clearing all tasks")
                    clearTasks(list.id)
                }

                Action.NavigateBack -> {
                    Log.d(TAG, "navigating back")
                    navigateBack()
                }

                is Action.DeleteTask -> {
                    Log.d(TAG, "deleting task")
                    deleteTask(action.task)
                }

                is Action.UpdateTask -> {
                    Log.d(TAG, "updating task")
                    updateTask(action.task)
                }

                Action.UpdateListName -> {
                    Log.d(TAG, "updating list name")
                    updateListName()
                }

                is Action.ChangeUiState -> {
                    Log.d(TAG, "Action: change state to edit")
                    changeState(action.uiState)
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
                    result.data.collect { item ->
                        _feedList.send(item)
                    }
                }

                is Result.Error -> Error.UnknownError(result.error)
            }
        }
    }

    fun getListData(listId: Int) {
        viewModelScope.launch {
            val result = GetListUseCase(listRepository).execute(listId)
            Log.d(TAG, "Getting tasks result: ${result.javaClass}")
            when (result) {
                is Result.Success -> {
                    result.data.collect { item ->
                        _listData.send(item)
                    }
                }

                is Result.Error -> Error.UnknownError(result.error)
            }
        }
    }

    private fun createTask() {
        viewModelScope.launch {
            val result =
                CreateTaskUseCase(taskRepository).execute(Task(list.id, newTaskMessage.value))
            when (result) {
                is Result.Success -> newTaskMessage.value = ""
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    private fun updateTask(task: Task) {
        viewModelScope.launch {
            val result = UpdateTaskUseCase(taskRepository).execute(task)
            when (result) {
                is Result.Success -> Log.d(TAG, "Updated Tasks")
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            val result = DeleteTaskUseCase(taskRepository).execute(task)
            when (result) {
                is Result.Success -> Log.d(TAG, "Deleted Task")
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    private fun clearTasks(listId: Int) {
        viewModelScope.launch {
            val result = ClearTasksUseCase(taskRepository).execute(listId)
            when (result) {
                is Result.Success -> Log.d(TAG, "Clear Task")
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
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
        data class ChangeUiState(val uiState: UiState) : BaseCommand()
        data class ChangeState(val task: Task) : BaseCommand()
        data class SwipeTaskDelete(val task: Task) : BaseCommand()
    }

    sealed class Action : BaseAction() {
        object CreateTask : BaseAction()
        object ClearTasks : BaseAction()
        object NavigateBack : BaseAction()
        object UpdateListName : BaseAction()
        data class ChangeUiState(val uiState: UiState) : BaseAction()
        data class DeleteTask(val task: Task) : BaseAction()
        data class UpdateTask(val task: Task) : BaseAction()
    }

    sealed class Error : BaseError() {
        object EmptyNewTask : BaseError()
        data class UnknownError(val cause: Exception) : BaseError()
    }
}