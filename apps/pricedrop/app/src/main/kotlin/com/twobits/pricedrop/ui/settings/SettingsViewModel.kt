package com.twobits.pricedrop.ui.settings

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseCancelledException
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
)

private object Keys {
    val CHECK_FREQ = intPreferencesKey("check_frequency_hours")
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val CHARGING_ONLY = booleanPreferencesKey("charging_only")
    val QUIET_HOURS = booleanPreferencesKey("quiet_hours")
    val API_URL = stringPreferencesKey("api_base_url")
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val subscriptionRepo: SubscriptionRepository,
        private val billingManager: BillingManager,
    ) : ViewModel() {
        private val _isPurchasing = MutableStateFlow(false)
        private val _purchaseError = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                dataStore.data,
                subscriptionRepo.subscriptionTier,
                _isPurchasing,
                _purchaseError,
            ) { prefs, tier, purchasing, error ->
                SettingsUiState(
                    subscriptionTier = tier,
                    checkFrequencyHours = prefs[Keys.CHECK_FREQ] ?: 6,
                    wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
                    onlyWhileCharging = prefs[Keys.CHARGING_ONLY] ?: false,
                    quietHoursEnabled = prefs[Keys.QUIET_HOURS] ?: false,
                    apiBaseUrl = prefs[Keys.API_URL] ?: "https://api.twobits.app",
                    isPurchasing = purchasing,
                    purchaseError = error,
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

        fun startProPurchase(
            activity: Activity,
            plan: String = "monthly",
        ) {
            viewModelScope.launch {
                _isPurchasing.value = true
                _purchaseError.value = null
                val pkg =
                    if (plan == "annual") {
                        billingManager.getAnnualPackage() ?: billingManager.getMonthlyPackage()
                    } else {
                        billingManager.getMonthlyPackage()
                    }
                if (pkg == null) {
                    _purchaseError.value = "Subscription not available — try again shortly."
                    _isPurchasing.value = false
                    return@launch
                }
                billingManager
                    .purchase(activity, pkg)
                    .onFailure { e ->
                        if (e !is PurchaseCancelledException) {
                            _purchaseError.value = e.message ?: "Purchase failed."
                        }
                    }
                _isPurchasing.value = false
            }
        }

        fun restorePurchases() {
            viewModelScope.launch {
                _isPurchasing.value = true
                _purchaseError.value = null
                billingManager
                    .restorePurchases()
                    .onFailure { _purchaseError.value = it.message ?: "Restore failed." }
                _isPurchasing.value = false
            }
        }

        fun dismissPurchaseError() {
            _purchaseError.value = null
        }
    }
