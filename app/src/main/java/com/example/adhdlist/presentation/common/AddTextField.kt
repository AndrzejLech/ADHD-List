package com.example.adhdlist.presentation.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview

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
@Composable
fun AddTextField(
    modifier: Modifier,
    value: String,
    onTextChanged: (String) -> Unit,
    onAddButtonClick: () -> Unit,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onTextChanged,
        modifier = modifier,
        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
        trailingIcon = {
            IconButton(
                modifier = Modifier.testTag("AddButton"),
                onClick = onAddButtonClick
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
            }
        },
        placeholder = { Text(placeholder) },
    )
}
