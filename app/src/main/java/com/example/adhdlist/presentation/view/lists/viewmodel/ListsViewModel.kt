package com.example.adhdlist.presentation.view.lists.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.example.adhdlist.data.model.ListWithProgress
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.database.lists.CreateListUseCase
import com.example.adhdlist.domain.database.lists.DeleteListUseCase
import com.example.adhdlist.domain.database.lists.GetListsUseCase
import com.example.adhdlist.domain.database.lists.JoinListUseCase
import com.example.adhdlist.domain.database.lists.ReorderListsUseCase
import com.example.adhdlist.domain.database.lists.UpdateListUseCase
import com.example.adhdlist.domain.navigation.NavigateToListUseCase
import com.example.adhdlist.domain.util.Result
import com.example.adhdlist.presentation.base.BaseViewModel
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
class ListsViewModel @Inject constructor(
    private val listRepository: ListRepository,
    private val navigationRepository: NavigationRepository
) : BaseViewModel() {
    override val TAG: String = "ListViewModel"
    var newListName by mutableStateOf("")
    var errorMessage: String = ""
    val canShare: Boolean get() = listRepository.canShare

    private val _feedList = MutableStateFlow<MutableList<ListWithProgress>>(mutableListOf())
    val feedList: StateFlow<MutableList<ListWithProgress>> = _feedList.asStateFlow()
    private var feedJob: Job? = null

    private val _actions = Channel<BaseAction>()
    val actions: Flow<BaseAction> = _actions.receiveAsFlow()

    private val _errors = Channel<BaseError>()
    val errors: Flow<BaseError> = _errors.receiveAsFlow()

    // Neutral confirmations for the snackbar (not errors).
    private val _messages = Channel<String>()
    val messages: Flow<String> = _messages.receiveAsFlow()

    override fun triggerCommand(command: BaseCommand) {
        viewModelScope.launch {
            when (command) {
                Command.AddButtonClick -> if (newListName.isNotBlank()) {
                    _actions.send(Action.CreateList)
                } else {
                    _errors.send(Error.EmptyListName)
                }

                is Command.DeleteList -> _actions.send(Action.DeleteList(command.list))
                is Command.RenameList -> if (command.name.isNotBlank()) {
                    _actions.send(Action.RenameList(command.list.copy(name = command.name.trim())))
                } else {
                    _errors.send(Error.EmptyListName)
                }
                is Command.NavigateToList -> _actions.send(Action.NavigateToList(command.list))
                Command.SaveOrder -> _actions.send(Action.SaveOrder)
                is Command.Join -> {
                    val code = SharedListsRemote.findJoinCode(command.text)
                    if (code != null) _actions.send(Action.Join(code)) else _errors.send(Error.InvalidJoinCode)
                }
            }
        }
    }

    override fun handleAction(action: BaseAction) {
        viewModelScope.launch {
            when (action) {
                Action.CreateList -> createList()
                is Action.DeleteList -> deleteList(action.list)
                is Action.RenameList -> {
                    val result = UpdateListUseCase(listRepository).execute(action.list)
                    if (result is Result.Error) _errors.send(Error.UnknownError(result.error))
                }
                is Action.NavigateToList -> navigateToList(action.list)
                Action.SaveOrder -> saveOrder()
                is Action.Join -> join(action.code)
            }
        }
    }

    override fun handleError(context: Context, error: BaseError) {
        when (error) {
            is Error.EmptyListName -> errorMessage = "List name cannot be empty."
            is Error.InvalidJoinCode -> errorMessage = "That doesn't look like a join code. It has 20 letters and digits."
            is Error.JoinFailed -> errorMessage = "Couldn't join. Check the code and your internet connection."
            is Error.UnknownError -> {
                errorMessage = "Something went wrong."
                Log.e(TAG, error.toString())
            }
        }

    }

    private fun createList() {
        // Take the text before suspending so a double tap can't insert it twice.
        val name = newListName.trim()
        if (name.isEmpty()) return
        newListName = ""
        viewModelScope.launch {
            val result = CreateListUseCase(listRepository).execute(TaskList(name))
            when (result) {
                is Result.Success -> Log.d(TAG, "Created List")

                is Result.Error -> {
                    if (newListName.isEmpty()) newListName = name
                    _errors.send(Error.UnknownError(result.error))
                }
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

    /** Called on every drag step; only reorders what is shown. [saveOrder] persists on drop. */
    fun moveList(from: Int, to: Int) {
        _feedList.value = _feedList.value.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun saveOrder() {
        viewModelScope.launch {
            val result = ReorderListsUseCase(listRepository).execute(_feedList.value.map { it.list })
            if (result is Result.Error) _errors.send(Error.UnknownError(result.error))
        }
    }

    private fun join(code: String) {
        viewModelScope.launch {
            when (val result = JoinListUseCase(listRepository).execute(code)) {
                is Result.Success -> _messages.send("Joined. The list will appear in a moment.")
                is Result.Error -> {
                    Log.e(TAG, "join", result.error)
                    _errors.send(Error.JoinFailed)
                }
            }
        }
    }

    fun getTaskList() {
        // Called on every screen entry; one Room subscription per ViewModel is enough, StateFlow replays it.
        if (feedJob != null) return
        feedJob = viewModelScope.launch {
            val result = GetListsUseCase(listRepository).execute()
            Log.d(TAG, "Getting tasks result: ${result.javaClass}")
            when (result) {
                is Result.Success -> {
                    result.data.collect { item ->
                        _feedList.value = item
                    }
                }

                is Result.Error -> _errors.send(Error.UnknownError(result.error))
            }
        }
    }

    sealed class Command : BaseCommand() {
        object AddButtonClick : BaseCommand()
        object SaveOrder : BaseCommand()
        data class NavigateToList(val list: TaskList) : BaseCommand()
        data class DeleteList(val list: TaskList) : BaseCommand()
        data class RenameList(val list: TaskList, val name: String) : BaseCommand()
        data class Join(val text: String) : BaseCommand()
    }

    sealed class Action : BaseAction() {
        object CreateList : BaseAction()
        object SaveOrder : BaseAction()
        data class DeleteList(val list: TaskList) : BaseAction()
        data class RenameList(val list: TaskList) : BaseAction()
        data class NavigateToList(val list: TaskList) : BaseAction()
        data class Join(val code: String) : BaseAction()
    }

    sealed class Error : BaseError() {
        object EmptyListName : BaseError()
        object InvalidJoinCode : BaseError()
        object JoinFailed : BaseError()
        data class UnknownError(val cause: Exception) : BaseError()
    }
}
