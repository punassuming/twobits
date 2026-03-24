package dev.scrybe.feature.sessiondetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackCard(
    state: SessionDetailUiState.Success,
    onSeek: (Long) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Playback",
                style = MaterialTheme.typography.titleSmall,
            )
            WaveformTimeline(
                samples = state.session.waveformSamples,
                progress = if (state.playbackDurationMs > 0L) {
                    state.playbackPositionMs.toFloat() / state.playbackDurationMs.toFloat()
                } else {
                    0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 0f..state.playbackDurationMs.coerceAtLeast(1L).toFloat(),
                onValueChangeFinished = {
                    onSeek(sliderPosition.toLong())
                },
            )
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
    val bars = if (samples.isEmpty()) List(48) { 0.12f } else samples
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Canvas(modifier = modifier) {
        val barWidth = size.width / (bars.size * 1.5f)
        val spacing = barWidth / 2f
        val progressIndex = (bars.size * progress.coerceIn(0f, 1f)).toInt()
        bars.forEachIndexed { index, sample ->
            val heightFactor = 0.18f + (sample * 0.72f)
            val lineHeight = size.height * heightFactor
            val x = (index * (barWidth + spacing)) + (barWidth / 2f)
            val startY = (size.height - lineHeight) / 2f
            drawLine(
                color = if (index <= progressIndex) activeColor else inactiveColor,
                start = androidx.compose.ui.geometry.Offset(x, startY),
                end = androidx.compose.ui.geometry.Offset(x, startY + lineHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
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
