package com.twobits.design.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

/**
 * The standard search field: leading search icon, a labelled clear button while there's text,
 * single line, and the keyboard's Search action (which calls [onSearch] when set and hides the
 * keyboard). Use for both live filtering and submit-to-search.
 */
@Composable
fun TwoBitsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: ((String) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearch?.invoke(query)
                    focusManager.clearFocus()
                },
            ),
    )
}
