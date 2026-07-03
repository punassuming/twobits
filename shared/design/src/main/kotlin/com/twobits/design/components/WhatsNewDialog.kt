package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The automatic "what's new in this version" popup. Reuses the same [WhatsNewCategory]/
 * [WhatsNewItem] model the full What's New screen ([WhatsNewScreenLayout]) renders, so bold
 * topic rows and sub-bullets look identical on both surfaces — no separate flattened model.
 */
@Composable
fun AppWhatsNewDialog(
    title: String,
    categories: List<WhatsNewCategory>,
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                categories.forEach { category ->
                    WhatsNewDialogCategorySection(category)
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
        } else {
            null
        },
    )
}

@Composable
private fun WhatsNewDialogCategorySection(category: WhatsNewCategory) {
    if (category.items.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            category.items.forEach { item ->
                WhatsNewDialogItemRow(item)
            }
        }
    }
}

@Composable
private fun WhatsNewDialogItemRow(item: WhatsNewItem) {
    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (item.description.isNotBlank()) {
            WhatsNewDescriptionText(
                description = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
