package com.shelfsnap.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light palette — TwoBits DS: primary=Glow(teal), secondary=Green, tertiary=Ember ──
// Surfaces are teal-tinted (CC-08) — this app's own identity, not the shared Mist/Ink neutrals
// Scrybe/PriceDrop use.
val Primary = Color(0xFF0C7A87) // Glow teal
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFB8E8ED)
val OnPrimaryContainer = Color(0xFF002022)

// Secondary (green — good outcomes only, CC-07). Signal blue moves to ConditionGood below,
// which already had its own independent literal.
val Secondary = Color(0xFF1E8E5A)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFBEEFD6)

// Not specified by the mockups (which don't define dark/light on-container text) — chosen to
// match Scrybe's equivalent on-secondary-container pair; report back if the design system wants
// a different value.
val OnSecondaryContainer = Color(0xFF00391F)

val Tertiary = Color(0xFFB05020) // Ember orange
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFE4D4)
val OnTertiaryContainer = Color(0xFF321200)

val Background = Color(0xFFEDF7F8)
val OnBackground = Color(0xFF061A1C)
val Surface = Color(0xFFDCF0F2)
val OnSurface = Color(0xFF061A1C)
val SurfaceVariant = Color(0xFFC8E8EB)
val OnSurfaceVariant = Color(0xFF2E6E75)

// Tonal surface containers — teal-tinted ramp around Background/Surface, mirroring how the dark
// scheme below is built (Container == Surface, Highest sits just under SurfaceVariant). Only
// SurfaceContainerHigh is specified by the mockups (CC-08); the rest are interpolated so no
// stock Material baseline-purple defaults leak into the light theme.
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFE5F3F5)
val SurfaceContainer = Color(0xFFDCF0F2)
val SurfaceContainerHigh = Color(0xFFD4EDF0)
val SurfaceContainerHighest = Color(0xFFCDEAED)
val Outline = Color(0xFF5FA8B0)
val OutlineVariant = Color(0xFFC0DEE2)

val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

val EstimateLabel = Color(0xFF9B7200) // Amber — flags estimate fields (was brown)

// ── Dark palette — TwoBits DS: primary=Glow(teal), secondary=Green, tertiary=Ember ──
val DarkPrimary = Color(0xFF7DD4DC) // Glow teal dark
val DarkOnPrimary = Color(0xFF003D42)
val DarkPrimaryContainer = Color(0xFF004F57)
val DarkOnPrimaryContainer = Color(0xFFC0F0F4)

val DarkSecondary = Color(0xFF88D7A8)
val DarkOnSecondary = Color(0xFF003823)
val DarkSecondaryContainer = Color(0xFF003823)

// See the light OnSecondaryContainer comment above — same not-specified-by-mockups caveat.
val DarkOnSecondaryContainer = Color(0xFFBEEFD6)

val DarkTertiary = Color(0xFFFFB695) // Ember dark
val DarkOnTertiary = Color(0xFF4A2511)
val DarkTertiaryContainer = Color(0xFF4A1A00)
val DarkOnTertiaryContainer = Color(0xFFFFD8C0)

val DarkBackground = Color(0xFF0B1A1C)
val DarkOnBackground = Color(0xFFDDF4F5)
val DarkSurface = Color(0xFF122127)
val DarkOnSurface = Color(0xFFDDF4F5)
val DarkSurfaceVariant = Color(0xFF1A3038)
val DarkOnSurfaceVariant = Color(0xFF7EB9BF)
val DarkOutline = Color(0xFF2E5059)
val DarkOutlineVariant = Color(0xFF1A3038)

// Tonal surface containers — teal-tinted (CC-08). Lowest/Low aren't specified by the mockups
// (only background/surface/surfaceContainerHigh/surfaceContainerHighest are); interpolated here
// to keep the same relative step pattern the old navy scale had — report back if the design
// system wants different values.
val DarkSurfaceContainerLowest = Color(0xFF081416)
val DarkSurfaceContainerLow = Color(0xFF0E1F21)
val DarkSurfaceContainer = Color(0xFF122127)
val DarkSurfaceContainerHigh = Color(0xFF162B31)
val DarkSurfaceContainerHighest = Color(0xFF1A3038)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkEstimateLabel = Color(0xFFFFD580) // Amber — flags AI-generated estimates (was #FFB695)

// ── Condition colors — aligned with TwoBits DS accent families ──
val ConditionExcellent = Color(0xFF7DD4DC) // Glow teal
val ConditionGood = Color(0xFF89C7FF) // Signal blue
val ConditionFair = Color(0xFFFFB695) // Ember orange
val ConditionPoor = Color(0xFFFFB4AB) // Error red

// ── Selling-platform brand colors ──
val PlatformEbay = Color(0xFFE53238)
val PlatformMercari = Color(0xFFFF6B6B)
val PlatformOfferUp = Color(0xFF00BFA5)
val PlatformFbMarketplace = Color(0xFF1877F2)
val PlatformCraigslist = Color(0xFF8C6BBF)
