package dev.scrybe.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared crossfade+slide transition for text that updates while the toast stays visible. */
private fun contentSwapTransition() =
    (slideInVertically(animationSpec = tween(180)) { it / 3 } + fadeIn(tween(180)))
        .togetherWith(slideOutVertically(animationSpec = tween(140)) { -it / 3 } + fadeOut(tween(140)))

/**
 * Live status toast shown while a recording is being transcribed, sliding up from the bottom of
 * the screen — same pattern as Shelf Snap's `ResearchProgressToast`. Lives at the app level (see
 * [ScrybeApp]) rather than inside any one screen, since transcription can be triggered from the
 * Records list, a session's detail screen, or automatically after recording, and keeps running if
 * the user navigates elsewhere while it's in progress.
 */
@Composable
fun TranscriptionProgressToast(
    visible: Boolean,
    label: String,
    queuedCount: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { fullHeight -> fullHeight } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { fullHeight -> fullHeight } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    // Two independent AnimatedContents (not one shared on the whole toast) so a
                    // queue-count change doesn't replay the label transition and vice versa.
                    AnimatedContent(
                        targetState = "Transcribing…",
                        transitionSpec = { contentSwapTransition() },
                        label = "transcriptionPhase",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (label.isNotBlank()) {
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = { contentSwapTransition() },
                            label = "transcriptionLabel",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (queuedCount > 0) {
                        AnimatedContent(
                            targetState = "$queuedCount more queued",
                            transitionSpec = { contentSwapTransition() },
                            label = "transcriptionQueue",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
