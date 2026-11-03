package dev.scrybe.feature.settings

import com.twobits.design.ThemeMode
import dev.scrybe.core.model.ThemeMode as StoredThemeMode

// The persisted enum stays in :core:model (which has no Compose/design dependency); the UI uses the
// shared one. Both are stored and read by name (SYSTEM/LIGHT/DARK), so the mapping is 1:1.
internal fun StoredThemeMode.toDesignThemeMode(): ThemeMode =
    when (this) {
        StoredThemeMode.SYSTEM -> ThemeMode.SYSTEM
        StoredThemeMode.LIGHT -> ThemeMode.LIGHT
        StoredThemeMode.DARK -> ThemeMode.DARK
    }

internal fun ThemeMode.toStoredThemeMode(): StoredThemeMode =
    when (this) {
        ThemeMode.SYSTEM -> StoredThemeMode.SYSTEM
        ThemeMode.LIGHT -> StoredThemeMode.LIGHT
        ThemeMode.DARK -> StoredThemeMode.DARK
    }
