package dev.scrybe.core.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Values match the shared com.twobits.design.TwoBitsSpacing tokens (screenH/screenV/section/item);
// duplicated here rather than depending on shared/design, since core/common doesn't otherwise
// need that module.
object ScrybeLayoutDefaults {
    val screenHorizontalPadding = 12.dp
    val screenVerticalSpacing = 12.dp
    val sectionPadding = 14.dp
    val sectionSpacing = 8.dp
    val contentMaxWidth = 760.dp
}

fun Modifier.scrybeContentWidth(maxWidth: Dp = ScrybeLayoutDefaults.contentMaxWidth): Modifier = fillMaxWidth().widthIn(max = maxWidth)
