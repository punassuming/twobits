package dev.scrybe.feature.sessiondetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackCard(
    state: SessionDetailUiState.Success,
    onSeek: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    var sliderPosition by remember(state.session.id, state.playbackPositionMs, state.playbackDurationMs) {
        mutableFloatStateOf(state.playbackPositionMs.toFloat())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Playback",
                style = MaterialTheme.typography.titleSmall,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
            ) {
                WaveformTimeline(
                    samples = state.session.waveformSamples,
                    progress =
                        if (state.playbackDurationMs > 0L) {
                            state.playbackPositionMs.toFloat() / state.playbackDurationMs.toFloat()
                        } else {
                            0f
                        },
                    modifier = Modifier.fillMaxSize(),
                )
                Slider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..state.playbackDurationMs.coerceAtLeast(1L).toFloat(),
                    onValueChangeFinished = {
                        onSeek(sliderPosition.toLong())
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
}

@Composable
private fun WaveformTimeline(
    samples: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val bars = if (samples.isEmpty()) List(48) { 0f } else samples
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val playheadColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    Canvas(modifier = modifier) {
        val centerY = size.height * 0.5f
        drawLine(
            color = baselineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val barWidth = size.width / (bars.size * 1.5f)
        val spacing = barWidth / 2f
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

private fun playbackAmplitude(sample: Float): Float {
    val gated = if (sample < 0.02f) 0f else sample
    return (0.012f + (gated * 0.88f)).coerceIn(0.012f, 0.9f)
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
