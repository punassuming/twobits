package dev.scrybe.feature.history

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.scrybe.core.common.SessionStatusPresentation
import dev.scrybe.core.model.Folder
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val RECORD_ROW_SWIPE_THRESHOLD_FRACTION = 0.68f
private const val RECORD_ROW_EDGE_SWIPE_ZONE_FRACTION = 0.12f
private const val RECORD_WAVEFORM_TARGET_BAR_COUNT = 120
private val RECORD_WAVEFORM_MAX_BAR_WIDTH = 2.dp
private const val RECORD_ROW_SWIPE_CONTENT_MIN_SCALE = 0.84f

internal fun recordRowEdgeSwipeZoneFraction(): Float = RECORD_ROW_EDGE_SWIPE_ZONE_FRACTION

internal enum class RecordSwipeAction {
    TRANSFORM,
    ARCHIVE,
    RESTORE,
}

internal fun recordSwipeConfirmationTitle(action: RecordSwipeAction): String =
    when (action) {
        RecordSwipeAction.TRANSFORM -> "Run Default Transform"
        RecordSwipeAction.ARCHIVE -> "Archive Record"
        RecordSwipeAction.RESTORE -> "Restore Record"
    }

internal fun recordSwipeConfirmationMessage(
    action: RecordSwipeAction,
    title: String,
): String =
    when (action) {
        RecordSwipeAction.TRANSFORM -> "Run the default transform for $title?"
        RecordSwipeAction.ARCHIVE -> "Archive $title? You can restore it later from archived records."
        RecordSwipeAction.RESTORE -> "Restore $title to the active records list?"
    }

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
    onShareTranscript: () -> Unit,
    onRetryTranscription: () -> Unit,
    onResetTranscriptionState: () -> Unit,
    showRecordingInfo: Boolean,
    confirmSwipeActions: Boolean,
) {
    var menuExpanded by remember(item.session.id) { mutableStateOf(false) }
    var pendingSwipeAction by remember(item.session.id) { mutableStateOf<RecordSwipeAction?>(null) }
    var swipePreviewAction by remember(item.session.id) { mutableStateOf<RecordSwipeAction?>(null) }
    var swipePreviewProgress by remember(item.session.id) { mutableFloatStateOf(0f) }
    val animatedSwipePreviewProgress by animateFloatAsState(
        targetValue = swipePreviewProgress,
        animationSpec = tween(durationMillis = 140),
        label = "record-row-swipe-preview",
    )
    val contentScale =
        1f - ((1f - RECORD_ROW_SWIPE_CONTENT_MIN_SCALE) * animatedSwipePreviewProgress.coerceIn(0f, 1f))
    val runSwipeAction: (RecordSwipeAction) -> Unit = { action ->
        if (confirmSwipeActions) {
            pendingSwipeAction = action
        } else {
            when (action) {
                RecordSwipeAction.TRANSFORM -> onTransform()
                RecordSwipeAction.ARCHIVE -> onArchive()
                RecordSwipeAction.RESTORE -> onRestore()
            }
        }
    }

    val rowContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!selectionEnabled) {
                SwipeBackground(
                    action = swipePreviewAction,
                    progress = animatedSwipePreviewProgress,
                )
            }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                            alpha = 1f - (animatedSwipePreviewProgress * 0.16f)
                        }
                        .recordSwipePreview(
                            enabled = !selectionEnabled,
                            isArchived = item.session.isArchived,
                            onPreviewChanged = { action, progress ->
                                swipePreviewAction = action
                                swipePreviewProgress = progress
                            },
                            onTriggered = runSwipeAction,
                        )
                        .combinedClickable(
                            onClick = {
                                if (selectionEnabled) onToggleSelection() else onOpen()
                            },
                            onLongClick = {
                                if (selectionEnabled) onToggleSelection() else onLongPress()
                            },
                        ),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        recordContainerColor(item.session.status, item.session.isArchived)
                    },
                shape = MaterialTheme.shapes.large,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    WaveformBackdrop(
                        samples = item.session.waveformSamples,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    if (!selectionEnabled) {
                        Icon(
                            Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 4.dp)
                                    .size(16.dp)
                                    .graphicsLayer {
                                        alpha = 0.22f * (1f - animatedSwipePreviewProgress.coerceIn(0f, 1f))
                                    },
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            if (item.session.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(16.dp)
                                    .graphicsLayer {
                                        alpha = 0.22f * (1f - animatedSwipePreviewProgress.coerceIn(0f, 1f))
                                    },
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (selectionEnabled) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggleSelection() },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Icon(
                            SessionStatusPresentation.icon(item.session.status, item.session.isArchived),
                            contentDescription = SessionStatusPresentation.label(item.session.status, item.session.isArchived),
                            modifier = Modifier.padding(top = 2.dp),
                            tint = SessionStatusPresentation.color(item.session.status, item.session.isArchived),
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
                            if (showRecordingInfo) {
                                Text(
                                    text = buildMetaLine(item.session),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.session.tags.isNotEmpty()) {
                                Text(
                                    text = item.session.tags.joinToString("  •  "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                                        leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
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
                                    if (item.transcriptPreview != null) {
                                        DropdownMenuItem(
                                            text = { Text("Share Transcript") },
                                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                onShareTranscript()
                                            },
                                        )
                                    }
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
    }

    rowContent()

    pendingSwipeAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                pendingSwipeAction = null
            },
            title = { Text(recordSwipeConfirmationTitle(action)) },
            text = { Text(recordSwipeConfirmationMessage(action, item.session.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            RecordSwipeAction.TRANSFORM -> onTransform()
                            RecordSwipeAction.ARCHIVE -> onArchive()
                            RecordSwipeAction.RESTORE -> onRestore()
                        }
                        pendingSwipeAction = null
                    },
                ) {
                    Text(
                        when (action) {
                            RecordSwipeAction.TRANSFORM -> "Run"
                            RecordSwipeAction.ARCHIVE -> "Archive"
                            RecordSwipeAction.RESTORE -> "Restore"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSwipeAction = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun Modifier.recordSwipePreview(
    enabled: Boolean,
    isArchived: Boolean,
    onPreviewChanged: (RecordSwipeAction?, Float) -> Unit,
    onTriggered: (RecordSwipeAction) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(isArchived) {
            awaitEachGesture {
                onPreviewChanged(null, 0f)
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val width = size.width.toFloat()
                if (width <= 0f) return@awaitEachGesture

                val edgeWidth = width * RECORD_ROW_EDGE_SWIPE_ZONE_FRACTION
                val startedFromStart = down.position.x <= edgeWidth
                val startedFromEnd = down.position.x >= (width - edgeWidth)
                if (!startedFromStart && !startedFromEnd) return@awaitEachGesture

                val action = swipeActionForEdge(isArchived = isArchived, fromStart = startedFromStart)
                val directionSign = if (startedFromStart) 1f else -1f
                val triggerDistance = width * RECORD_ROW_SWIPE_THRESHOLD_FRACTION
                var pointerId = down.id
                var totalHorizontal = 0f
                var totalVertical = 0f
                var locked = false

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change =
                        event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                            ?: break

                    pointerId = change.id
                    val delta = change.positionChange()
                    totalHorizontal += delta.x
                    totalVertical += delta.y
                    val desiredDistance = totalHorizontal * directionSign

                    if (!locked) {
                        val horizontalDragDetected =
                            abs(totalHorizontal) > viewConfiguration.touchSlop &&
                                abs(totalHorizontal) > (abs(totalVertical) * 1.25f) &&
                                desiredDistance > 0f
                        val verticalDragDetected =
                            abs(totalVertical) > viewConfiguration.touchSlop &&
                                abs(totalVertical) >= abs(totalHorizontal)
                        if (verticalDragDetected || (abs(totalHorizontal) > viewConfiguration.touchSlop && desiredDistance <= 0f)) {
                            onPreviewChanged(null, 0f)
                            break
                        }
                        if (horizontalDragDetected) {
                            locked = true
                        }
                    }

                    if (locked) {
                        val progress = (desiredDistance / triggerDistance).coerceIn(0f, 1f)
                        onPreviewChanged(action, progress)
                        event.changes.filter { it.pressed }.forEach { it.consume() }
                    }

                    if (event.changes.none { it.pressed }) {
                        if (locked) {
                            val progress = (desiredDistance / triggerDistance).coerceIn(0f, 1f)
                            if (progress >= 1f) {
                                onTriggered(action)
                            }
                        }
                        onPreviewChanged(null, 0f)
                        break
                    }
                }
            }
        }
    }

private fun swipeActionForEdge(
    isArchived: Boolean,
    fromStart: Boolean,
): RecordSwipeAction =
    when {
        fromStart -> RecordSwipeAction.TRANSFORM
        isArchived -> RecordSwipeAction.RESTORE
        else -> RecordSwipeAction.ARCHIVE
    }

@Composable
private fun BoxScope.SwipeBackground(
    action: RecordSwipeAction?,
    progress: Float,
) {
    if (action == null || progress <= 0f) return
    val presentation = swipeActionPresentation(action)
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        color = presentation.containerColor.copy(alpha = 0.22f + (progress * 0.60f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = presentation.label,
                    tint = presentation.contentColor,
                )
                Text(
                    text = presentation.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = presentation.contentColor,
                )
            }
        }
    }
}

@Composable
private fun swipeActionPresentation(action: RecordSwipeAction): SwipeActionPresentation =
    when (action) {
        RecordSwipeAction.TRANSFORM ->
            SwipeActionPresentation(
                icon = Icons.Filled.AutoFixHigh,
                label = "Transform",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        RecordSwipeAction.ARCHIVE ->
            SwipeActionPresentation(
                icon = Icons.Filled.Archive,
                label = "Archive",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        RecordSwipeAction.RESTORE ->
            SwipeActionPresentation(
                icon = Icons.Filled.Unarchive,
                label = "Restore",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
    }

private data class SwipeActionPresentation(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
)

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
internal fun WaveformBackdrop(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    val bars = normalizeBackdropSamples(samples, targetCount = RECORD_WAVEFORM_TARGET_BAR_COUNT)
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
                    }
                    .forEach { status ->
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
        Text(text = status.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase))
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
        tags = session.tags,
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
            }
            if (filters.includedStatuses.isNotEmpty()) {
                add(
                    if (filters.includedStatuses.size == 1) {
                        filters.includedStatuses.first().name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(folder.id) { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
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
