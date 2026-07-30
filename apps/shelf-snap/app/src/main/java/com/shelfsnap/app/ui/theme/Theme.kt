package com.shelfsnap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.twobits.design.ThemeMode
import com.twobits.design.TwoBitsShapes
import com.twobits.design.TwoBitsTypography

/** Composition-local that resolves to the correct EstimateLabel colour for the active theme. */
val LocalEstimateLabel = staticCompositionLocalOf { EstimateLabel }

private val LightColorScheme =
    lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = OnSecondaryContainer,
        tertiary = Tertiary,
        onTertiary = OnTertiary,
        tertiaryContainer = TertiaryContainer,
        onTertiaryContainer = OnTertiaryContainer,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        outlineVariant = OutlineVariant,
        error = Error,
        onError = OnError,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        // Undefined here, these silently fall back to Material3's stock baseline-purple
        // defaults — unrelated to this app's teal/blue palette in either theme. A dark chip
        // against the light theme, borrowing the dark theme's brighter primary as the accent
        // that reads against it (mirrors ScrybeTheme's inverse-role derivation).
        inverseSurface = OnSurface,
        inverseOnSurface = Background,
        inversePrimary = DarkPrimary,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        // A light chip against the dark theme, borrowing the light theme's primary as the
        // accent that reads against it — see LightColorScheme's inverse roles above.
        inverseSurface = DarkOnSurface,
        inverseOnSurface = DarkBackground,
        inversePrimary = Primary,
    )

@Composable
fun ShelfSnapTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val estimateLabelColor = if (darkTheme) DarkEstimateLabel else EstimateLabel

    CompositionLocalProvider(LocalEstimateLabel provides estimateLabelColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TwoBitsTypography,
            shapes = TwoBitsShapes,
            content = content,
        )
    }
}
