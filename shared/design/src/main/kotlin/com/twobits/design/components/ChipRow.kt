package com.twobits.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontally scrollable chip row with fading edges. Every app's filter/suggestion chip rows
 * previously clipped chips hard at the screen edge with nothing signalling that more options are
 * offscreen; this draws a gradient scrim over whichever edge still has content beyond it, and it
 * disappears once the row is scrolled to that end. Chips scroll under stable [horizontalPadding]
 * insets (LazyRow-contentPadding behavior). Pass the container's background as [fadeColor] when
 * the row does not sit directly on `colorScheme.background`.
 */
@Composable
fun AppChipRow(
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.background,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        val startFade by animateFloatAsState(
            targetValue = if (scrollState.canScrollBackward) 1f else 0f,
            label = "chip-row-start-fade",
        )
        val endFade by animateFloatAsState(
            targetValue = if (scrollState.canScrollForward) 1f else 0f,
            label = "chip-row-end-fade",
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(FADE_WIDTH)
                    .alpha(startFade)
                    .background(Brush.horizontalGradient(listOf(fadeColor, fadeColor.copy(alpha = 0f)))),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(FADE_WIDTH)
                    .alpha(endFade)
                    .background(Brush.horizontalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor))),
        )
    }
}

private val FADE_WIDTH = 28.dp
