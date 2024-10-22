package com.example.adhdlist.presentation.view.lists.layout

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adhdlist.presentation.common.AddTextField
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    viewModel: ListsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val feedListState = viewModel.feedList.collectAsState(initial = mutableListOf())
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.background
                ),
                title = { Text(text = "ADHD List") },
            )
        },
        bottomBar = {
            AddTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.newListName,
                onTextChanged = {
                    viewModel.newListName = it
                },
                onAddButtonClick = {
                    viewModel.triggerCommand(ListsViewModel.Command.AddButtonClick)
                },
                placeholder = "Type in here to add list"
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .heightIn(max = 300.dp),
        ) {
            feedListState.value.forEach { item ->
                item {
                    Log.d("ListScreen", item.toString())
                    TaskListListElement(
                        taskList = item,
                        onClicked = {
                            viewModel.triggerCommand(ListsViewModel.Command.NavigateToList(item))
                        },
                        onItemSwiped = {
                            viewModel.triggerCommand(ListsViewModel.Command.DeleteList(item))
                        }
                    )
                }
            }

        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.getTaskList()
        Log.d("ListScreen", "Getting Tasks")
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.actions.collect { action ->
            viewModel.handleAction(action)
        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.errors.collect { error ->
            viewModel.handleError(context, error)
            snackbarHostState.showSnackbar(viewModel.errorMessage)
        }
    }
}