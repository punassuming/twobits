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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One entry in the update dialog: a short bold title with an optional plain
 * description beneath it. Built from the parsed changelog's structured items.
 */
data class WhatsNewDialogEntry(
    val title: String,
    val description: String = "",
)

@Composable
fun AppWhatsNewDialog(
    title: String,
    entries: List<WhatsNewDialogEntry>,
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
                entries.forEach { entry ->
                    WhatsNewDialogEntryRow(entry)
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

@Composable
private fun WhatsNewDialogEntryRow(entry: WhatsNewDialogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (entry.description.isNotBlank()) {
            Text(
                text = entry.description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
