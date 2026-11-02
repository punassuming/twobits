package com.twobits.pricedrop.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.twobits.design.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPrefsTest {
    @Test
    fun `themeMode defaults to system when unset`() {
        assertEquals(ThemeMode.SYSTEM, SettingsPrefs.themeMode(emptyPreferences()))
    }

    @Test
    fun `themeMode reads the persisted mode`() {
        assertEquals(ThemeMode.DARK, SettingsPrefs.themeMode(preferencesOf(SettingsPrefs.THEME_MODE to ThemeMode.DARK.name)))
    }

    @Test
    fun `themeMode falls back to system for an unknown value`() {
        assertEquals(ThemeMode.SYSTEM, SettingsPrefs.themeMode(preferencesOf(SettingsPrefs.THEME_MODE to "sepia")))
    }
}
