package com.shelfsnap.app.ui.components

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
import androidx.compose.ui.unit.dp

/** Shared crossfade+slide transition for text that updates while the toast stays visible. */
private fun contentSwapTransition() =
    (slideInVertically(animationSpec = tween(180)) { it / 3 } + fadeIn(tween(180)))
        .togetherWith(slideOutVertically(animationSpec = tween(140)) { -it / 3 } + fadeOut(tween(140)))

/**
 * Live status toast shown while local (on-device Gemma) listing or vision analysis runs, sliding
 * up from the bottom of the screen — same pattern as [com.shelfsnap.app.ui.itemdetail.ResearchProgressToast]
 * and Scrybe's equivalent transcription-progress toast. Lives at the app level (see
 * [com.shelfsnap.app.ui.navigation.AppNavigation]), not any one screen, since analysis can be
 * triggered from the Camera screen right after capture or an item's detail screen, and keeps
 * running if the user navigates elsewhere while it's in progress.
 *
 * [otherActiveCount] can be nonzero — e.g. `ItemDetailViewModel.refineAllListings()` refines
 * every draft platform's listing concurrently, one `LocalListingService.refine()` call per
 * platform — so this shows one label plus how many others are also currently running, not a
 * queue position.
 */
@Composable
fun LocalAnalysisProgressToast(
    label: String?,
    otherActiveCount: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = label != null,
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
                    AnimatedContent(
                        targetState = label ?: "",
                        transitionSpec = { contentSwapTransition() },
                        label = "localAnalysisLabel",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (otherActiveCount > 0) {
                        AnimatedContent(
                            targetState = otherActiveCount,
                            transitionSpec = { contentSwapTransition() },
                            label = "localAnalysisOtherCount",
                        ) { count ->
                            Text(
                                text = "and $count more also running",
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
