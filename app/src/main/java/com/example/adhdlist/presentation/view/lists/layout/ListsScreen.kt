package com.example.adhdlist.presentation.view.lists.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.presentation.common.AddTextField
import com.example.adhdlist.presentation.common.ConfirmDialog
import com.example.adhdlist.presentation.common.EmptyState
import com.example.adhdlist.presentation.common.TextInputDialog
import com.example.adhdlist.presentation.view.lists.viewmodel.ListsViewModel
import com.example.adhdlist.ui.theme.yellowTopBarColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    joinCode: String? = null,
    onJoinCodeHandled: () -> Unit = {},
    viewModel: ListsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val feedList by viewModel.feedList.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var pendingDelete by remember { mutableStateOf<TaskList?>(null) }
    var joinDialogCode by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<TaskList?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = yellowTopBarColors(),
                title = { Text(text = "ADHD List") },
                actions = {
                    if (viewModel.canShare) {
                        TextButton(
                            onClick = { joinDialogCode = "" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join list")
                        }
                    }
                }
            )
        },
        bottomBar = {
            AddTextField(
                modifier = Modifier
                    .testTag("ListsTextField")
                    .fillMaxWidth(),
                value = viewModel.newListName,
                onTextChanged = {
                    viewModel.newListName = it
                },
                onAddButtonClick = {
                    viewModel.triggerCommand(ListsViewModel.Command.AddButtonClick)
                },
                placeholder = "New list name"
            )
        }
    ) { innerPadding ->
        if (feedList.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Checklist,
                title = "No lists yet",
                text = "Type a name below and tap + to make your first list.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                viewModel.moveList(from.index, to.index)
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .testTag("ListLazyList")
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(feedList, key = { _, item -> item.list.id }) { index, item ->
                    ReorderableItem(reorderState, key = item.list.id) { isDragging ->
                        ListCard(
                            index = index,
                            item = item,
                            isDragging = isDragging,
                            onClick = {
                                viewModel.triggerCommand(ListsViewModel.Command.NavigateToList(item.list))
                            },
                            onDeleteRequest = { pendingDelete = item.list },
                            onRename = { renaming = item.list },
                            handleModifier = Modifier.draggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { viewModel.triggerCommand(ListsViewModel.Command.SaveOrder) }
                            )
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { list ->
        val shared = list.remoteId != null
        ConfirmDialog(
            title = if (shared) "Leave “${list.name}”?" else "Delete “${list.name}”?",
            text = if (shared) "It stays for the other people. You can join again with the code."
            else "This removes the list and everything in it. It can't be undone.",
            confirmLabel = if (shared) "Leave" else "Delete",
            onConfirm = {
                viewModel.triggerCommand(ListsViewModel.Command.DeleteList(list))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
    renaming?.let { list ->
        TextInputDialog(
            title = "Rename list",
            initial = list.name,
            label = "List name",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.triggerCommand(ListsViewModel.Command.RenameList(list, it))
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    joinDialogCode?.let { code ->
        TextInputDialog(
            title = "Join a shared list",
            initial = code,
            label = "Join code",
            confirmLabel = "Join",
            onConfirm = {
                viewModel.triggerCommand(ListsViewModel.Command.Join(it))
                joinDialogCode = null
            },
            onDismiss = { joinDialogCode = null }
        )
    }

    LaunchedEffect(joinCode) {
        // Opened from a shared link or message: confirm before joining, never join silently.
        if (joinCode != null && viewModel.canShare) joinDialogCode = joinCode
        if (joinCode != null) onJoinCodeHandled()
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.getTaskList()
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.actions.collect { action ->
            viewModel.handleAction(action)
        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.errors.collect { error ->
            viewModel.handleError(context, error)
            snackbarHostState.showSnackbar(viewModel.errorMessage, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }
}
