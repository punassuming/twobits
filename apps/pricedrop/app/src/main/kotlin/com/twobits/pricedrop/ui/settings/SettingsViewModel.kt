package com.twobits.pricedrop.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val checkFrequencyHours: Int = 6,
    val wifiOnly: Boolean = false,
    val onlyWhileCharging: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val apiBaseUrl: String = "https://api.twobits.app",
)

private object Keys {
    val CHECK_FREQ = intPreferencesKey("check_frequency_hours")
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val CHARGING_ONLY = booleanPreferencesKey("charging_only")
    val QUIET_HOURS = booleanPreferencesKey("quiet_hours")
    val API_URL = stringPreferencesKey("api_base_url")
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val subscriptionRepo: SubscriptionRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = dataStore.data.map { prefs ->
        SettingsUiState(
            checkFrequencyHours = prefs[Keys.CHECK_FREQ] ?: 6,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            onlyWhileCharging = prefs[Keys.CHARGING_ONLY] ?: false,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS] ?: false,
            apiBaseUrl = prefs[Keys.API_URL] ?: "https://api.twobits.app",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setCheckFrequency(hours: Int) {
        viewModelScope.launch { dataStore.edit { it[Keys.CHECK_FREQ] = hours } }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[Keys.WIFI_ONLY] = enabled } }
    }

    fun setChargingOnly(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[Keys.CHARGING_ONLY] = enabled } }
    }

    fun setQuietHours(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[Keys.QUIET_HOURS] = enabled } }
    }
}
