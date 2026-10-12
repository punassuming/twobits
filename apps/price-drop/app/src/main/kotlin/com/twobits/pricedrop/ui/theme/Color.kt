package com.twobits.pricedrop.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light palette — PriceDrop DS: primary=Coral Red, secondary=Green, tertiary=Amber ──
val Primary = Color(0xFFC0382A) // Coral red light
val OnPrimary = Color(0xFFFFFFFF)

// Container roles are accent tints (9% alpha), not solid colors — matches the mockups (CC-23).
val PrimaryContainer = Primary.copy(alpha = 0.09f)
val OnPrimaryContainer = Color(0xFF410000)

val Secondary = Color(0xFF1E8E5A) // Green light
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Secondary.copy(alpha = 0.09f)
val OnSecondaryContainer = Color(0xFF003820)

val Tertiary = Color(0xFF9A6A00) // Amber light — Pro accent
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Tertiary.copy(alpha = 0.09f)
val OnTertiaryContainer = Color(0xFF3D2E00)

val Background = Color(0xFFF4F7FB)
val OnBackground = Color(0xFF17202A)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF17202A)
val SurfaceVariant = Color(0xFFE8EEF5)
val OnSurfaceVariant = Color(0xFF4A6080)
val Outline = Color(0xFFC8D4E0)
val OutlineVariant = Color(0xFFDDE5EF)

val SurfaceContainerHigh = Color(0xFFEAECF3)
val SurfaceContainer = Color(0xFFEEF0F5)
val SurfaceContainerLow = Color(0xFFF1F3F8)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerHighest = Color(0xFFE5E7EF)

val Error = Color(0xFFCC3333)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410000)

// ── Dark palette — PriceDrop DS: primary=Coral Red, secondary=Green, tertiary=Amber ──
val DarkPrimary = Color(0xFFFF8066) // Coral red dark
val DarkOnPrimary = Color(0xFF1A0600)

// Container roles are accent tints (13% alpha), not solid colors — matches the mockups (CC-23).
val DarkPrimaryContainer = DarkPrimary.copy(alpha = 0.13f)
val DarkOnPrimaryContainer = Color(0xFFFFD9CF)

val DarkSecondary = Color(0xFF88D7A8) // Green dark
val DarkOnSecondary = Color(0xFF003820)
val DarkSecondaryContainer = DarkSecondary.copy(alpha = 0.13f)
val DarkOnSecondaryContainer = Color(0xFFA4F2C5)

val DarkTertiary = Color(0xFFFFD580) // Amber dark — Pro accent
val DarkOnTertiary = Color(0xFF3D2E00)
val DarkTertiaryContainer = DarkTertiary.copy(alpha = 0.13f)
val DarkOnTertiaryContainer = Color(0xFFFFE98B)

val DarkBackground = Color(0xFF0F1318)
val DarkOnBackground = Color(0xFFE8EEF5)
val DarkSurface = Color(0xFF171E27)
val DarkOnSurface = Color(0xFFE8EEF5)
val DarkSurfaceVariant = Color(0xFF243040)
val DarkOnSurfaceVariant = Color(0xFF7A8FA8)
val DarkOutline = Color(0xFF2A3648)
val DarkOutlineVariant = Color(0xFF1E2B3A)

val DarkSurfaceContainerLowest = Color(0xFF0F1318)
val DarkSurfaceContainerLow = Color(0xFF131921)
val DarkSurfaceContainer = Color(0xFF171E27)
val DarkSurfaceContainerHigh = Color(0xFF1C2532)
val DarkSurfaceContainerHighest = Color(0xFF243040)

val DarkError = Color(0xFFFF6B6B)
val DarkOnError = Color(0xFF690000)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
