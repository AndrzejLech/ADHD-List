package com.example.adhdlist.presentation.view.tasks.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.presentation.common.SwipeDeleteBackground
import com.example.adhdlist.presentation.common.rememberDeliberateSwipeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    index: Int,
    task: Task,
    isDragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    handleModifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // Flips at once on tap and re-syncs when Room delivers a different state, so fast taps never get lost.
    var checked by remember(task.state) { mutableStateOf(task.state) }
    val toggle = { value: Boolean ->
        checked = value
        onCheckedChange(value)
    }
    val dismissState = rememberDeliberateSwipeState {
        onDelete()
        true
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("TaskListElement$index"),
        backgroundContent = { SwipeDeleteBackground(dismissState) }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = if (isDragging) 6.dp else 0.dp,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            // The whole row toggles (a big target is easier than the checkbox); press and hold to edit.
            Row(
                modifier = Modifier
                    .combinedClickable(
                        onClickLabel = if (checked) "Uncheck" else "Check",
                        onLongClickLabel = "Edit",
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEdit()
                        },
                        onClick = { toggle(!checked) }
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = toggle,
                    modifier = Modifier.testTag("TaskCheckBox")
                )
                Text(
                    task.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                        .testTag("TaskText")
                )
                Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder ${task.message}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
