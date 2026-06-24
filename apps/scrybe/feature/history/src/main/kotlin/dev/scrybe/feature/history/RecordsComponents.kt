package dev.scrybe.feature.history

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.scrybe.core.model.Folder
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TransformProfile
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val RECORD_WAVEFORM_TARGET_BAR_COUNT = 120
private val RECORD_WAVEFORM_MAX_BAR_WIDTH = 2.dp

private fun modeAccentColor(
    mode: RecordingMode,
    colors: ColorScheme,
): Color =
    when (mode) {
        RecordingMode.MEETING -> colors.primary
        RecordingMode.IDEA -> colors.tertiary
        RecordingMode.TASKS -> colors.secondary
        RecordingMode.CONVERSATION -> colors.primary
        RecordingMode.STORY -> colors.tertiary
        RecordingMode.INTERVIEW -> colors.secondary
        RecordingMode.JOURNAL -> colors.onSurfaceVariant
        RecordingMode.CUSTOM -> colors.secondary
    }

private fun historyModeIcon(mode: RecordingMode): ImageVector =
    when (mode) {
        RecordingMode.MEETING -> Icons.Filled.Groups
        RecordingMode.IDEA -> Icons.Filled.Lightbulb
        RecordingMode.TASKS -> Icons.Filled.TaskAlt
        RecordingMode.CONVERSATION -> Icons.Filled.Forum
        RecordingMode.STORY -> Icons.Filled.MenuBook
        RecordingMode.INTERVIEW -> Icons.Filled.PersonSearch
        RecordingMode.JOURNAL -> Icons.Filled.Book
        RecordingMode.CUSTOM -> Icons.Filled.Label
    }

@Composable
private fun HistoryModeBadge(
    mode: RecordingMode,
    customTypeName: String? = null,
) {
    val accentColor = modeAccentColor(mode, MaterialTheme.colorScheme)
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(historyModeIcon(mode), contentDescription = null, modifier = Modifier.size(11.dp), tint = accentColor)
            Text(customTypeName ?: mode.label, style = MaterialTheme.typography.labelSmall, color = accentColor)
        }
    }
}

@Composable
private fun HistoryMiniWaveform(
    samples: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val barCount = 22
    val normalized =
        if (samples.isEmpty()) {
            List(barCount) { 0.3f }
        } else {
            val step = samples.size.toFloat() / barCount
            List(barCount) { i -> samples[(i * step).toInt().coerceAtMost(samples.size - 1)] }
        }
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        normalized.forEach { amp ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(amp.coerceIn(0.1f, 1f))
                        .background(accentColor.copy(alpha = 0.55f), RoundedCornerShape(1.dp)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRowFooter(item: HistorySessionItem) {
    val hasSpeakers = item.speakerCount > 1
    val hasTasks = item.openTaskCount > 0
    val hasTags = item.session.tags.isNotEmpty()
    if (!hasSpeakers && !hasTasks && !hasTags) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasSpeakers) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.speakerCount} speakers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasTasks) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text("${item.openTaskCount} task${if (item.openTaskCount == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item.session.tags.take(3).forEach { tag ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("#$tag", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecordRowContent(
    item: HistorySessionItem,
    folderName: String? = null,
) {
    val accentColor = modeAccentColor(item.session.mode, MaterialTheme.colorScheme)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryModeBadge(mode = item.session.mode, customTypeName = item.customTypeName)
                item.session.locationLabel?.let { loc ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(loc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (folderName != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(folderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                item.session.createdAt
                    .atZone(ZoneId.systemDefault())
                    .format(HISTORY_TIME_FORMATTER),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.session.durationMs > 0L) {
                Text(formatDuration(item.session.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (item.session.waveformSamples.isNotEmpty()) {
        HistoryMiniWaveform(samples = item.session.waveformSamples, accentColor = accentColor, modifier = Modifier.fillMaxWidth())
    }
    RecordRowFooter(item = item)
    item.transcriptPreview?.takeIf { it.isNotBlank() }?.let { preview ->
        Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onManageTags: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: () -> Unit,
    onShareTranscript: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRetryTranscription: () -> Unit,
    onResetTranscriptionState: () -> Unit,
    showRecordingInfo: Boolean,
    onTagClick: ((String) -> Unit)? = null,
    folderName: String? = null,
) {
    var menuExpanded by remember(item.session.id) { mutableStateOf(false) }

    Card(
        modifier =
            Modifier.fillMaxWidth().combinedClickable(
                onClick = { if (selectionEnabled) onToggleSelection() else onOpen() },
                onLongClick = { if (selectionEnabled) onToggleSelection() else onLongPress() },
            ),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordRowContent(item = item, folderName = folderName)
            }
            if (!selectionEnabled) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Record actions")
                    }
                    RecordDropdownMenu(
                        item = item,
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onOpen = onOpen,
                        onOpenWith = onOpenWith,
                        onRetryTranscription = onRetryTranscription,
                        onTransform = onTransform,
                        onShareTranscript = onShareTranscript,
                        onSaveCopy = onSaveCopy,
                        onRename = onRename,
                        onManageTags = onManageTags,
                        onMoveToFolder = onMoveToFolder,
                        onArchive = onArchive,
                        onRestore = onRestore,
                        onDelete = onDelete,
                        onInfo = onInfo,
                        onResetTranscriptionState = onResetTranscriptionState,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordDropdownMenu(
    item: HistorySessionItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onRetryTranscription: () -> Unit,
    onTransform: () -> Unit,
    onShareTranscript: () -> Unit,
    onSaveCopy: () -> Unit,
    onRename: () -> Unit,
    onManageTags: () -> Unit,
    onMoveToFolder: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onResetTranscriptionState: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Filled.History, null) }, onClick = {
            onDismiss()
            onOpen()
        })
        DropdownMenuItem(text = { Text("Open With") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = {
            onDismiss()
            onOpenWith()
        })
        val hasAiItems = item.session.status == SessionStatus.RECORDED || item.transcriptPreview != null
        if (hasAiItems) {
            DropdownSectionHeader("AI")
            if (item.session.status == SessionStatus.RECORDED) {
                DropdownMenuItem(text = { Text("Transcribe") }, leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, null) }, onClick = {
                    onDismiss()
                    onRetryTranscription()
                })
            }
            if (item.transcriptPreview != null) {
                DropdownMenuItem(text = { Text("Transform…") }, leadingIcon = { Icon(Icons.Filled.AutoFixHigh, null) }, onClick = {
                    onDismiss()
                    onTransform()
                })
            }
        }
        DropdownSectionHeader("Export")
        if (item.transcriptPreview != null) {
            DropdownMenuItem(text = { Text("Share Transcript") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = {
                onDismiss()
                onShareTranscript()
            })
        }
        DropdownMenuItem(text = { Text("Save Copy") }, leadingIcon = { Icon(Icons.Filled.SaveAlt, null) }, onClick = {
            onDismiss()
            onSaveCopy()
        })
        DropdownSectionHeader("Manage")
        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
            onDismiss()
            onRename()
        })
        DropdownMenuItem(text = { Text("Manage Tags") }, leadingIcon = { Icon(Icons.Filled.Label, null) }, onClick = {
            onDismiss()
            onManageTags()
        })
        DropdownMenuItem(text = { Text("Move to Folder") }, leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) }, onClick = {
            onDismiss()
            onMoveToFolder()
        })
        DropdownMenuItem(
            text = { Text(if (item.session.isArchived) "Restore" else "Archive") },
            leadingIcon = { Icon(if (item.session.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, null) },
            onClick = {
                onDismiss()
                if (item.session.isArchived) onRestore() else onArchive()
            },
        )
        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
            onDismiss()
            onDelete()
        })
        DropdownSectionHeader("Info")
        DropdownMenuItem(text = { Text("Information") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = {
            onDismiss()
            onInfo()
        })
        val hasStatusItems = item.session.status == SessionStatus.FAILED || item.session.status == SessionStatus.TRANSCRIBING
        if (hasStatusItems) {
            DropdownSectionHeader("Status")
            if (item.session.status == SessionStatus.FAILED) {
                DropdownMenuItem(text = { Text("Retry Transcription") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = {
                    onDismiss()
                    onRetryTranscription()
                })
            }
            if (item.session.status == SessionStatus.TRANSCRIBING) {
                DropdownMenuItem(text = { Text("Clear Stuck State") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = {
                    onDismiss()
                    onResetTranscriptionState()
                })
            }
        }
    }
}

@Composable
internal fun HistoryModeFilterRow(
    selected: RecordingMode?,
    onSelect: (RecordingMode?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        RecordingMode.entries.forEach { mode ->
            val accentColor = modeAccentColor(mode, MaterialTheme.colorScheme)
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(if (selected == mode) null else mode) },
                label = { Text(mode.label) },
                leadingIcon = { Icon(historyModeIcon(mode), contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor,
                        selectedLeadingIconColor = accentColor,
                    ),
            )
        }
    }
}

@Composable
private fun FolderSheetItem(
    node: FolderNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(start = (16 + node.depth * 20).dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (isSelected) Icons.Filled.FolderOpen else Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderNavigationSheet(
    folderTree: List<FolderNode>,
    currentFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        scrimColor = Color.Black.copy(alpha = 0.62f),
    ) {
        Text("Browse folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                FolderSheetItem(
                    node = FolderNode(id = "", name = "All recordings", sessionCount = 0, depth = 0),
                    isSelected = currentFolderId == null,
                    onSelect = {
                        onSelectFolder(null)
                        onDismiss()
                    },
                )
            }
            items(folderTree, key = { it.id }) { node ->
                FolderSheetItem(
                    node = node,
                    isSelected = node.id == currentFolderId,
                    onSelect = {
                        onSelectFolder(node.id)
                        onDismiss()
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val SWIPE_BUTTON_WIDTH = 80.dp

@Composable
internal fun SwipeRevealRow(
    isArchived: Boolean,
    enabled: Boolean,
    onArchiveOrRestore: () -> Unit,
    onOpenTransformPicker: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val buttonWidthPx = with(density) { SWIPE_BUTTON_WIDTH.toPx() }
    val threshold = buttonWidthPx * 0.35f
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(enabled) {
        if (!enabled) offsetX.animateTo(0f)
    }

    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
        Box(
            modifier =
                Modifier.matchParentSize().graphicsLayer {
                    alpha = (offsetX.value / buttonWidthPx).coerceIn(0f, 1f)
                },
        ) {
            SwipeActionButton(
                icon = Icons.Filled.AutoFixHigh,
                label = "Transform",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onOpenTransformPicker()
                },
                modifier = Modifier.align(Alignment.CenterStart).width(SWIPE_BUTTON_WIDTH + 20.dp).fillMaxHeight(),
            )
        }
        Box(
            modifier =
                Modifier.matchParentSize().graphicsLayer {
                    alpha = (-offsetX.value / buttonWidthPx).coerceIn(0f, 1f)
                },
        ) {
            SwipeActionButton(
                icon = if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                label = if (isArchived) "Restore" else "Archive",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onArchiveOrRestore()
                },
                modifier = Modifier.align(Alignment.CenterEnd).width(SWIPE_BUTTON_WIDTH + 20.dp).fillMaxHeight(),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(enabled, buttonWidthPx) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    val target =
                                        when {
                                            offsetX.value < -threshold -> -buttonWidthPx
                                            offsetX.value > threshold -> buttonWidthPx
                                            else -> 0f
                                        }
                                    offsetX.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                }
                            },
                            onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-buttonWidthPx, buttonWidthPx)
                                scope.launch { offsetX.snapTo(newOffset) }
                            },
                        )
                    },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    shape: Shape = MaterialTheme.shapes.large,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(containerColor, shape = shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransformPickerSheet(
    dialogState: TransformDialogState,
    profiles: List<TransformProfile>,
    onRunProfile: (TransformProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title =
        when {
            dialogState.sessionTitles.size == 1 -> "Transform “${dialogState.sessionTitles.first()}”"
            dialogState.sessionTitles.size > 1 -> "Transform ${dialogState.sessionIds.size} recordings"
            else -> "Transform"
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            if (dialogState.runningProfileId != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            if (dialogState.result != null) {
                TransformSheetResult(result = dialogState.result, onDismiss = onDismiss)
            } else {
                if (profiles.isEmpty()) {
                    Text(
                        "No transform profiles found. Create one in Profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    profiles.forEach { profile ->
                        TransformProfileRow(
                            profile = profile,
                            isRunning = dialogState.runningProfileId == profile.id,
                            anyRunning = dialogState.runningProfileId != null,
                            onRun = { onRunProfile(profile) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformProfileRow(
    profile: TransformProfile,
    isRunning: Boolean,
    anyRunning: Boolean,
    onRun: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
            if (profile.description.isNotBlank()) {
                Text(
                    profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalButton(onClick = onRun, enabled = !anyRunning) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Run")
            }
        }
    }
}

@Composable
private fun TransformSheetResult(
    result: TransformDialogResult,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${result.profileName} result",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                result.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp).heightIn(max = 200.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(result.text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Copy")
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagBrowserSheet(
    availableTags: List<Pair<String, Int>>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Browse by tag", style = MaterialTheme.typography.titleMedium)
                if (selectedTag != null) {
                    TextButton(onClick = {
                        onSelectTag(null)
                        onDismiss()
                    }) { Text("Clear filter") }
                }
            }
            if (availableTags.isEmpty()) {
                Text(
                    "No tags found. Add tags to your recordings in Session Detail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                availableTags.forEach { (tag, count) ->
                    TagBrowserRow(
                        tag = tag,
                        count = count,
                        selected = tag == selectedTag,
                        onSelect = {
                            onSelectTag(tag)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagBrowserRow(
    tag: String,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun recordContainerColor(
    status: SessionStatus,
    isArchived: Boolean,
): Color =
    when {
        isArchived -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        status == SessionStatus.TRANSCRIBING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        status == SessionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

@Composable
private fun DropdownSectionHeader(title: String) {
    HorizontalDivider()
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
internal fun WaveformBackdrop(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    val bars = normalizeBackdropSamples(samples, targetCount = RECORD_WAVEFORM_TARGET_BAR_COUNT)
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val baselineY = size.height * 0.76f
        drawLine(
            color = baselineColor,
            start =
                androidx.compose.ui.geometry
                    .Offset(0f, baselineY),
            end =
                androidx.compose.ui.geometry
                    .Offset(size.width, baselineY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val barWidth =
            (size.width / (bars.size * 3.8f))
                .coerceAtLeast(1.dp.toPx())
                .coerceAtMost(RECORD_WAVEFORM_MAX_BAR_WIDTH.toPx())
        val spacing = (size.width - (barWidth * bars.size)) / bars.size.coerceAtLeast(1)
        bars.forEachIndexed { index, sample ->
            val shapedAmplitude = subtleWaveformAmplitude(sample)
            val lineHeight = (size.height * 0.28f) * shapedAmplitude
            val x = (index * (barWidth + spacing)) + (barWidth / 2f)
            val startY = baselineY - lineHeight
            drawLine(
                color = color,
                start =
                    androidx.compose.ui.geometry
                        .Offset(x, startY),
                end =
                    androidx.compose.ui.geometry
                        .Offset(x, baselineY),
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

private fun normalizeBackdropSamples(
    samples: List<Float>,
    targetCount: Int,
): List<Float> {
    if (targetCount <= 0) return emptyList()
    if (samples.isEmpty()) return List(targetCount) { 0f }
    if (samples.size == 1) return List(targetCount) { samples.first() }

    val normalized = MutableList(targetCount) { 0f }
    samples.forEachIndexed { index, sample ->
        val bucket =
            ((index.toFloat() / samples.lastIndex.coerceAtLeast(1)) * (targetCount - 1))
                .roundToInt()
                .coerceIn(0, targetCount - 1)
        normalized[bucket] = maxOf(normalized[bucket], sample)
    }
    return normalized
}

@Composable
internal fun RecordsFilterBar(
    filters: RecordsFilterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraFilterCount = activeRecordsFilterCount(filters)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = buildCompactFilterSummary(filters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extraFilterCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = extraFilterCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
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
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterSectionLabel(icon = Icons.Filled.Inventory2, text = "Archive View")
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
                HorizontalDivider()
                FilterSectionLabel(icon = Icons.Filled.Sort, text = "Sort")
                RecordsSortOption.entries.forEach { option ->
                    FilterOptionRow(
                        title = option.name.lowercase().replaceFirstChar(Char::titlecase),
                        selected = draft.sortOption == option,
                        onClick = { draft = draft.copy(sortOption = option) },
                    )
                }
                HorizontalDivider()
                FilterSectionLabel(icon = Icons.Filled.CalendarMonth, text = "Date Range")
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
                FilterSectionLabel(icon = Icons.Filled.Circle, text = "Status")
                SessionStatus.entries
                    .filter {
                        it in setOf(SessionStatus.RECORDED, SessionStatus.TRANSCRIBING, SessionStatus.FAILED, SessionStatus.TRANSCRIBED, SessionStatus.EDITED)
                    }.forEach { status ->
                        StatusToggleRow(
                            status = status,
                            checked = status in draft.includedStatuses,
                            onToggle = {
                                draft =
                                    draft.copy(
                                        includedStatuses =
                                            draft.includedStatuses.toMutableSet().apply {
                                                if (contains(status)) remove(status) else add(status)
                                            },
                                    )
                            },
                        )
                    }
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

@Composable
private fun FilterSectionLabel(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle, onLongClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text =
                status.name
                    .lowercase()
                    .replace('_', ' ')
                    .replaceFirstChar(Char::titlecase),
        )
    }
}

@Composable
internal fun RecordInfoDialog(
    info: RecordInfo,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine("Recorded", info.createdAt.atZone(ZoneId.systemDefault()).format(HISTORY_TIME_FORMATTER))
                if (info.tags.isNotEmpty()) {
                    InfoLine("Tags", info.tags.joinToString(", "))
                }
                InfoLine("Duration", formatDuration(info.durationMs))
                InfoLine("File Size", formatFileSize(info.fileSizeBytes))
                InfoLine("Type", info.audioFormat)
                InfoLine(
                    "Quality",
                    "${info.sampleRateHz / 1000} kHz · ${info.encodingBitRate / 1000} kbps · ${if (info.channelCount == 1) "Mono" else "Stereo"}",
                )
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
        dismissButton = {
            if (onDelete != null) {
                if (confirmDelete) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                    ) {
                        Text("Confirm Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
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
    onSuggestAiTitle: (suspend () -> String?)? = null,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var isSuggesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                trailingIcon =
                    if (onSuggestAiTitle != null) {
                        {
                            if (isSuggesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            isSuggesting = true
                                            onSuggestAiTitle()?.let { title = it }
                                            isSuggesting = false
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = "Suggest title with AI",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
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
        tags = session.tags,
        createdAt = session.createdAt,
        durationMs = session.durationMs,
        fileSizeBytes = session.fileSizeBytes,
        audioFormat = session.audioFormat.name,
        sampleRateHz = session.sampleRateHz,
        encodingBitRate = session.encodingBitRate,
        channelCount = session.channelCount,
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
    val statusSummary =
        if (filters.includedStatuses.isEmpty()) {
            "All statuses"
        } else {
            filters.includedStatuses.joinToString { it.name.lowercase().replace('_', ' ') }
        }
    val archiveSummary = if (filters.showArchived) "Archived only" else "Active only"
    val dateSummary =
        when (filters.dateRange) {
            RecordsDateRange.ALL -> "All time"
            RecordsDateRange.TODAY -> "Today"
            RecordsDateRange.LAST_7_DAYS -> "Last 7 days"
            RecordsDateRange.LAST_30_DAYS -> "Last 30 days"
        }
    val sortSummary =
        when (filters.sortOption) {
            RecordsSortOption.NEWEST -> "Newest first"
            RecordsSortOption.OLDEST -> "Oldest first"
            RecordsSortOption.LONGEST -> "Longest first"
            RecordsSortOption.LARGEST -> "Largest first"
            RecordsSortOption.ALPHABETICAL -> "Name A→Z"
        }
    return "$dateSummary · $sortSummary · $statusSummary · $archiveSummary"
}

internal fun buildCompactFilterSummary(filters: RecordsFilterState): String {
    val parts =
        buildList {
            add(if (filters.showArchived) "Archived" else "Active")
            when (filters.dateRange) {
                RecordsDateRange.ALL -> Unit
                RecordsDateRange.TODAY -> add("Today")
                RecordsDateRange.LAST_7_DAYS -> add("Last 7 days")
                RecordsDateRange.LAST_30_DAYS -> add("Last 30 days")
            }
            when (filters.sortOption) {
                RecordsSortOption.NEWEST -> Unit
                RecordsSortOption.OLDEST -> add("Oldest")
                RecordsSortOption.LONGEST -> add("Longest")
                RecordsSortOption.LARGEST -> add("Largest")
                RecordsSortOption.ALPHABETICAL -> add("Name A→Z")
            }
            if (filters.includedStatuses.isNotEmpty()) {
                add(
                    if (filters.includedStatuses.size == 1) {
                        filters.includedStatuses
                            .first()
                            .name
                            .lowercase()
                            .replace('_', ' ')
                            .replaceFirstChar(Char::titlecase)
                    } else {
                        "${filters.includedStatuses.size} statuses"
                    },
                )
            }
        }
    return parts.joinToString(" · ")
}

internal fun activeRecordsFilterCount(filters: RecordsFilterState): Int =
    listOf(
        filters.dateRange != RecordsDateRange.ALL,
        filters.sortOption != RecordsSortOption.NEWEST,
        filters.includedStatuses.isNotEmpty(),
        filters.selectedTag != null,
    ).count { it }

internal fun openAudioWith(
    context: Context,
    session: RecordingSession,
) {
    val file = File(session.audioFilePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType =
        when (file.extension.lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"
            else -> "audio/*"
        }
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
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

@Composable
internal fun FolderRow(
    folder: Folder,
    expanded: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(folder.id) { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "folder-chevron-${folder.id}",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(chevronAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to\u2026") },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        },
                    )
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

@Composable
internal fun RenameFolderDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Folder name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun BreadcrumbRow(
    breadcrumb: List<Folder>,
    onNavigateToRoot: () -> Unit,
    onNavigateToFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Records",
            modifier = Modifier.clickable { onNavigateToRoot() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        breadcrumb.forEachIndexed { index, folder ->
            Text(
                text = " \u203A ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val isLast = index == breadcrumb.lastIndex
            Text(
                text = folder.name,
                modifier = if (!isLast) Modifier.clickable { onNavigateToFolder(folder.id) } else Modifier,
                style = MaterialTheme.typography.labelMedium,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Folder name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun MoveFolderDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Root (no folder)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                folders.forEach { folder ->
                    Surface(
                        onClick = { onSelect(folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (folders.isEmpty()) {
                    Text(
                        "No folders yet. Create a folder first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
