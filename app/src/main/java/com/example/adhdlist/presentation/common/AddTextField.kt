package com.example.adhdlist.presentation.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
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

@Composable
fun AddTextField(
    modifier: Modifier,
    value: String,
    onTextChanged: (String) -> Unit,
    onAddButtonClick: () -> Unit,
    placeholder: String
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }


    TextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onTextChanged(it.text)
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
        trailingIcon = {
            IconButton(onClick = {
                onAddButtonClick()
                textFieldValue = TextFieldValue("")
            }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
            }
        },
        placeholder = { Text(placeholder) }
    )
}