package com.example.adhdlist.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun AddTextFieldPreview(){
    AddTextField(
        modifier = Modifier,
        value = "",
        onTextChanged = {},
        onAddButtonClick = { },
        placeholder = "Type in here"
    )
}

// Controlled by `value`: the ViewModel's text is the only copy, so what is shown is what gets submitted.
// Always pinned to the bottom, above the keyboard, so adding works the same way on every screen.
@Composable
fun AddTextField(
    modifier: Modifier,
    value: String,
    onTextChanged: (String) -> Unit,
    onAddButtonClick: () -> Unit,
    placeholder: String,
    // Items can span lines (e.g. exercise sets): Enter starts a new line and + adds.
    multiLine: Boolean = false
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filled grey field as in the first version; no outline, so nothing yellow-on-white to squint at.
            TextField(
                value = value,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f).then(modifier),
                singleLine = !multiLine,
                maxLines = if (multiLine) 5 else 1,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (multiLine) ImeAction.Default else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onAddButtonClick() }),
                placeholder = { Text(placeholder) },
            )
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                modifier = Modifier.size(56.dp).testTag("AddButton"),
                shape = MaterialTheme.shapes.medium,
                onClick = onAddButtonClick
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}
