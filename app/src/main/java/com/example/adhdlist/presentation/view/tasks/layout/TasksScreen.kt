package com.example.adhdlist.presentation.view.tasks.layout

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adhdlist.MainActivity
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.presentation.common.AddTextField
import com.example.adhdlist.presentation.common.ConfirmDialog
import com.example.adhdlist.presentation.common.EmptyState
import com.example.adhdlist.presentation.common.ProgressBar
import com.example.adhdlist.presentation.common.TextInputDialog
import com.example.adhdlist.presentation.view.lists.layout.progressText
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel
import com.example.adhdlist.ui.theme.yellowTopBarColors
import com.example.adhdlist.presentation.view.tasks.viewmodel.TasksViewModel.Command
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    list: TaskList,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val feedList by viewModel.feedList.collectAsState()
    val listData by viewModel.listData.collectAsState()
    val current = listData ?: list
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    // Screen-level scope: a row's own scope dies with the row, which would cancel its undo snackbar.
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    viewModel.list = list

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = yellowTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.triggerCommand(Command.NavigateBack) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            modifier = Modifier.testTag("Title"),
                            text = current.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (current.remoteId != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Shared list",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                    }
                    if (viewModel.canShare) {
                        IconButton(onClick = { viewModel.triggerCommand(Command.Share) }) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = "Share")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Uncheck all") },
                                leadingIcon = { Icon(Icons.Outlined.RemoveDone, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.triggerCommand(Command.RefreshTasks)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete everything") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AddTextField(
                modifier = Modifier
                    .testTag("TasksTextField")
                    .fillMaxWidth(),
                value = viewModel.newTaskMessage.value,
                onTextChanged = { viewModel.newTaskMessage.value = it },
                onAddButtonClick = { viewModel.triggerCommand(Command.AddButtonClick) },
                placeholder = "Add something",
                multiLine = true
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (feedList.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = "Nothing here yet",
                    text = "Add anything below: things to do, buy, pack or remember."
                )
            } else {
                val done = feedList.count { it.state }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        if (done == feedList.size) "All ticked. Nice work." else progressText(done, feedList.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(done, feedList.size)
                }
                val lazyListState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    viewModel.moveTask(from.index, to.index)
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .testTag("TaskLazyList")
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(feedList, key = { _, task -> task.id }) { index, task ->
                        ReorderableItem(reorderState, key = task.id) { isDragging ->
                            TaskRow(
                                index = index,
                                task = task,
                                isDragging = isDragging,
                                onCheckedChange = { viewModel.triggerCommand(Command.ChangeState(task, it)) },
                                onEdit = { editing = task },
                                onDelete = {
                                    viewModel.triggerCommand(Command.SwipeTaskDelete(task))
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            "Deleted “${task.message}”",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.triggerCommand(Command.RestoreTask(task))
                                        }
                                    }
                                },
                                handleModifier = Modifier.draggableHandle(
                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { viewModel.triggerCommand(Command.SaveOrder) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename list",
            initial = current.name,
            label = "List name",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.triggerCommand(Command.RenameList(it))
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }
    editing?.let { task ->
        TextInputDialog(
            title = "Edit",
            initial = task.message,
            label = "Item",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.triggerCommand(Command.EditItem(task, it))
                editing = null
            },
            onDismiss = { editing = null },
            multiLine = true
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Delete everything?",
            text = "This removes everything in “${current.name}”. It can't be undone.",
            confirmLabel = "Delete all",
            onConfirm = {
                viewModel.triggerCommand(Command.ClearList)
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }

    LaunchedEffect(key1 = viewModel.feedList) {
        viewModel.getTasks(list.id)
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
            snackbarHostState.showSnackbar(viewModel.errorMessage, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(key1 = viewModel.shareCodes) {
        viewModel.shareCodes.collect { code ->
            val name = viewModel.listData.value?.name ?: list.name
            val text = "Let's do “$name” together in ADHD List.\n" +
                "Join code: $code\n" +
                "Or open: adhdlist://join/$code"
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(
                Intent.createChooser(send, "Share “$name”")
                    // The app accepts invite texts itself; offering it here would be a dead end.
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
            )
        }
    }
}
