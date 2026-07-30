package com.shelfsnap.app.ui.itemdetail

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
import com.shelfsnap.app.data.remote.ResearchProgress

/** Shared crossfade+slide transition for text that updates while the toast stays visible. */
private fun contentSwapTransition() =
    (slideInVertically(animationSpec = tween(180)) { it / 3 } + fadeIn(tween(180)))
        .togetherWith(slideOutVertically(animationSpec = tween(140)) { -it / 3 } + fadeOut(tween(140)))

/**
 * Live status toast shown while market research runs, sliding up from the bottom instead of an
 * opaque spinner. [progress] may still be null right as [visible] flips true — the request just
 * hasn't produced its first event yet — so a generic "Starting research…" label covers that gap.
 */
@Composable
fun ResearchProgressToast(
    visible: Boolean,
    progress: ResearchProgress?,
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
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
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
                    color = MaterialTheme.colorScheme.inversePrimary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    // Each line transitions independently — phase, detail, and the running
                    // counts update on their own schedules (phase changes 2-3 times a run,
                    // detail on every query/page-read, counts even more often), so a shared
                    // AnimatedContent keyed on the whole toast would replay all three every
                    // time only one of them actually changed.
                    AnimatedContent(
                        targetState = progress.phaseLabel(),
                        transitionSpec = { contentSwapTransition() },
                        label = "researchPhase",
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    val detail = progress?.detail
                    if (!detail.isNullOrBlank()) {
                        AnimatedContent(
                            targetState = detail,
                            transitionSpec = { contentSwapTransition() },
                            label = "researchDetail",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val counts = progress?.countsLabel().orEmpty()
                    if (counts.isNotBlank()) {
                        AnimatedContent(
                            targetState = counts,
                            transitionSpec = { contentSwapTransition() },
                            label = "researchCounts",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ResearchProgress?.phaseLabel(): String =
    when (this?.phase) {
        ResearchProgress.Phase.SEARCHING -> "Searching marketplaces…"
        ResearchProgress.Phase.VERIFYING -> "Verifying listings…"
        ResearchProgress.Phase.SYNTHESIZING -> "Analyzing with AI…"
        null -> "Starting research…"
    }

private fun ResearchProgress.countsLabel(): String =
    buildList {
        if (queriesRun > 0) add("$queriesRun ${if (queriesRun == 1) "query" else "queries"}")
        if (resultsFound > 0) add("$resultsFound found")
        if (pagesTarget > 0) add("$pagesConfirmed/$pagesTarget verified")
    }.joinToString(" · ")
