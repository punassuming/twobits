package dev.scrybe.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        // Primary (signal blue)
        primary = SignalDark,
        onPrimary = Ink900,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        // Secondary (glow green)
        secondary = GlowDark,
        onSecondary = Ink900,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        // Tertiary (ember peach)
        tertiary = EmberDark,
        onTertiary = Ink900,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        // Error
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        // Background / canvas
        background = DarkBg,
        onBackground = DarkOnSurface,
        // Surface hierarchy
        surface = DarkSurf,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainer = DarkSurf,
        surfaceContainerLow = DarkSurfLow,
        surfaceContainerHigh = DarkSurfHigh,
        surfaceContainerHighest = DarkSurfHighest,
        // Outlines / borders
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        // Inverse
        inverseSurface = DarkOnSurface,
        inverseOnSurface = DarkBg,
        inversePrimary = SignalLight,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SignalLight,
        onPrimary = Mist100,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        secondary = GlowLight,
        onSecondary = Mist100,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = EmberLight,
        onTertiary = Mist100,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        // Error
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        // Background / canvas
        background = Mist100,
        onBackground = Ink900,
        // Surface hierarchy
        surface = LightSurface,
        onSurface = Ink900,
        surfaceVariant = Mist200,
        onSurfaceVariant = Ink700,
        surfaceContainer = Mist100,
        surfaceContainerLow = LightSurface,
        surfaceContainerHigh = Mist200,
        surfaceContainerHighest = LightSurfHighest,
        // Outlines / borders
        outline = Slate500,
        outlineVariant = LightOutlineVariant,
        // Inverse
        inverseSurface = Ink900,
        inverseOnSurface = Mist100,
        inversePrimary = SignalDark,
    )

@Composable
fun ScrybeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScrybeTypography,
        shapes = ScrybeShapes,
        content = content,
    )
}
