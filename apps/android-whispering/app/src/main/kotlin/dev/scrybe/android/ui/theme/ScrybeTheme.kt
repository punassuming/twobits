package dev.scrybe.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = SignalDark,
        onPrimary = Ink900,
        secondary = GlowDark,
        tertiary = EmberDark,
        background = ColorTokens.darkBackground,
        surface = ColorTokens.darkSurface,
        surfaceVariant = ColorTokens.darkSurfaceVariant,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SignalLight,
        onPrimary = Mist100,
        secondary = GlowLight,
        tertiary = EmberLight,
        background = Mist100,
        surface = ColorTokens.lightSurface,
        surfaceVariant = Mist200,
    )

@Composable
fun ScrybeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScrybeTypography,
        shapes = ScrybeShapes,
        content = content,
    )
}

private object ColorTokens {
    val lightSurface = Color(0xFFFFFFFF)
    val darkBackground = Color(0xFF0F1720)
    val darkSurface = Color(0xFF172431)
    val darkSurfaceVariant = Color(0xFF243443)
}
