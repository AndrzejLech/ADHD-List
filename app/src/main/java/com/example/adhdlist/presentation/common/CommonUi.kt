package com.example.adhdlist.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Destructive actions always ask first, in plain words, with the safe choice on the left. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/** One text field in a dialog; it owns its text, so nothing leaks into the add-task field. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    multiLine: Boolean = false
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = !multiLine,
                maxLines = if (multiLine) 8 else 1,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (multiLine) ImeAction.Default else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(value.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("DialogTextField")
            )
            // Inside the dialog's own composition: the field must be attached before it can take focus.
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = canConfirm) { Text(confirmLabel) }
        }
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {}
    )
}

// Fraction of the row a swipe must cover before it deletes. Material's default is a fixed 56 dp,
// which an accidental brush can reach; this has to be a deliberate, long swipe.
private const val SWIPE_TO_DELETE_FRACTION = 0.6f

/**
 * Swipe state that only fires [onSwiped] after a long swipe. Material also dismisses on a quick
 * flick of any length, so the distance is checked again here. Return true from [onSwiped] to let
 * the row leave, false to spring it back (e.g. to ask first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDeliberateSwipeState(onSwiped: () -> Boolean): SwipeToDismissBoxState {
    val minDistance = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp - 32.dp).toPx() * SWIPE_TO_DELETE_FRACTION
    }
    lateinit var state: SwipeToDismissBoxState
    state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.Settled || (abs(state.requireOffset()) >= minDistance && onSwiped())
        },
        positionalThreshold = { total -> total * SWIPE_TO_DELETE_FRACTION }
    )
    return state
}

/**
 * What shows under a row while it is swiped: a soft hint at first, turning to the delete colour
 * only once the swipe is far enough to count, so it is clear when letting go will delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val armed = state.targetValue != SwipeToDismissBoxValue.Settled
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(if (armed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp),
        contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = if (armed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
