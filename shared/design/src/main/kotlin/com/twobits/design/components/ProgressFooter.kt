package com.twobits.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
 * replay the others' transition. [progressFraction] switches the leading spinner from
 * indeterminate to determinate when the caller actually knows how far through a chunked task it
 * is (e.g. chunk 3 of 7) — omit it, as most callers do, for a task with no natural fraction.
 * [onCancel] is optional — omit it for a task with no cancel affordance; when present, the button
 * swaps to a spinner and disables itself while [isCancelling] is true, so a second tap can't look
 * like a no-op while the underlying task is still working out how to stop.
 *
 * [startedAtMs], when non-null (an epoch-millis timestamp, e.g. `System.currentTimeMillis()` at
 * the moment the task began), renders as a trailing `m:ss` (or `h:mm:ss` past an hour) label that
 * ticks up once a second on its own — standardized here rather than left to each caller, since
 * the original version of this had one app (Shelf Snap's local analysis) baking its own
 * hand-formatted "...7s" straight into [primaryText], recomputed from whatever ticker its own
 * engine happened to expose. A plain start timestamp is the one thing every caller can trivially
 * provide (no per-app ticking `Flow` needed), and ticking lives here once instead of three times.
 * Independent of [tertiaryText]: a chunked task can show both a step count and how long it's
 * taken at once (e.g. "chunk 3 of 7" + "0:42").
 *
 * [onViewDetails], when non-null, makes the whole card clickable — not just another icon crowded
 * into the already-busy trailing area next to the elapsed time and cancel button — for a task
 * that has a real detail view to jump to (e.g. Shelf Snap's market research, whose per-query and
 * per-page-read breakdown otherwise only turns up if the user happens to reopen that exact item
 * and scroll to its Market tab while a run is in flight). [onCancel]'s own `IconButton` still gets
 * its tap first — Compose resolves nested clickables to the innermost hit target, so tapping
 * Cancel does not also trigger navigation.
 */
@Composable
fun ProgressFooter(
    visible: Boolean,
    primaryText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    tertiaryText: String? = null,
    progressFraction: Float? = null,
    startedAtMs: Long? = null,
    onCancel: (() -> Unit)? = null,
    isCancelling: Boolean = false,
    onViewDetails: (() -> Unit)? = null,
) {
    // Ticks once a second while startedAtMs is set — restarted (via the key) whenever a new task
    // starts, so a stale reading from a previous run can't linger into the next one's first tick.
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtMs) {
        if (startedAtMs == null) return@LaunchedEffect
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAtMs
            delay(1_000)
        }
    }

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
            modifier =
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth()
                    .let { if (onViewDetails != null) it.clickable(onClick = onViewDetails) else it },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progressFraction != null) {
                    CircularProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // weight(1f) is the fix, not fillMaxWidth() on the Row alone: without a weighted
                // child claiming the space the Row now stretches into, the cancel button below
                // still ends up sitting flush against the text instead of at the trailing edge —
                // which is exactly the "x is not right-aligned" this shape was reported as.
                Column(modifier = Modifier.weight(1f)) {
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
                if (startedAtMs != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatProgressFooterElapsed(elapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

/** `m:ss`, or `h:mm:ss` past an hour — the one format every caller of [elapsedMs] shares. */
private fun formatProgressFooterElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
