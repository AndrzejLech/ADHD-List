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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
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
import com.example.adhdlist.data.model.Category
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
import sh.calvin.reorderable.ReorderableLazyListState
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
    // Swipe and the item's popup both delete through here, so both offer Undo.
    val onDelete: (Task) -> Unit = { task ->
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
    }
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    val categories by viewModel.categories.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmUncheck by remember { mutableStateOf(false) }
    var confirmDeleteChecked by remember { mutableStateOf(false) }
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
                                text = { Text("New category") },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    creatingCategory = true
                                }
                            )
                            // One entry that flips: "Uncheck all" while anything is ticked (asks first,
                            // it throws away progress), "Check all" once nothing is.
                            if (feedList.any { it.state }) {
                                DropdownMenuItem(
                                    text = { Text("Uncheck all") },
                                    leadingIcon = { Icon(Icons.Outlined.RemoveDone, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmUncheck = true
                                    }
                                )
                            } else if (feedList.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Check all") },
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.triggerCommand(Command.CheckAll)
                                    }
                                )
                            }
                            // Greyed out rather than hidden, so the menu looks the same every time.
                            DropdownMenuItem(
                                enabled = feedList.any { it.state },
                                text = { Text("Delete checked") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDeleteChecked = true
                                }
                            )
                            DropdownMenuItem(
                                enabled = feedList.isNotEmpty() || categories.isNotEmpty(),
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
            val addTarget = categories.firstOrNull { it.id == viewModel.addTargetId.value }
            AddTextField(
                modifier = Modifier
                    .testTag("TasksTextField")
                    .fillMaxWidth(),
                value = viewModel.newTaskMessage.value,
                onTextChanged = { viewModel.newTaskMessage.value = it },
                onAddButtonClick = { viewModel.triggerCommand(Command.AddButtonClick) },
                placeholder = addTarget?.let { "Add to ${it.name}" } ?: "Add something",
                multiLine = true,
                target = addTarget?.name,
                onClearTarget = { viewModel.triggerCommand(Command.AddTo(null)) }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (feedList.isEmpty() && categories.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = "Nothing here yet",
                    text = "Add anything below: things to do, buy, pack or remember."
                )
            } else {
                val done = feedList.count { it.state }
                if (feedList.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            if (done == feedList.size) "All ticked. Nice work." else progressText(done, feedList.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(done, feedList.size)
                    }
                }
                val lazyListState = rememberLazyListState()
                // Built here, from one read of the items and categories, not inside LazyColumn's
                // builder: that runs later and would see newer data than topItems, so a just-moved
                // item showed twice (at the top and in its category) and crashed the list.
                val categoryIds = categories.mapTo(HashSet()) { it.id }
                val topItems = feedList.filter { it.categoryId == null || it.categoryId !in categoryIds }
                val rows = buildList {
                    topItems.forEach { add(ListRow.Item(it)) }
                    categories.forEach { category ->
                        val inCategory = feedList.filter { it.categoryId == category.id }
                        add(ListRow.Header(category, inCategory.count { it.state }, inCategory.size))
                        if (category.expanded) inCategory.forEach { add(ListRow.Item(it)) }
                    }
                }
                // Keys are "item-<id>" and "category-<id>"; LazyColumn indices are indices into rows.
                // Headers never move, but dragging an item across one moves it into that section.
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val fromId = itemId(from.key) ?: return@rememberReorderableLazyListState
                    val toId = itemId(to.key)
                    val header = rows.getOrNull(to.index) as? ListRow.Header
                    when {
                        toId != null -> viewModel.moveTask(fromId, toId)
                        header == null -> Unit
                        // Down past a header: first item of that category.
                        to.index > from.index -> viewModel.moveTaskToSection(fromId, header.category.id, first = true)
                        // Up past its own header: last item of the section above.
                        else -> viewModel.moveTaskToSection(fromId, sectionAbove(rows, to.index), first = false)
                    }
                }
                // Position among the items shown, for the "TaskListElement$n" test tags.
                val rowIndex = rows.filterIsInstance<ListRow.Item>().withIndex().associate { (i, row) -> row.task.id to i }

                val onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                val onDragStopped = { viewModel.triggerCommand(Command.SaveOrder) }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .testTag("TaskLazyList")
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        when (row) {
                            is ListRow.Item -> ReorderableTaskRow(
                                reorderState, rowIndex[row.task.id] ?: 0, row.task, viewModel,
                                { editing = it }, onDelete, onDragStarted, onDragStopped
                            )
                            // Enabled so a dragged item can be dropped on it (a disabled row is never a drop target);
                            // it has no drag handle, so the header itself can't be dragged.
                            is ListRow.Header -> ReorderableItem(reorderState, key = row.key) {
                                CategoryHeader(
                                    category = row.category,
                                    ticked = row.ticked,
                                    total = row.total,
                                    isAddTarget = viewModel.addTargetId.value == row.category.id,
                                    onToggle = { viewModel.triggerCommand(Command.ToggleCategory(row.category)) },
                                    onAddHere = { viewModel.triggerCommand(Command.AddTo(row.category)) },
                                    onEdit = { editingCategory = row.category },
                                    onDeleteRequest = { deletingCategory = row.category }
                                )
                            }
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
        var categoryId by remember(task.id) { mutableStateOf(task.categoryId) }
        TextInputDialog(
            title = "Edit",
            initial = task.message,
            label = "Item",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.triggerCommand(Command.EditItem(task, it, categoryId))
                editing = null
            },
            onDismiss = { editing = null },
            multiLine = true,
            extraContent = if (categories.isEmpty()) null else {
                { CategoryChooser(categories, categoryId) { categoryId = it } }
            },
            extraAction = "Delete" to {
                editing = null
                onDelete(task)
            }
        )
    }
    if (creatingCategory) {
        TextInputDialog(
            title = "New category",
            initial = "",
            label = "Category name",
            confirmLabel = "Add",
            onConfirm = {
                viewModel.triggerCommand(Command.NewCategory(it))
                creatingCategory = false
            },
            onDismiss = { creatingCategory = false }
        )
    }
    editingCategory?.let { category ->
        TextInputDialog(
            title = "Category",
            initial = category.name,
            label = "Category name",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.triggerCommand(Command.RenameCategory(category, it))
                editingCategory = null
            },
            onDismiss = { editingCategory = null },
            extraAction = "Delete" to {
                editingCategory = null
                deletingCategory = category
            }
        )
    }
    deletingCategory?.let { category ->
        val count = feedList.count { it.categoryId == category.id }
        ConfirmDialog(
            title = "Delete “${category.name}”?",
            text = when (count) {
                0 -> "The category is empty."
                1 -> "This also deletes its 1 item. It can't be undone."
                else -> "This also deletes its $count items. It can't be undone."
            },
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.triggerCommand(Command.DeleteCategory(category))
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null }
        )
    }
    if (confirmUncheck) {
        ConfirmDialog(
            title = "Uncheck everything?",
            text = feedList.count { it.state }.let { n ->
                if (n == 1) "This unticks the ticked item in “${current.name}”."
                else "This unticks all $n ticked items in “${current.name}”."
            },
            confirmLabel = "Uncheck all",
            onConfirm = {
                viewModel.triggerCommand(Command.RefreshTasks)
                confirmUncheck = false
            },
            onDismiss = { confirmUncheck = false }
        )
    }
    if (confirmDeleteChecked) {
        ConfirmDialog(
            title = "Delete checked items?",
            text = feedList.count { it.state }.let { n ->
                if (n == 1) "This removes the ticked item from “${current.name}”. It can't be undone."
                else "This removes all $n ticked items from “${current.name}”. It can't be undone."
            },
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.triggerCommand(Command.DeleteChecked)
                confirmDeleteChecked = false
            },
            onDismiss = { confirmDeleteChecked = false }
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
    LaunchedEffect(key1 = viewModel.categories) {
        viewModel.getCategories(list.id)
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

@Composable
private fun LazyItemScope.ReorderableTaskRow(
    reorderState: ReorderableLazyListState,
    index: Int,
    task: Task,
    viewModel: TasksViewModel,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    ReorderableItem(reorderState, key = ListRow.Item(task).key) { isDragging ->
        TaskRow(
            index = index,
            task = task,
            isDragging = isDragging,
            onCheckedChange = { viewModel.triggerCommand(Command.ChangeState(task, it)) },
            onEdit = { onEdit(task) },
            onDelete = { onDelete(task) },
            handleModifier = Modifier.draggableHandle(
                onDragStarted = { onDragStarted() },
                onDragStopped = { onDragStopped() }
            )
        )
    }
}

private sealed interface ListRow {
    val key: String

    data class Item(val task: Task) : ListRow {
        override val key get() = "item-${task.id}"
    }

    data class Header(val category: Category, val ticked: Int, val total: Int) : ListRow {
        override val key get() = "category-${category.id}"
    }
}

private fun itemId(key: Any?) = (key as? String)?.removePrefix("item-")?.takeIf { it != key }?.toIntOrNull()

/** The category of the section that ends just above row [index]: the nearest header or item above it (null: the top). */
private fun sectionAbove(rows: List<ListRow>, index: Int): Int? =
    when (val row = rows.getOrNull(index - 1)) {
        is ListRow.Header -> row.category.id
        is ListRow.Item -> row.task.categoryId
        null -> null
    }
