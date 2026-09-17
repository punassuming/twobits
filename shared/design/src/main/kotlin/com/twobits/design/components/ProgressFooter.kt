package com.twobits.design.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared crossfade+slide transition for one line of text that updates while the footer stays visible. */
private fun progressFooterTextTransition() =
    (slideInVertically(animationSpec = tween(180)) { it / 3 } + fadeIn(tween(180)))
        .togetherWith(slideOutVertically(animationSpec = tween(140)) { -it / 3 } + fadeOut(tween(140)))

/**
 * The one bottom progress footer for a long-running background task, shared by all three apps —
 * local transcription (Scrybe), local vision/listing analysis and market research (Shelf Snap).
 * Before this, each app defined its own near-identical composable (`TranscriptionProgressToast`,
 * `LocalAnalysisProgressToast`, `ResearchProgressToast`) — same shape, same bugs to fix three
 * times over. Lives at the app's navigation root, in its own `Scaffold`'s `bottomBar` slot (see
 * call sites) — not inside any one screen, since the task it reports on can be triggered from
 * more than one screen and keeps running while the user navigates elsewhere.
 *
 * Deliberately reaches the true bottom edge of the screen with no gap below it: the `Surface`
 * carries no bottom margin of its own and only rounds its top corners, so its background fills
 * all the way to the edge; gesture-navigation clearance is applied to the content [Row] instead
 * of around the whole card. Wrapping `navigationBarsPadding()` around the *whole* card (the
 * predecessors' approach) leaves a strip of plain screen background below the card's rounded
 * edge no matter how its own margin is tuned — that's what repeatedly read as "a gap at the
 * bottom" across three separate fix attempts on the old, per-app composables.
 *
 * [primaryText] is the bold, always-shown headline (e.g. "Transcribing…"). [secondaryText] and
 * [tertiaryText] are optional detail lines, each animated independently so updating one doesn't
 * replay the others' transition. [onCancel] is optional — omit it for a task with no cancel
 * affordance (market research, local vision/listing analysis); when present, the button swaps to
 * a spinner and disables itself while [isCancelling] is true, so a second tap can't look like a
 * no-op while the underlying task is still working out how to stop.
 */
@Composable
fun ProgressFooter(
    visible: Boolean,
    primaryText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    tertiaryText: String? = null,
    onCancel: (() -> Unit)? = null,
    isCancelling: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { fullHeight -> fullHeight } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { fullHeight -> fullHeight } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    AnimatedContent(
                        targetState = primaryText,
                        transitionSpec = { progressFooterTextTransition() },
                        label = "progressFooterPrimary",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!secondaryText.isNullOrBlank()) {
                        AnimatedContent(
                            targetState = secondaryText,
                            transitionSpec = { progressFooterTextTransition() },
                            label = "progressFooterSecondary",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!tertiaryText.isNullOrBlank()) {
                        AnimatedContent(
                            targetState = tertiaryText,
                            transitionSpec = { progressFooterTextTransition() },
                            label = "progressFooterTertiary",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (onCancel != null) {
                    Spacer(Modifier.width(4.dp))
                    // Disabled (not just visually, functionally) while cancelling — the
                    // underlying native call (e.g. WhisperEngine's decode) can take up to one
                    // chunk to actually stop, so a second tap in that window would otherwise
                    // look like it's doing nothing.
                    IconButton(onClick = onCancel, enabled = !isCancelling, modifier = Modifier.size(32.dp)) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
