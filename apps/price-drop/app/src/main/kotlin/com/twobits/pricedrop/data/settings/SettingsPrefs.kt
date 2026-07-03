package com.twobits.pricedrop.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Preference keys shared between the Settings UI and the background price checker,
 * so the two cannot drift apart on the string names.
 */
object SettingsPrefs {
    val CHECK_FREQ = intPreferencesKey("check_frequency_hours")
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val CHARGING_ONLY = booleanPreferencesKey("charging_only")
    val QUIET_HOURS = booleanPreferencesKey("quiet_hours")
    val API_URL = stringPreferencesKey("api_base_url")
    val SEARCH_HISTORY = stringPreferencesKey("search_history")

    const val DEFAULT_CHECK_FREQ_HOURS = 24
    const val MIN_CHECK_FREQ_HOURS = 4
    const val MAX_CHECK_FREQ_HOURS = 96
    const val DEFAULT_API_URL = "https://api.twobits.app"

    // Quiet-hours window (local time) used when the toggle is enabled.
    const val QUIET_START_HOUR = 22
    const val QUIET_END_HOUR = 8
}
