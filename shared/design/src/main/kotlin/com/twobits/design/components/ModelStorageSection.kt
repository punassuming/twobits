package com.twobits.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Every model-management screen only ever lists the *current* model enum (Download/Delete rows),
 * never the directory's actual contents — so there was no way to see exact file sizes, the
 * on-disk path, or (most importantly) individual files a since-removed/renamed model option or a
 * failed download left behind. This is that view: every installed model plus every orphaned
 * entry, by name and exact byte size, with the storage path and a one-tap way to reclaim
 * whatever's unused. Hidden entirely when there's nothing downloaded yet at all.
 *
 * Files here still aren't reachable by an external file manager or PC — Android scopes
 * `Android/data/<package>` to the owning app on modern versions — this is the closest either
 * comes to inspectable, and it's a real listing, not a guess.
 */
@Composable
fun ModelStorageSection(
    storageDirPath: String,
    installed: List<Pair<String, Long>>,
    orphaned: List<Pair<String, Long>>,
    onClearOrphaned: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFileManager: (() -> Unit)? = null,
) {
    val items =
        remember(installed, orphaned) {
            (
                installed.map { ModelStorageItem(it.first, it.second, isOrphaned = false) } +
                    orphaned.map { ModelStorageItem(it.first, it.second, isOrphaned = true) }
            ).sortedByDescending { it.sizeBytes }
        }
    if (items.isEmpty()) return

    val totalBytes = items.sumOf { it.sizeBytes }
    val orphanedBytes = orphaned.sumOf { it.second }
    // Expanded by default — this is now the answer to "what's actually installed," positioned
    // right below the top-level Import action, not a secondary detail worth hiding behind a tap.
    var expanded by remember { mutableStateOf(true) }
    var showConfirmClear by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Storage — ${totalBytes.toHumanBytes()} across ${items.size} item(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (onOpenFileManager != null) {
                        Text(
                            text = "Open in File Manager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenFileManager),
                        )
                    } else {
                        Text(
                            text = storageDirPath,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide files" else "Show files") }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.isOrphaned) {
                                Text(
                                    text = "unused",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            text = item.sizeBytes.toHumanBytes(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (orphaned.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showConfirmClear = true }) {
                        Text("Clear ${orphanedBytes.toHumanBytes()} unused")
                    }
                }
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear unused storage?") },
            text = {
                Text(
                    "This deletes ${orphanedBytes.toHumanBytes()} of leftover model files that aren't part of any " +
                        "model listed above. Your downloaded and selected models aren't affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmClear = false
                    onClearOrphaned()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

private data class ModelStorageItem(
    val name: String,
    val sizeBytes: Long,
    val isOrphaned: Boolean,
)

private fun Long.toHumanBytes(): String {
    val gb = this / 1_073_741_824.0
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(this / 1_048_576.0)
}
