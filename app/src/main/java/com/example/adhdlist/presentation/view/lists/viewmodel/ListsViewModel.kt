package com.example.adhdlist.presentation.view.lists.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.database.lists.CreateListUseCase
import com.example.adhdlist.domain.database.lists.DeleteListUseCase
import com.example.adhdlist.domain.database.lists.GetListsUseCase
import com.example.adhdlist.domain.navigation.NavigateToListUseCase
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ListRepository,
    private val navigationRepository: NavigationRepository
) : BaseViewModel() {
    override val TAG: String = "ListViewModel"
    var newListName: String = ""
    var errorMessage: String = ""

    private var _feedList = Channel<MutableList<TaskList>>()
    var feedList: Flow<MutableList<TaskList>> = _feedList.receiveAsFlow()

    private val _actions = Channel<BaseAction>()
    val actions: Flow<BaseAction> = _actions.receiveAsFlow()

    private val _errors = Channel<BaseError>()
    val errors: Flow<BaseError> = _errors.receiveAsFlow()

    override fun triggerCommand(command: BaseCommand) {
        viewModelScope.launch {
            when (command) {
                Command.AddButtonClick -> if (newListName.isNotBlank()) {
                    _actions.send(Action.CreateList)
                } else {
                    _errors.send(Error.EmptyListName)
                }

                is Command.DeleteList -> _actions.send(Action.DeleteList(command.list))
                is Command.NavigateToList -> _actions.send(Action.NavigateToList(command.list))
            }
        }
    }

    override fun handleAction(action: BaseAction) {
        viewModelScope.launch {
            when (action) {
                Action.CreateList -> createList()
                is Action.DeleteList -> deleteList(action.list)
                is Action.NavigateToList -> navigateToList(action.list)
            }
        }
    }

    override fun handleError(context: Context, error: BaseError) {
        when (error) {
            is Error.EmptyListName -> errorMessage = "List name cannot be empty."
            is Error.UnknownError -> Log.e(TAG, error.toString())
        }

    }

    private fun createList() {
        viewModelScope.launch {
            val result = CreateListUseCase(listRepository).execute(
                TaskList(newListName.trim())
            )
            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Created List")
                    newListName = ""
                }

                is Result.Error -> _errors.send(
                    Error.UnknownError(
                        result.error
                    )
                )
            }
        }
    }

    private fun navigateToList(list: TaskList) {
        NavigateToListUseCase(navigationRepository).execute(list)
    }

    private fun deleteList(list: TaskList) {
        viewModelScope.launch {
            val result = DeleteListUseCase(listRepository).execute(list)
            when (result) {
                is Result.Success -> {}
                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    fun getTaskList() {
        viewModelScope.launch {
            val result = GetListsUseCase(listRepository).execute()
            Log.d(TAG, "Getting tasks result: ${result.javaClass}")
            when (result) {
                is Result.Success -> {
                    result.data.collect { item ->
                        _feedList.send(item)
                    }
                }

                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    sealed class Command : BaseCommand() {
        object AddButtonClick : BaseCommand()
        data class NavigateToList(val list: TaskList) : BaseCommand()
        data class DeleteList(val list: TaskList) : BaseCommand()
    }

    sealed class Action : BaseAction() {
        object CreateList : BaseAction()
        data class DeleteList(val list: TaskList) : BaseAction()
        data class NavigateToList(val list: TaskList) : BaseAction()
    }

    sealed class Error : BaseError() {
        object EmptyListName : BaseError()
        data class UnknownError(val cause: Exception) : BaseError()
    }
}