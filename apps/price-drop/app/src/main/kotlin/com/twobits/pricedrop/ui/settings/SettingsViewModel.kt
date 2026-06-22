package com.twobits.pricedrop.ui.settings

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseDelegate
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.settings.SettingsPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderState(
    val mode: ProviderMode,
    val key: String,
)

data class SettingsUiState(
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val checkFrequencyHours: Int = 6,
    val wifiOnly: Boolean = false,
    val onlyWhileCharging: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val apiBaseUrl: String = "https://api.twobits.app",
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val subscriptionRepo: SubscriptionRepository,
        private val providerStore: ProviderSettingsStore,
        billingManager: BillingManager,
    ) : ViewModel() {
        private val purchaseDelegate = PurchaseDelegate(billingManager, viewModelScope)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                dataStore.data,
                subscriptionRepo.subscriptionTier,
                purchaseDelegate.isPurchasing,
                purchaseDelegate.purchaseError,
            ) { prefs, tier, purchasing, error ->
                SettingsUiState(
                    subscriptionTier = tier,
                    checkFrequencyHours = prefs[SettingsPrefs.CHECK_FREQ] ?: 6,
                    wifiOnly = prefs[SettingsPrefs.WIFI_ONLY] ?: false,
                    onlyWhileCharging = prefs[SettingsPrefs.CHARGING_ONLY] ?: false,
                    quietHoursEnabled = prefs[SettingsPrefs.QUIET_HOURS] ?: false,
                    apiBaseUrl = prefs[SettingsPrefs.API_URL] ?: "https://api.twobits.app",
                    isPurchasing = purchasing,
                    purchaseError = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setCheckFrequency(hours: Int) {
            viewModelScope.launch { dataStore.edit { it[SettingsPrefs.CHECK_FREQ] = hours } }
        }

        fun setWifiOnly(enabled: Boolean) {
            viewModelScope.launch { dataStore.edit { it[SettingsPrefs.WIFI_ONLY] = enabled } }
        }

        fun setChargingOnly(enabled: Boolean) {
            viewModelScope.launch { dataStore.edit { it[SettingsPrefs.CHARGING_ONLY] = enabled } }
        }

        fun setQuietHours(enabled: Boolean) {
            viewModelScope.launch { dataStore.edit { it[SettingsPrefs.QUIET_HOURS] = enabled } }
        }

        val providerStates: StateFlow<Map<PriceDropProvider, ProviderState>> =
            combine(
                PriceDropProvider.entries.map { p ->
                    combine(providerStore.observeMode(p), providerStore.observeKey(p)) { mode, key ->
                        p to ProviderState(mode, key)
                    }
                },
            ) { pairs ->
                pairs.toMap()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        fun setProviderMode(
            p: PriceDropProvider,
            mode: ProviderMode,
        ) {
            viewModelScope.launch { providerStore.setMode(p, mode) }
        }

        fun setProviderKey(
            p: PriceDropProvider,
            key: String,
        ) {
            viewModelScope.launch { providerStore.setKey(p, key) }
        }

        fun startProPurchase(
            activity: Activity,
            plan: String = "monthly",
        ) = purchaseDelegate.startPurchase(activity, plan)

        fun restorePurchases() = purchaseDelegate.restore()

        fun dismissPurchaseError() = purchaseDelegate.dismissError()

        fun clearSearchHistory() {
            viewModelScope.launch {
                dataStore.edit { prefs ->
                    prefs.remove(SettingsPrefs.SEARCH_HISTORY)
                }
            }
        }

        fun exportData() {
            // Export triggered via share sheet — placeholder for Intent dispatch in the UI layer.
        }
    }
