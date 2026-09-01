package com.twobits.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

sealed class LocalModelStatus {
    data object NotAvailable : LocalModelStatus()

    data class InProgress(
        val progressPercent: Int,
    ) : LocalModelStatus()

    data object Ready : LocalModelStatus()

    data class Error(
        val message: String,
    ) : LocalModelStatus()
}

@Composable
fun <T : Any> LocalModelPanel(
    sectionLabel: String,
    models: List<T>,
    status: (T) -> LocalModelStatus,
    selected: T?,
    onSelect: (T) -> Unit,
    onPrimaryAction: (T) -> Unit,
    primaryActionLabel: String,
    primaryActionIcon: ImageVector,
    onDelete: (T) -> Unit,
    onImport: ((T) -> Unit)? = null,
    name: (T) -> String,
    sizeLabel: (T) -> String,
    description: (T) -> String,
    modifier: Modifier = Modifier,
    sectionSubtitle: String? = null,
    progressLabel: String = "Loading",
    huggingFaceUrl: ((T) -> String)? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = sectionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = 14.dp,
                        top = 10.dp,
                        bottom = if (sectionSubtitle != null) 0.dp else 2.dp,
                    ),
            )
            sectionSubtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
                )
            }
            models.forEachIndexed { index, model ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                LocalModelRow(
                    modelName = name(model),
                    modelSize = sizeLabel(model),
                    modelDescription = description(model),
                    modelStatus = status(model),
                    isSelected = model == selected,
                    huggingFaceUrl = huggingFaceUrl?.invoke(model),
                    primaryActionLabel = primaryActionLabel,
                    primaryActionIcon = primaryActionIcon,
                    progressLabel = progressLabel,
                    onSelect = { onSelect(model) },
                    onPrimaryAction = { onPrimaryAction(model) },
                    onDelete = { onDelete(model) },
                    onImport = onImport?.let { action -> { action(model) } },
                )
            }
        }
    }
}

@Composable
private fun LocalModelRow(
    modelName: String,
    modelSize: String,
    modelDescription: String,
    modelStatus: LocalModelStatus,
    isSelected: Boolean,
    huggingFaceUrl: String?,
    primaryActionLabel: String,
    primaryActionIcon: ImageVector,
    progressLabel: String,
    onSelect: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDelete: () -> Unit,
    onImport: (() -> Unit)?,
) {
    val isReady = modelStatus is LocalModelStatus.Ready
    val isSelectedAndReady = isSelected && isReady

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (isSelectedAndReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val leadingIcon =
                when {
                    isSelectedAndReady -> Icons.Filled.RadioButtonChecked
                    isReady -> Icons.Filled.RadioButtonUnchecked
                    else -> primaryActionIcon
                }
            val leadingTint =
                when {
                    isSelectedAndReady -> MaterialTheme.colorScheme.primary
                    isReady -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingTint,
                modifier = Modifier.size(20.dp).padding(top = 1.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // modelSize must be measured at its natural width first (weight(fill = false)
                    // on modelName achieves this) — otherwise a long model name can claim the
                    // row's entire width on its own line, leaving modelSize almost no room and
                    // forcing it to wrap one character per line.
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color =
                            if (isSelectedAndReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = modelSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = modelDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (modelStatus is LocalModelStatus.NotAvailable && huggingFaceUrl != null) {
                    Text(
                        text = huggingFaceUrl,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
                if (modelStatus is LocalModelStatus.InProgress) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { modelStatus.progressPercent / 100f },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "$progressLabel… ${modelStatus.progressPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (modelStatus is LocalModelStatus.Error) {
                    Text(
                        text = modelStatus.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    modelStatus is LocalModelStatus.NotAvailable ->
                        {
                            if (onImport != null) {
                                TextButton(
                                    onClick = onImport,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text("Import", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            AssistChip(
                                onClick = onPrimaryAction,
                                label = {
                                    Text(primaryActionLabel, style = MaterialTheme.typography.labelSmall)
                                },
                                leadingIcon = {
                                    Icon(
                                        primaryActionIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    isReady && !isSelected -> {
                        TextButton(
                            onClick = onSelect,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Use", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    isReady && isSelected ->
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    modelStatus is LocalModelStatus.Error -> {
                        TextButton(onClick = onPrimaryAction) {
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                        // A failed download may have left a resumable partial file behind (by
                        // design — Retry picks it back up) — Discard is how to abandon it and
                        // reclaim that space instead of waiting on it to age out on its own.
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Discard",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
