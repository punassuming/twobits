package dev.scrybe.feature.sessiondetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
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
    onSeek: (Long) -> Unit,
) {
    ScrybeSectionCard(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScrybeSectionHeader(
            title = "Playback",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onTogglePlayback) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = onStopPlayback,
                        enabled = state.isPlaying || state.playbackPositionMs > 0L,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
        ) {
            WaveformTimeline(
                samples = state.session.waveformSamples,
                progress =
                    if (state.playbackDurationMs > 0L) {
                        state.playbackPositionMs.toFloat() / state.playbackDurationMs.toFloat()
                    } else {
                        0f
                    },
                durationMs = state.playbackDurationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxSize(),
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
        if (state.speakerSegments.isNotEmpty()) {
            PerSpeakerTimelineSection(
                segments = state.speakerSegments,
                durationMs = state.session.durationMs,
                playbackPositionMs = state.playbackPositionMs,
                onSeek = onSeek,
            )
        }
        if (state.sentimentSegments.isNotEmpty()) {
            SentimentBar(
                segments = state.sentimentSegments,
                durationMs = state.session.durationMs,
            )
        }
        if (state.topicMarkers.isNotEmpty()) {
            TopicMarkerBar(
                markers = state.topicMarkers,
                durationMs = state.session.durationMs,
            )
        }
    }
}

@Composable
private fun WaveformTimeline(
    samples: List<Float>,
    progress: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = normalizePlaybackSamples(samples, targetCount = PLAYBACK_WAVEFORM_TARGET_BAR_COUNT)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val playheadColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val waveformWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }

        fun seekToOffset(offsetX: Float) {
            if (durationMs <= 0L) return
            val clampedProgress = (offsetX / waveformWidthPx).coerceIn(0f, 1f)
            onSeek((durationMs * clampedProgress).roundToLong().coerceIn(0L, durationMs))
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
                drawLine(
                    color = if (index <= progressIndex) activeColor else inactiveColor,
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
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFF9C27B0),
    )

@Composable
internal fun SpeakerSegmentBar(
    segments: List<SpeakerSegment>,
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
                val colorIdx =
                    seg.speakerId
                        .lastOrNull { it.isDigit() }
                        ?.digitToInt()
                        ?.rem(speakerColorPalette.size) ?: 0
                val alpha = if (seg.endMs <= playbackPositionMs) 0.9f else 0.45f
                drawLine(
                    color = speakerColorPalette[colorIdx].copy(alpha = alpha),
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
            val colorIdx =
                speakerId
                    .lastOrNull { it.isDigit() }
                    ?.digitToInt()
                    ?.rem(speakerColorPalette.size) ?: (idx % speakerColorPalette.size)
            val color = speakerColorPalette[colorIdx]
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
internal fun SentimentBar(
    segments: List<SentimentSegment>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || durationMs <= 0L) return
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        segments.forEach { seg ->
            val startX = (seg.startMs.toFloat() / durationMs) * size.width
            val segWidth = ((seg.endMs - seg.startMs).toFloat() / durationMs) * size.width
            val color =
                when (seg.sentiment.uppercase()) {
                    "POSITIVE" -> Color(0xFF4CAF50)
                    "NEGATIVE" -> Color(0xFFF44336)
                    else -> Color(0xFF9E9E9E)
                }
            drawRect(
                color = color.copy(alpha = 0.65f),
                topLeft = Offset(startX, 0f),
                size = Size(segWidth.coerceAtLeast(1f), size.height),
            )
        }
    }
}

@Composable
internal fun TopicMarkerBar(
    markers: List<TopicMarker>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || durationMs <= 0L) return
    var selectedMarker by remember { mutableStateOf<TopicMarker?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(18.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(markers, durationMs, widthPx) {
                        detectTapGestures { offset ->
                            val tapMs = (offset.x / widthPx) * durationMs
                            selectedMarker = markers.minByOrNull { abs(it.timeMs - tapMs.toLong()) }
                        }
                    },
        ) {
            markers.forEach { marker ->
                val x = (marker.timeMs.toFloat() / durationMs) * size.width
                drawLine(
                    color = Color(0xFFFF9800).copy(alpha = 0.8f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        selectedMarker?.let { marker ->
            AlertDialog(
                onDismissRequest = { selectedMarker = null },
                title = { Text("Topic") },
                text = { Text(marker.label) },
                confirmButton = { TextButton(onClick = { selectedMarker = null }) { Text("OK") } },
            )
        }
    }
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

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
