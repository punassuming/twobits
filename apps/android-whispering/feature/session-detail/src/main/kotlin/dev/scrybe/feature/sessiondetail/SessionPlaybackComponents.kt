package dev.scrybe.feature.sessiondetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
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
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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

@Composable
internal fun RenamePromptDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

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
