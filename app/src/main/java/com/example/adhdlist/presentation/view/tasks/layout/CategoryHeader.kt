package com.example.adhdlist.presentation.view.tasks.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adhdlist.data.model.Category
import com.example.adhdlist.presentation.common.SwipeDeleteBackground
import com.example.adhdlist.presentation.common.rememberDeliberateSwipeState
import com.example.adhdlist.presentation.view.lists.layout.progressText

/** Tap opens or closes the category; press and hold edits it; + makes it where new items go. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryHeader(
    category: Category,
    ticked: Int,
    total: Int,
    isAddTarget: Boolean,
    onToggle: () -> Unit,
    onAddHere: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Swiping only asks, like a list card: the header springs back and the delete dialog opens.
    val dismissState = rememberDeliberateSwipeState {
        onDeleteRequest()
        false
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.padding(top = 8.dp),
        backgroundContent = { SwipeDeleteBackground(dismissState) }
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.background)
            // The category new items go into is marked, so it's clear where + in the field adds.
            .background(if (isAddTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .combinedClickable(
                onClickLabel = if (category.expanded) "Close" else "Open",
                onLongClickLabel = "Edit",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
                onClick = onToggle
            )
            .semantics { stateDescription = if (category.expanded) "Open" else "Closed" }
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag("CategoryHeader"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (category.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                progressText(ticked, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAddHere) {
            Icon(Icons.Outlined.Add, contentDescription = "Add to ${category.name}")
        }
    }
    }
}

/** "Where does this item go?" in the edit dialog: one chip per category, plus "No category". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChooser(categories: List<Category>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("No category") })
        categories.forEach { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}
