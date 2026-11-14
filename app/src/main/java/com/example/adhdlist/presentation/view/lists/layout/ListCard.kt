package com.example.adhdlist.presentation.view.lists.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adhdlist.data.model.ListWithProgress
import com.example.adhdlist.presentation.common.ProgressBar
import com.example.adhdlist.presentation.common.SwipeDeleteBackground
import com.example.adhdlist.presentation.common.rememberDeliberateSwipeState

fun progressText(done: Int, total: Int) = when {
    total == 0 -> "Nothing here yet"
    done == total -> "All $total ticked"
    else -> "$done of $total ticked"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListCard(
    index: Int,
    item: ListWithProgress,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    onRename: () -> Unit,
    handleModifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // Swiping only asks; the card springs back and a dialog confirms, because a list holds many items.
    val dismissState = rememberDeliberateSwipeState {
        onDeleteRequest()
        false
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("TaskListListElement$index"),
        backgroundContent = { SwipeDeleteBackground(dismissState) }
    ) {
        // Tap opens the list; press and hold renames it (the same "hold to edit" as items).
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClickLabel = "Open",
                    onLongClickLabel = "Rename",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRename()
                    },
                    onClick = onClick
                ),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.list.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.list.remoteId != null) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Outlined.Groups,
                                contentDescription = "Shared",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progressText(item.done, item.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.total > 0) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(item.done, item.total)
                    }
                }
                Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder ${item.list.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
