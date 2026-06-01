package com.shelfsnap.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light palette ─────────────────────────────────────────
val Primary = Color(0xFF2E7D32)           // Forest green
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFA5D6A7)
val OnPrimaryContainer = Color(0xFF002105)

val Secondary = Color(0xFF558B2F)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD7E8CC)
val OnSecondaryContainer = Color(0xFF131F0D)

val Tertiary = Color(0xFF00695C)          // Teal accent
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFA7F5E7)
val OnTertiaryContainer = Color(0xFF00201C)

val Background = Color(0xFFF9FBF7)
val OnBackground = Color(0xFF1A1C19)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1A1C19)
val SurfaceVariant = Color(0xFFDEE5D8)
val OnSurfaceVariant = Color(0xFF424940)
val Outline = Color(0xFF72796F)
val OutlineVariant = Color(0xFFC2C9BD)

val Error = Color(0xFFB00020)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFCDAD7)
val OnErrorContainer = Color(0xFF410002)

val EstimateLabel = Color(0xFF795548)     // Warm brown — flags estimate fields

// ── Dark palette (Scrybe DS — the "Shelf Snap v2" design example) ──────────
// Forest-green primary on cool dark-blue surfaces, blue secondary, amber estimate.
val DarkPrimary = Color(0xFF81C784)
val DarkOnPrimary = Color(0xFF06351A)
val DarkPrimaryContainer = Color(0xFF1B3D2F)
val DarkOnPrimaryContainer = Color(0xFFC8E6C9)

val DarkSecondary = Color(0xFF89C7FF)
val DarkOnSecondary = Color(0xFF00344F)
val DarkSecondaryContainer = Color(0xFF003A63)
val DarkOnSecondaryContainer = Color(0xFFCDE5FF)

val DarkTertiary = Color(0xFFFFB695)
val DarkOnTertiary = Color(0xFF4A2511)
val DarkTertiaryContainer = Color(0xFF3B2515)
val DarkOnTertiaryContainer = Color(0xFFFFDBC8)

val DarkBackground = Color(0xFF0F1720)
val DarkOnBackground = Color(0xFFE2E8F0)
val DarkSurface = Color(0xFF172431)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkSurfaceVariant = Color(0xFF243443)
val DarkOnSurfaceVariant = Color(0xFF8B9BAB)
val DarkOutline = Color(0xFF3A4A5A)
val DarkOutlineVariant = Color(0xFF243443)

// Tonal surface containers (used by Card, NavigationBar, menus, etc.) tuned to the
// design's layered dark blues: bg < surface < surfaceHigh < surfaceVar.
val DarkSurfaceContainerLowest = Color(0xFF0B121A)
val DarkSurfaceContainerLow = Color(0xFF141F2B)
val DarkSurfaceContainer = Color(0xFF172431)
val DarkSurfaceContainerHigh = Color(0xFF1C2B3B)
val DarkSurfaceContainerHighest = Color(0xFF243443)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF4B1413)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkEstimateLabel = Color(0xFFFFB695) // Amber — flags AI-generated estimates

// ── Condition colors (match design: Excellent green, Good blue, Fair amber, Poor red) ──
val ConditionExcellent = Color(0xFF81C784)
val ConditionGood = Color(0xFF89C7FF)
val ConditionFair = Color(0xFFFFB695)
val ConditionPoor = Color(0xFFFFB4AB)

// ── Selling-platform brand colors (v2 market research / cross-listing) ──
val PlatformEbay = Color(0xFFE53238)
val PlatformMercari = Color(0xFFFF6B6B)
val PlatformOfferUp = Color(0xFF00BFA5)
val PlatformFbMarketplace = Color(0xFF1877F2)
val PlatformCraigslist = Color(0xFF8C6BBF)
