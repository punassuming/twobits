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
                playbackPositionMs = state.playbackPositionMs,
                progress =
                    if (state.playbackDurationMs > 0L) {
                        state.playbackPositionMs.toFloat() / state.playbackDurationMs.toFloat()
                    } else {
                        0f
                    },
                durationMs = state.playbackDurationMs,
                sentimentSegments = state.sentimentSegments,
                topicMarkers = state.topicMarkers,
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
    }
}

@Composable
private fun WaveformTimeline(
    samples: List<Float>,
    playbackPositionMs: Long,
    progress: Float,
    durationMs: Long,
    sentimentSegments: List<SentimentSegment>,
    topicMarkers: List<TopicMarker>,
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
        val activeIntent = nearestIntentMarker(topicMarkers, playbackPositionMs, durationMs)

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
                drawLine(
                    color = if (index <= progressIndex) activeColor else inactiveColor,
                    start = Offset(x, centerY - lineHeight),
                    end = Offset(x, centerY + lineHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
            drawSentimentDots(
                segments = sentimentSegments,
                durationMs = durationMs,
                canvasWidth = size.width,
                centerY = centerY,
            )
            drawIntentDots(
                markers = topicMarkers,
                durationMs = durationMs,
                canvasWidth = size.width,
                centerY = centerY,
                activeMarker = activeIntent,
            )
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
        Color(0xFF2196F3),
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFF9C27B0),
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSentimentDots(
    segments: List<SentimentSegment>,
    durationMs: Long,
    canvasWidth: Float,
    centerY: Float,
) {
    if (segments.isEmpty() || durationMs <= 0L) return
    segments.forEach { segment ->
        val midpointMs = (segment.startMs + segment.endMs) / 2L
        val x = (midpointMs.toFloat() / durationMs) * canvasWidth
        drawCircle(
            color = sentimentColor(segment.sentiment).copy(alpha = 0.88f),
            radius = 4.dp.toPx(),
            center = Offset(x, centerY + 22.dp.toPx()),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntentDots(
    markers: List<TopicMarker>,
    durationMs: Long,
    canvasWidth: Float,
    centerY: Float,
    activeMarker: TopicMarker?,
) {
    if (markers.isEmpty() || durationMs <= 0L) return
    markers.forEach { marker ->
        val x = (marker.timeMs.toFloat() / durationMs) * canvasWidth
        drawCircle(
            color =
                if (marker == activeMarker) {
                    Color(0xFFFFB74D)
                } else {
                    Color(0xFFFF9800).copy(alpha = 0.88f)
                },
            radius = if (marker == activeMarker) 5.dp.toPx() else 4.dp.toPx(),
            center = Offset(x, centerY - 22.dp.toPx()),
        )
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

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
