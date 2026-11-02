package com.twobits.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TwoBitsLayout {
    /**
     * Readable maximum width for screen content. On phones this never kicks in; on tablets and
     * foldables it stops lists and forms stretching edge to edge. Center the constrained content
     * in its parent (e.g. `Alignment.TopCenter` / `horizontalAlignment = CenterHorizontally`).
     */
    val contentMaxWidth = 760.dp
}

/** Fills the available width up to [TwoBitsLayout.contentMaxWidth]. */
fun Modifier.twoBitsContentWidth(maxWidth: Dp = TwoBitsLayout.contentMaxWidth): Modifier = fillMaxWidth().widthIn(max = maxWidth)
