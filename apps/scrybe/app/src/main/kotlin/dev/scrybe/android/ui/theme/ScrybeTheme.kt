package dev.scrybe.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.twobits.design.TwoBitsShapes
import com.twobits.design.TwoBitsTypography

private val DarkColorScheme =
    darkColorScheme(
        // Primary (signal blue)
        primary = SignalDark,
        onPrimary = Ink900,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        // Secondary (green — good outcomes only, CC-07)
        secondary = GreenDark,
        onSecondary = Ink900,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        // Tertiary (ember peach)
        tertiary = EmberDark,
        onTertiary = Ink900,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        // Error
        error = DarkError,
        onError = DarkOnError,
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
        secondary = GreenLight,
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
        typography = TwoBitsTypography,
        shapes = TwoBitsShapes,
        content = content,
    )
}
