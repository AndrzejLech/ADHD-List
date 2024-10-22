package com.example.adhdlist.presentation.view.tasks.layout

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.presentation.common.AddTextField
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    list: TaskList,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val feedListState = viewModel.feedList.collectAsState(initial = mutableListOf())
    val listData = viewModel.listData.collectAsState(initial = list)
    val uiState = viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    viewModel.list = list

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.triggerCommand(TasksViewModel.Command.NavigateBack)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = listData.value.name)
                        IconButton(
                            onClick = {
                                Log.d("TasksScreen", "Edit Click")
                                viewModel.triggerCommand(TasksViewModel.Command.ChangeUiState(TasksViewModel.UiState.Edit))
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {

                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "Restore"
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.triggerCommand(TasksViewModel.Command.ClearList)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "Delete"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                AddTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
                    value = viewModel.newTaskMessage.value,
                    onTextChanged = { viewModel.newTaskMessage.value = it },
                    onAddButtonClick = {
                        Log.d("TasksScreen", "ui clicked")
                        when (uiState.value) {
                            TasksViewModel.UiState.Edit -> viewModel.triggerCommand(TasksViewModel.Command.UpdateListName)
                            TasksViewModel.UiState.Normal -> viewModel.triggerCommand(TasksViewModel.Command.AddButtonClick)
                        }
                    },
                    placeholder = when(uiState.value){
                        TasksViewModel.UiState.Normal -> "Type in here to add task"
                        TasksViewModel.UiState.Edit -> "Type here to edit list name"

                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .heightIn(max = 300.dp),
        ) {
            feedListState.value.forEachIndexed { index, item ->
                item {
                    TaskListElement(
                        index = index + 1,
                        task = item,
                        onItemSwiped = {
                            viewModel.triggerCommand(TasksViewModel.Command.SwipeTaskDelete(item))
                        },
                        onCheckboxClicked = {
                            item.state = it
                            viewModel.triggerCommand(TasksViewModel.Command.ChangeState(item))
                        })
                }
            }
        }
    }
    BackHandler {
        when(uiState.value){
            TasksViewModel.UiState.Edit -> viewModel.triggerCommand(TasksViewModel.Command.ChangeUiState(TasksViewModel.UiState.Normal))
            TasksViewModel.UiState.Normal -> viewModel.triggerCommand(TasksViewModel.Command.NavigateBack)
        }
    }
    LaunchedEffect(key1 = viewModel.feedList) {
        viewModel.getTasks(list.id)
        Log.d("TasksScreen", "Getting Tasks")
    }
    LaunchedEffect(key1 = viewModel.listData) {
        viewModel.getListData(list.id)
    }
    LaunchedEffect(key1 = viewModel.actions) {
        viewModel.actions.collect { action ->
            viewModel.handleAction(action)
        }
    }
    LaunchedEffect(key1 = viewModel.errors) {
        viewModel.errors.collect { error ->
            viewModel.handleError(context, error)
            snackbarHostState.showSnackbar(viewModel.errorMessage)
        }
    }
    LaunchedEffect(key1 = viewModel.uiState) {
        Log.d("TasksScreen", "Changing state")
        viewModel.uiState.collect{ newState ->
            when (newState) {
                TasksViewModel.UiState.Edit -> {
                    Log.d("TaskScreen", "Ui: StateEdit")
                    viewModel.newTaskMessage.value = listData.value.name
                    focusRequester.requestFocus()
                }

                TasksViewModel.UiState.Normal -> {
                    viewModel.newTaskMessage.value
                }
            }
        }
    }
}