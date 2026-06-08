package dev.scrybe.core.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ScrybeLayoutDefaults {
    val screenHorizontalPadding = 16.dp
    val screenVerticalSpacing = 14.dp
    val sectionPadding = 16.dp
    val sectionSpacing = 10.dp
    val contentMaxWidth = 760.dp
}

fun Modifier.scrybeContentWidth(maxWidth: Dp = ScrybeLayoutDefaults.contentMaxWidth): Modifier =
    fillMaxWidth().widthIn(max = maxWidth)
