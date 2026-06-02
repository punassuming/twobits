package com.twobits.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.twobits.design.R

@OptIn(ExperimentalTextApi::class)
val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.dm_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.dm_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.dm_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val Default = Typography()

val TwoBitsTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = DmSans),
    displayMedium = Default.displayMedium.copy(fontFamily = DmSans),
    displaySmall = Default.displaySmall.copy(fontFamily = DmSans),
    headlineLarge = Default.headlineLarge.copy(fontFamily = DmSans),
    headlineMedium = Default.headlineMedium.copy(fontFamily = DmSans),
    headlineSmall = Default.headlineSmall.copy(fontFamily = DmSans),
    titleLarge = Default.titleLarge.copy(fontFamily = DmSans),
    titleMedium = Default.titleMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = DmSans),
    bodyLarge = Default.bodyLarge.copy(fontFamily = DmSans),
    bodyMedium = Default.bodyMedium.copy(fontFamily = DmSans),
    bodySmall = Default.bodySmall.copy(fontFamily = DmSans),
    labelLarge = Default.labelLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
    labelMedium = Default.labelMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
)
