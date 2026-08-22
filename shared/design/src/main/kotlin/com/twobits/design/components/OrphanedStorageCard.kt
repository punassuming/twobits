package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Surfaces reclaimable on-device model storage that isn't tied to any model the app currently
 * knows about — a leftover archive from a failed extraction, or a fully-downloaded file for a
 * model option that was later removed or renamed. Every model-management screen otherwise only
 * ever lists the *current* model enum, so bytes like these are invisible and unreachable by any
 * other UI; this is the one place a user can actually find and reclaim them. Hidden entirely
 * when [bytes] is zero — most users will never see this card.
 */
@Composable
fun OrphanedStorageCard(
    bytes: Long,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bytes <= 0) return
    var showConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unused model storage",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${bytes.toHumanBytes()} left behind by a removed model option or an interrupted download",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showConfirm = true }) { Text("Clear") }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Clear unused storage?") },
            text = {
                Text(
                    "This deletes ${bytes.toHumanBytes()} of leftover model files that aren't part of any " +
                        "model listed above. Your downloaded and selected models aren't affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onClear() }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private fun Long.toHumanBytes(): String {
    val gb = this / 1_073_741_824.0
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(this / 1_048_576.0)
}
