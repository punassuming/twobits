package dev.scrybe.feature.sessiondetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.twobits.design.components.AppSectionCard
import dev.scrybe.core.model.Person
import dev.scrybe.core.model.SentimentSegment
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.model.TopicMarker
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val PLAYBACK_WAVEFORM_TARGET_BAR_COUNT = 120
private val PLAYBACK_WAVEFORM_MAX_BAR_WIDTH = 2.dp

@Composable
internal fun PlaybackCard(
    state: SessionDetailUiState.Success,
    onTogglePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit = {},
    onSpeakerClick: (speakerId: String) -> Unit = {},
    onManageSpeakers: (() -> Unit)? = null,
) {
    AppSectionCard(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            IntentDotStrip(
                markers = state.topicMarkers,
                durationMs = state.playbackDurationMs,
                modifier = Modifier.fillMaxWidth().height(14.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                WaveformTimeline(
                    samples = state.session.waveformSamples,
                    playbackPositionMs = state.playbackPositionMs,
                    progress =
                        if (state.playbackDurationMs > 0L) {
                            state.playbackPositionMs.toFloat() / state.playbackDurationMs.toFloat()
                        } else {
                            0f
                        },
                    durationMs = state.playbackDurationMs,
                    topicMarkers = state.topicMarkers,
                    speakerSegments = state.speakerSegments,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SentimentDotStrip(
                segments = state.sentimentSegments,
                durationMs = state.playbackDurationMs,
                modifier = Modifier.fillMaxWidth().height(14.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatPlaybackTime(state.playbackPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPlaybackTime(state.playbackDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedPill(speed = state.playbackSpeed, onClick = onCycleSpeed)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSkipBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.Replay10,
                    contentDescription = "Skip back 10s",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Surface(
                onClick = onTogglePlayback,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onSkipForward, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.Forward10,
                    contentDescription = "Skip forward 10s",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onManageSpeakers != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onManageSpeakers, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription =
                            if (state.speakerSegments.isEmpty()) "Identify speakers" else "Manage speakers",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Info button that reveals the marker and speaker legends in a click tooltip — lives inline with
 * the session's type/location header row. Hidden entirely when there is nothing to explain
 * (no markers, no sentiments, no speakers).
 */
@Composable
internal fun LegendInfoButton(
    hasTopicMarkers: Boolean,
    hasSentimentSegments: Boolean,
    speakerSegments: List<SpeakerSegment>,
    persons: List<Person>,
    onSpeakerClick: (speakerId: String) -> Unit,
) {
    if (!hasTopicMarkers && !hasSentimentSegments && speakerSegments.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Show marker and speaker legend",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasTopicMarkers || hasSentimentSegments) {
                    Text(
                        "Markers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasTopicMarkers) {
                        LegendChip(color = Color(0xFFFF9800), label = "Topics — tap a dot to hear that moment")
                    }
                    if (hasSentimentSegments) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendChip(color = Color(0xFF4CAF50), label = "Positive")
                            LegendChip(color = Color(0xFF9E9E9E), label = "Neutral")
                            LegendChip(color = Color(0xFFF44336), label = "Negative")
                        }
                    }
                }
                val speakerIds = speakerSegments.map { it.speakerId }.distinct().sorted()
                if (speakerIds.isNotEmpty()) {
                    Text(
                        "Speakers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val personMap = persons.associate { it.id to it.name }
                    speakerIds.forEachIndexed { idx, speakerId ->
                        val color = speakerColorForIndex(idx)
                        val seg = speakerSegments.first { it.speakerId == speakerId }
                        val personName = seg.personId?.let { personMap[it] }
                        Row(
                            modifier =
                                Modifier.clickable {
                                    expanded = false
                                    onSpeakerClick(speakerId)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = color) }
                            Text(
                                text =
                                    personName
                                        ?: speakerId.removePrefix("SPEAKER_").let { "Speaker $it" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text(
                        "Tap a speaker to assign a person",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformTimeline(
    samples: List<Float>,
    playbackPositionMs: Long,
    progress: Float,
    durationMs: Long,
    topicMarkers: List<TopicMarker>,
    speakerSegments: List<SpeakerSegment> = emptyList(),
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = normalizePlaybackSamples(samples, targetCount = PLAYBACK_WAVEFORM_TARGET_BAR_COUNT)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val playheadColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val speakerIndex =
        speakerSegments
            .map { it.speakerId }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (i, id) -> id to i }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val waveformWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val activeIntent =
            remember(topicMarkers, playbackPositionMs, durationMs) {
                nearestIntentMarker(topicMarkers, playbackPositionMs, durationMs)
            }

        fun seekToOffset(offsetX: Float) {
            if (durationMs <= 0L) return
            val clampedProgress = (offsetX / waveformWidthPx).coerceIn(0f, 1f)
            onSeek((durationMs * clampedProgress).roundToLong().coerceIn(0L, durationMs))
        }

        activeIntent?.let { marker ->
            val markerProgress = marker.timeMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()
            val tooltipStart =
                with(density) {
                    ((maxWidth * markerProgress).toPx() - 72.dp.toPx())
                        .coerceIn(0f, (waveformWidthPx - 144.dp.toPx()).coerceAtLeast(0f))
                        .toDp()
                }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = tooltipStart, top = 2.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = marker.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs, waveformWidthPx) {
                        detectTapGestures { offset ->
                            if (durationMs > 0L) {
                                seekToOffset(offset.x)
                            }
                        }
                    }.pointerInput(durationMs, waveformWidthPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, _ ->
                                if (durationMs > 0L) {
                                    seekToOffset(change.position.x)
                                    change.consume()
                                }
                            },
                        )
                    },
        ) {
            val centerY = size.height * 0.5f
            drawLine(
                color = baselineColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val barWidth =
                (size.width / (bars.size * 3.2f))
                    .coerceAtLeast(1.dp.toPx())
                    .coerceAtMost(PLAYBACK_WAVEFORM_MAX_BAR_WIDTH.toPx())
            val spacing = (size.width - (barWidth * bars.size)) / bars.size.coerceAtLeast(1)
            val progressIndex = (bars.size * progress.coerceIn(0f, 1f)).toInt()
            bars.forEachIndexed { index, sample ->
                val smoothed = densitySmoothed(bars, index, windowRadius = 2)
                if (smoothed > 0.02f) {
                    val x = (index * (barWidth + spacing)) + (barWidth / 2f)
                    val lh = (size.height * 0.34f) * smoothed
                    drawLine(
                        color = activeColor.copy(alpha = smoothed * 0.18f),
                        start = Offset(x, centerY - lh * 1.6f),
                        end = Offset(x, centerY + lh * 1.6f),
                        strokeWidth = barWidth * 4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            bars.forEachIndexed { index, sample ->
                val shapedAmplitude = playbackAmplitude(sample)
                val lineHeight = (size.height * 0.34f) * shapedAmplitude
                val x = (index * (barWidth + spacing)) + (barWidth / 2f)
                val barTimeMs =
                    if (durationMs > 0L) (index.toFloat() / bars.size.toFloat()) * durationMs else 0f
                val activeSpeaker =
                    speakerSegments.firstOrNull { barTimeMs >= it.startMs && barTimeMs <= it.endMs }
                val barColor =
                    if (activeSpeaker != null) {
                        val idx = speakerIndex[activeSpeaker.speakerId] ?: 0
                        speakerColorForIndex(idx).copy(alpha = if (index <= progressIndex) 0.85f else 0.40f)
                    } else {
                        if (index <= progressIndex) activeColor else inactiveColor
                    }
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY - lineHeight),
                    end = Offset(x, centerY + lineHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
            val playheadX = size.width * progress.coerceIn(0f, 1f)
            drawLine(
                color = playheadColor,
                start = Offset(playheadX, size.height * 0.1f),
                end = Offset(playheadX, size.height * 0.9f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun playbackAmplitude(sample: Float): Float {
    val gated = if (sample < 0.02f) 0f else sample
    return (0.012f + (gated * 0.88f)).coerceIn(0.012f, 0.9f)
}

private fun normalizePlaybackSamples(
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

private fun densitySmoothed(
    bars: List<Float>,
    index: Int,
    windowRadius: Int,
): Float {
    val from = (index - windowRadius).coerceAtLeast(0)
    val to = (index + windowRadius).coerceAtMost(bars.lastIndex)
    return bars.subList(from, to + 1).average().toFloat()
}

internal val speakerColorPalette =
    listOf(
        // signal blue
        Color(0xFF89C7FF),
        // glow green
        Color(0xFF88D7A8),
        // ember
        Color(0xFFFFB695),
        // purple
        Color(0xFFC6A0F6),
    )

internal fun speakerColorForIndex(index: Int): Color = speakerColorPalette[index.mod(speakerColorPalette.size)]

@Composable
internal fun SpeakerSegmentBar(
    segments: List<SpeakerSegment>,
    color: Color,
    durationMs: Long,
    playbackPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || durationMs <= 0L) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs, widthPx) {
                        detectTapGestures { offset ->
                            onSeek(((offset.x / widthPx) * durationMs).toLong().coerceIn(0L, durationMs))
                        }
                    },
        ) {
            segments.forEach { seg ->
                val startX = (seg.startMs.toFloat() / durationMs) * size.width
                val endX = (seg.endMs.toFloat() / durationMs) * size.width
                val alpha = if (seg.endMs <= playbackPositionMs) 0.9f else 0.45f
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(startX, size.height / 2f),
                    end = Offset((endX).coerceAtLeast(startX + 2f), size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun PerSpeakerTimelineSection(
    segments: List<SpeakerSegment>,
    durationMs: Long,
    playbackPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || durationMs <= 0L) return
    val speakerIds =
        segments
            .map { it.speakerId }
            .distinct()
            .sorted()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        speakerIds.forEachIndexed { idx, speakerId ->
            val color = speakerColorForIndex(idx)
            val label =
                speakerId
                    .removePrefix("SPEAKER_")
                    .let { "Speaker $it" }
            val speakerSegs = segments.filter { it.speakerId == speakerId }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .padding(0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = color)
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                SpeakerSegmentBar(
                    segments = speakerSegs,
                    color = color,
                    durationMs = durationMs,
                    playbackPositionMs = playbackPositionMs,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LegendChip(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(color.copy(alpha = 0.88f), CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntentDotStrip(
    markers: List<TopicMarker>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || durationMs <= 0L) return
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        markers.forEach { marker ->
            val x = (marker.timeMs.toFloat() / durationMs) * size.width
            drawCircle(
                color = Color(0xFFFF9800).copy(alpha = 0.88f),
                radius = 4.dp.toPx(),
                center = Offset(x, centerY),
            )
        }
    }
}

@Composable
private fun SentimentDotStrip(
    segments: List<SentimentSegment>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || durationMs <= 0L) return
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        segments.forEach { segment ->
            val midpointMs = (segment.startMs + segment.endMs) / 2L
            val x = (midpointMs.toFloat() / durationMs) * size.width
            drawCircle(
                color = sentimentColor(segment.sentiment).copy(alpha = 0.88f),
                radius = 4.dp.toPx(),
                center = Offset(x, centerY),
            )
        }
    }
}

private fun sentimentColor(sentiment: String): Color =
    when (sentiment.uppercase()) {
        "POSITIVE" -> Color(0xFF4CAF50)
        "NEGATIVE" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }

private fun nearestIntentMarker(
    markers: List<TopicMarker>,
    playbackPositionMs: Long,
    durationMs: Long,
): TopicMarker? {
    if (markers.isEmpty() || durationMs <= 0L) return null
    val thresholdMs = (durationMs * 0.015f).roundToLong().coerceIn(900L, 4_000L)
    val nearest = markers.minByOrNull { marker -> abs(marker.timeMs - playbackPositionMs) }
    return nearest?.takeIf { marker -> abs(marker.timeMs - playbackPositionMs) <= thresholdMs }
}

@Composable
internal fun RenamePromptDialog(
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
        title = { Text("Name This Recording") },
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
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

@Composable
private fun SpeedPill(
    speed: Float,
    onClick: () -> Unit,
) {
    val isNonDefault = speed != 1.0f
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isNonDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isNonDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = formatSpeed(speed),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatSpeed(speed: Float): String =
    when {
        speed == speed.toLong().toFloat() -> "${speed.toLong()}×"
        else -> "$speed×"
    }

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
