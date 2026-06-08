package com.twobits.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppWhatsNewDialog(
    title: String,
    notes: List<String>,
    confirmLabel: String = "Close",
    onDismiss: () -> Unit,
    onViewHistory: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                notes.forEach { note ->
                    Text(
                        text = "• $note",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        },
        dismissButton = if (onViewHistory != null) {
            {
                TextButton(onClick = {
                    onDismiss()
                    onViewHistory()
                }) {
                    Text("View history")
                }
            }
        } else null,
    )
}
