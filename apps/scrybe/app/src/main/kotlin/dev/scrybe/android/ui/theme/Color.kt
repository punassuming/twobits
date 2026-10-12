package dev.scrybe.android.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral palette
val Ink900 = Color(0xFF0D1A2A)
val Ink700 = Color(0xFF4A6880)
val Slate500 = Color(0xFF8AACCB)
val Mist100 = Color(0xFFF0F5FB)
val Mist200 = Color(0xFFD3DEEC)

// Brand accent — light / dark variants
val SignalLight = Color(0xFF1A6FB5)
val SignalDark = Color(0xFF89C7FF)
val EmberLight = Color(0xFFB05020)
val EmberDark = Color(0xFFFFB695)

// Secondary — green in every app, good outcomes only (CC-07). Scrybe's previous teal "Glow"
// secondary mapping is retired; Glow itself is unused now (confirmed zero other call sites).
val GreenLight = Color(0xFF1E8E5A)
val GreenDark = Color(0xFF88D7A8)

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
val DarkSurfVariant = Color(0xFF243443)

// Text
val DarkOnSurface = Color(0xFFE3EAF2)
val DarkOnSurfaceVariant = Color(0xFF8BA5BE)

// Containers (from design)
val DarkPrimaryContainer = Color(0xFF003355)
val DarkOnPrimaryContainer = Color(0xFFC8E6FF)
val DarkSecondaryContainer = Color(0xFF003823)
val DarkOnSecondaryContainer = Color(0xFFBEEFD6)
val DarkTertiaryContainer = Color(0xFF4A1A00)
val DarkOnTertiaryContainer = Color(0xFFFFD8C0)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Outline — subtle borders matching design rgba(255,255,255,0.07) on dark surface
val DarkOutlineVariant = Color(0xFF243443)
val DarkOutline = Color(0xFF3D5269)

// Dark error — named constants instead of ScrybeTheme.kt's previous inline literals, for
// consistency with every other role in that file.
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)

// ── Light scheme supplementary ────────────────────────────────────────────
val LightSurface = Color(0xFFE2EAF4)
val LightPrimaryContainer = Color(0xFFC8E0F6)
val LightOnPrimaryContainer = Color(0xFF001D36)
val LightSecondaryContainer = Color(0xFFBEEFD6)
val LightOnSecondaryContainer = Color(0xFF00391F)
val LightTertiaryContainer = Color(0xFFFFE4D5)
val LightOnTertiaryContainer = Color(0xFF321200)

// Light error (M3 standard)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Light surface hierarchy — highest elevation step above Mist200
val LightSurfHighest = Color(0xFFDBE4EF)

// Light outline — subtle border between Mist100 and Mist200
val LightOutlineVariant = Color(0xFFCBDCEB)
