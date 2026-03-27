package dev.scrybe.feature.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordRow(
    item: HistorySessionItem,
    selectionEnabled: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onTransform: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: () -> Unit,
    onRetryTranscription: () -> Unit,
    onResetTranscriptionState: () -> Unit,
) {
    var menuExpanded by remember(item.session.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.25f },
        confirmValueChange = { value ->
            if (selectionEnabled) return@rememberSwipeToDismissBoxState false
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onTransform()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (item.session.isArchived) onRestore() else onArchive()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    val rowContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionEnabled) onToggleSelection() else onOpen()
                    },
                    onLongClick = {
                        if (selectionEnabled) onToggleSelection() else onLongPress()
                    },
                ),
            color = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                recordContainerColor(item.session.status, item.session.isArchived)
            },
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WaveformBackdrop(
                    samples = item.session.waveformSamples,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectionEnabled) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelection() },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 2.dp),
                        tint = statusColor(item.session.status, item.session.isArchived),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.session.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.session.createdAt.atZone(ZoneId.systemDefault()).format(HISTORY_TIME_FORMATTER),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = buildMetaLine(item.session),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = statusLabel(item.session.status, item.session.isArchived),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor(item.session.status, item.session.isArchived),
                        )
                        item.transcriptPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!selectionEnabled) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Record actions")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Open") },
                                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpen()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open With") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenWith()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Copy") },
                                    leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onSaveCopy()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Run Default Transform") },
                                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onTransform()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (item.session.isArchived) "Restore" else "Archive") },
                                    leadingIcon = {
                                        Icon(
                                            if (item.session.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (item.session.isArchived) onRestore() else onArchive()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRename()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Information") },
                                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onInfo()
                                    },
                                )
                                if (item.session.status == SessionStatus.FAILED) {
                                    DropdownMenuItem(
                                        text = { Text("Retry Transcription") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onRetryTranscription()
                                        },
                                    )
                                }
                                if (item.session.status == SessionStatus.TRANSCRIBING) {
                                    DropdownMenuItem(
                                        text = { Text("Clear Stuck State") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onResetTranscriptionState()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectionEnabled) {
        rowContent()
    } else {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                SwipeBackground(
                    isArchived = item.session.isArchived,
                    dismissDirection = dismissState.dismissDirection,
                )
            },
        ) {
            rowContent()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeBackground(
    isArchived: Boolean,
    dismissDirection: SwipeToDismissBoxValue?,
) {
    val color = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.tertiaryContainer
        null, SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val label = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> "Run transform"
        SwipeToDismissBoxValue.EndToStart -> if (isArchived) "Restore" else "Archive"
        null, SwipeToDismissBoxValue.Settled -> ""
    }
    val icon = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.AutoFixHigh
        SwipeToDismissBoxValue.EndToStart -> if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive
        null, SwipeToDismissBoxValue.Settled -> null
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon?.let { Icon(it, contentDescription = null) }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    icon?.let { Icon(it, contentDescription = null) }
                }
            }
        }
    }
}

private fun statusLabel(status: SessionStatus, isArchived: Boolean): String = when {
    isArchived -> "Archived"
    status == SessionStatus.TRANSCRIBING -> "Transcribing"
    status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED -> "Transcribed"
    status == SessionStatus.FAILED -> "Failed"
    else -> "Recorded"
}

@Composable
private fun statusColor(status: SessionStatus, isArchived: Boolean) = when {
    isArchived -> MaterialTheme.colorScheme.tertiary
    status == SessionStatus.TRANSCRIBING -> MaterialTheme.colorScheme.secondary
    status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED -> MaterialTheme.colorScheme.primary
    status == SessionStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun recordContainerColor(status: SessionStatus, isArchived: Boolean): Color = when {
    isArchived -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    status == SessionStatus.TRANSCRIBING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED ->
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    status == SessionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
internal fun WaveformBackdrop(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    val bars = normalizeBackdropSamples(samples, targetCount = 84)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    Canvas(modifier = modifier) {
        val baselineY = size.height * 0.76f
        drawLine(
            color = baselineColor,
            start = androidx.compose.ui.geometry.Offset(0f, baselineY),
            end = androidx.compose.ui.geometry.Offset(size.width, baselineY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val barWidth = (size.width / (bars.size * 2.8f)).coerceAtLeast(1.dp.toPx())
        val spacing = (size.width - (barWidth * bars.size)) / bars.size.coerceAtLeast(1)
        bars.forEachIndexed { index, sample ->
            val shapedAmplitude = subtleWaveformAmplitude(sample)
            val lineHeight = (size.height * 0.28f) * shapedAmplitude
            val x = (index * (barWidth + spacing)) + (barWidth / 2f)
            val startY = baselineY - lineHeight
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, startY),
                end = androidx.compose.ui.geometry.Offset(x, baselineY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun subtleWaveformAmplitude(sample: Float): Float {
    val gated = if (sample < 0.025f) 0f else sample
    return (0.002f + (gated * 0.55f)).coerceIn(0.002f, 0.6f)
}

private fun normalizeBackdropSamples(samples: List<Float>, targetCount: Int): List<Float> {
    if (targetCount <= 0) return emptyList()
    if (samples.isEmpty()) return List(targetCount) { 0f }
    if (samples.size == 1) return List(targetCount) { samples.first() }

    val normalized = MutableList(targetCount) { 0f }
    samples.forEachIndexed { index, sample ->
        val bucket = ((index.toFloat() / samples.lastIndex.coerceAtLeast(1)) * (targetCount - 1))
            .roundToInt()
            .coerceIn(0, targetCount - 1)
        normalized[bucket] = maxOf(normalized[bucket], sample)
    }
    return normalized
}

@Composable
internal fun RecordsFilterDialog(
    current: RecordsFilterState,
    onDismiss: () -> Unit,
    onApply: (RecordsFilterState) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sort", style = MaterialTheme.typography.labelLarge)
                RecordsSortOption.entries.forEach { option ->
                    FilterOptionRow(
                        title = option.name.lowercase().replaceFirstChar(Char::titlecase),
                        selected = draft.sortOption == option,
                        onClick = { draft = draft.copy(sortOption = option) },
                    )
                }
                HorizontalDivider()
                Text("Date Range", style = MaterialTheme.typography.labelLarge)
                listOf(
                    RecordsDateRange.ALL to "All time",
                    RecordsDateRange.TODAY to "Today",
                    RecordsDateRange.LAST_7_DAYS to "Last 7 days",
                    RecordsDateRange.LAST_30_DAYS to "Last 30 days",
                ).forEach { (range, label) ->
                    FilterOptionRow(
                        title = label,
                        selected = draft.dateRange == range,
                        onClick = { draft = draft.copy(dateRange = range) },
                    )
                }
                HorizontalDivider()
                Text("Status", style = MaterialTheme.typography.labelLarge)
                SessionStatus.entries
                    .filter { it in setOf(SessionStatus.RECORDED, SessionStatus.TRANSCRIBING, SessionStatus.TRANSCRIBED, SessionStatus.EDITED, SessionStatus.FAILED) }
                    .forEach { status ->
                        StatusToggleRow(
                            status = status,
                            checked = status in draft.includedStatuses,
                            onToggle = {
                                draft = draft.copy(
                                    includedStatuses = draft.includedStatuses.toMutableSet().apply {
                                        if (contains(status)) remove(status) else add(status)
                                    }
                                )
                            },
                        )
                    }
                HorizontalDivider()
                Text("Archive", style = MaterialTheme.typography.labelLarge)
                FilterOptionRow(
                    title = "Active records",
                    selected = !draft.showArchived,
                    onClick = { draft = draft.copy(showArchived = false) },
                )
                FilterOptionRow(
                    title = "Archived records",
                    selected = draft.showArchived,
                    onClick = { draft = draft.copy(showArchived = true) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (selected) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusToggleRow(
    status: SessionStatus,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text = status.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase))
    }
}

@Composable
internal fun RecordInfoDialog(
    info: RecordInfo,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine("Recorded", info.createdAt.atZone(ZoneId.systemDefault()).format(HISTORY_TIME_FORMATTER))
                InfoLine("Duration", formatDuration(info.durationMs))
                InfoLine("File Size", formatFileSize(info.fileSizeBytes))
                InfoLine("Type", info.audioFormat)
                InfoLine("Quality", "${info.sampleRateHz / 1000} kHz · ${info.encodingBitRate / 1000} kbps · ${if (info.channelCount == 1) "Mono" else "Stereo"}")
                InfoLine("Path", info.filePath)
                info.transcriptPreview?.takeIf { it.isNotBlank() }?.let {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Record") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal fun HistorySessionItem.toRecordInfo(): RecordInfo =
    RecordInfo(
        title = session.title,
        createdAt = session.createdAt,
        durationMs = session.durationMs,
        fileSizeBytes = session.fileSizeBytes,
        audioFormat = session.audioFormat.name,
        sampleRateHz = session.sampleRateHz,
        encodingBitRate = session.encodingBitRate,
        channelCount = session.channelCount,
        filePath = session.audioFilePath,
        transcriptPreview = transcriptPreview,
    )

internal fun buildMetaLine(session: RecordingSession): String =
    listOf(
        formatDuration(session.durationMs),
        formatFileSize(session.fileSizeBytes),
        session.audioFormat.name,
        "${session.sampleRateHz / 1000} kHz",
        "${session.encodingBitRate / 1000} kbps",
        if (session.channelCount == 1) "Mono" else "Stereo",
    ).joinToString(" · ")

internal fun buildFilterSummary(filters: RecordsFilterState): String {
    val statusSummary = if (filters.includedStatuses.isEmpty()) {
        "All statuses"
    } else {
        filters.includedStatuses.joinToString { it.name.lowercase().replace('_', ' ') }
    }
    val archiveSummary = if (filters.showArchived) "Archived only" else "Active only"
    val dateSummary = when (filters.dateRange) {
        RecordsDateRange.ALL -> "All time"
        RecordsDateRange.TODAY -> "Today"
        RecordsDateRange.LAST_7_DAYS -> "Last 7 days"
        RecordsDateRange.LAST_30_DAYS -> "Last 30 days"
    }
    val sortSummary = when (filters.sortOption) {
        RecordsSortOption.NEWEST -> "Newest first"
        RecordsSortOption.OLDEST -> "Oldest first"
        RecordsSortOption.LONGEST -> "Longest first"
        RecordsSortOption.LARGEST -> "Largest first"
    }
    return "$dateSummary · $sortSummary · $statusSummary · $archiveSummary"
}

internal fun openAudioWith(context: Context, session: RecordingSession) {
    val file = File(session.audioFilePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = when (file.extension.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        else -> "audio/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open audio with"))
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        bytes >= mib -> String.format("%.1f MB", bytes / mib)
        bytes >= kib -> String.format("%.1f KB", bytes / kib)
        else -> "$bytes B"
    }
}

internal val HISTORY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
