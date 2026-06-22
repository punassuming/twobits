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
import com.twobits.pricedrop.data.provider.CredentialCheck
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.settings.SettingsPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderState(
    val mode: ProviderMode,
    val key: String,
    val isValidating: Boolean = false,
    val validationMessage: String? = null,
    val isKeyValid: Boolean? = null,
)

private data class ProviderValidation(
    val isValidating: Boolean = false,
    val message: String? = null,
    val isValid: Boolean? = null,
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

        // Transient, non-persisted save/test feedback keyed by provider.
        private val validationStates = MutableStateFlow<Map<PriceDropProvider, ProviderValidation>>(emptyMap())

        private val baseStates: Flow<Map<PriceDropProvider, ProviderState>> =
            combine(
                PriceDropProvider.entries.map { p ->
                    combine(providerStore.observeMode(p), providerStore.observeKey(p)) { mode, key ->
                        p to ProviderState(mode, key)
                    }
                },
            ) { pairs ->
                pairs.toMap()
            }

        val providerStates: StateFlow<Map<PriceDropProvider, ProviderState>> =
            combine(baseStates, validationStates) { base, validation ->
                base.mapValues { (p, state) ->
                    val v = validation[p] ?: return@mapValues state
                    state.copy(
                        isValidating = v.isValidating,
                        validationMessage = v.message,
                        isKeyValid = v.isValid,
                    )
                }
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
            viewModelScope.launch {
                providerStore.setKey(p, key)
                val result = CredentialCheck.check(p, key)
                setValidation(
                    p,
                    ProviderValidation(
                        message = if (result.isValid) "Saved" else result.message,
                        isValid = result.isValid,
                    ),
                )
            }
        }

        fun testProviderKey(
            p: PriceDropProvider,
            key: String,
        ) {
            setValidation(p, ProviderValidation(isValidating = true))
            viewModelScope.launch {
                val result = CredentialCheck.check(p, key)
                setValidation(
                    p,
                    ProviderValidation(message = result.message, isValid = result.isValid),
                )
            }
        }

        fun clearProviderKey(p: PriceDropProvider) {
            viewModelScope.launch {
                providerStore.clearKey(p)
                setValidation(p, ProviderValidation())
            }
        }

        private fun setValidation(
            p: PriceDropProvider,
            v: ProviderValidation,
        ) {
            validationStates.value = validationStates.value.toMutableMap().apply { put(p, v) }
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
