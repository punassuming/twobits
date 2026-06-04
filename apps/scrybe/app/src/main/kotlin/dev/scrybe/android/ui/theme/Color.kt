package dev.scrybe.android.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral palette
val Ink900 = Color(0xFF17202A)
val Ink700 = Color(0xFF304253)
val Slate500 = Color(0xFF6D7D8B)
val Mist100 = Color(0xFFF4F7FB)
val Mist200 = Color(0xFFE8EEF5)

// Brand accent — light / dark variants
val SignalLight = Color(0xFF005B99)
val SignalDark = Color(0xFF89C7FF)
val GlowLight = Color(0xFF1A7F8A)
val GlowDark = Color(0xFF7DD4DC)
val EmberLight = Color(0xFFB85C38)
val EmberDark = Color(0xFFFFB695)

// ── Dark scheme surface hierarchy (from design SCRYBE_C) ──────────────────
// background: deepest canvas          #0F1720
// surfaceContainerLow: recessed       #111C27
// surface / surfaceContainer: base    #172431
// surfaceContainerHigh: raised card   #1C2B3B  ← ScrybeSectionCard default
// surfaceContainerHighest / surfVar:  #243443
val DarkBg = Color(0xFF0F1720)
val DarkSurf = Color(0xFF172431)
val DarkSurfLow = Color(0xFF111C27)
val DarkSurfHigh = Color(0xFF1C2B3B)
val DarkSurfHighest = Color(0xFF243443)
val DarkSurfVariant = Color(0xFF1D2E3F)

// Text
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkOnSurfaceVariant = Color(0xFF8B9BAB)

// Containers (from design)
val DarkPrimaryContainer = Color(0xFF003A63)
val DarkOnPrimaryContainer = Color(0xFFC8E6FF)
val DarkSecondaryContainer = Color(0xFF143335)
val DarkOnSecondaryContainer = Color(0xFFC0F0F4)
val DarkTertiaryContainer = Color(0xFF3B2515)
val DarkOnTertiaryContainer = Color(0xFFFFD8C0)
val DarkErrorContainer = Color(0xFF4B1413)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Outline — subtle borders matching design rgba(255,255,255,0.07) on dark surface
val DarkOutlineVariant = Color(0xFF1E2E3D)
val DarkOutline = Color(0xFF4A6278)

// ── Light scheme supplementary ────────────────────────────────────────────
val LightSurface = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD0E8FF)
val LightOnPrimaryContainer = Color(0xFF001D36)
val LightSecondaryContainer = Color(0xFFBEE8EC)
val LightOnSecondaryContainer = Color(0xFF002022)
val LightTertiaryContainer = Color(0xFFFFDBCA)
val LightOnTertiaryContainer = Color(0xFF321200)

// Light error (M3 standard)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Light surface hierarchy — highest elevation step above Mist200
val LightSurfHighest = Color(0xFFDAE3EE)

// Light outline — subtle border between Mist100 and Mist200
val LightOutlineVariant = Color(0xFFC9D6E3)
