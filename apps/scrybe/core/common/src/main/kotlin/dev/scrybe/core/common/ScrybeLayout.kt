package dev.scrybe.core.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.twobits.design.TwoBitsLayout
import com.twobits.design.TwoBitsSpacing
import com.twobits.design.twoBitsContentWidth

// Scrybe's names for the shared com.twobits.design tokens, kept so existing call sites don't churn.
object ScrybeLayoutDefaults {
    val screenHorizontalPadding = TwoBitsSpacing.screenH
    val screenVerticalSpacing = TwoBitsSpacing.screenV
    val sectionPadding = TwoBitsSpacing.section
    val sectionSpacing = TwoBitsSpacing.item
    val contentMaxWidth = TwoBitsLayout.contentMaxWidth
}

fun Modifier.scrybeContentWidth(maxWidth: Dp = ScrybeLayoutDefaults.contentMaxWidth): Modifier = twoBitsContentWidth(maxWidth)
