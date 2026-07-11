package com.twobits.pricedrop.ui.settings

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseDelegate
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.CredentialCheck
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderKeyValidator
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.repository.WatchlistRepository
import com.twobits.pricedrop.data.settings.SettingsPrefs
import com.twobits.pricedrop.work.PriceCheckScheduler
import com.twobits.securestore.SharedCredentialId
import com.twobits.securestore.ipc.SharedCredentialClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** Feature-level config presented in the AI Configuration screen. */
data class FeatureState(
    val source: ProviderMode,
    val modelId: String,
    val enabledProviders: Set<String>,
)

data class SettingsUiState(
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val checkFrequencyHours: Int = SettingsPrefs.DEFAULT_CHECK_FREQ_HOURS,
    val wifiOnly: Boolean = false,
    val onlyWhileCharging: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val apiBaseUrl: String = "https://api.twobits.app",
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
) {
    val hasPro: Boolean get() = subscriptionTier == SubscriptionTier.Pro
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: DataStore<Preferences>,
        private val subscriptionRepo: SubscriptionRepository,
        private val providerStore: ProviderSettingsStore,
        private val watchlistRepo: WatchlistRepository,
        billingManager: BillingManager,
        private val credentialClient: SharedCredentialClient,
        private val providerKeyValidator: ProviderKeyValidator,
    ) : ViewModel() {
        private val purchaseDelegate = PurchaseDelegate(billingManager, viewModelScope)

        private val _exportEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val exportEvent: SharedFlow<String> = _exportEvent.asSharedFlow()

        init {
            viewModelScope.launch {
                val readThroughPairs =
                    listOf(
                        PriceDropProvider.OPENAI to SharedCredentialId.OPENAI,
                        PriceDropProvider.WEB_SEARCH to SharedCredentialId.JINA,
                        PriceDropProvider.SHOPPING to SharedCredentialId.SEARCHAPI,
                        PriceDropProvider.SERPER to SharedCredentialId.SERPER,
                        PriceDropProvider.COUPON to SharedCredentialId.COUPON,
                        PriceDropProvider.RAINFOREST to SharedCredentialId.RAINFOREST,
                    )
                readThroughPairs.forEach { (provider, credId) ->
                    if (providerStore.getKey(provider).isBlank()) {
                        credentialClient.readThrough(credId)?.let { sibling ->
                            providerStore.setKey(provider, sibling)
                        }
                    }
                }
            }
        }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                dataStore.data,
                subscriptionRepo.subscriptionTier,
                purchaseDelegate.isPurchasing,
                purchaseDelegate.purchaseError,
            ) { prefs, tier, purchasing, error ->
                SettingsUiState(
                    subscriptionTier = tier,
                    checkFrequencyHours =
                        (prefs[SettingsPrefs.CHECK_FREQ] ?: SettingsPrefs.DEFAULT_CHECK_FREQ_HOURS)
                            .coerceIn(SettingsPrefs.MIN_CHECK_FREQ_HOURS, SettingsPrefs.MAX_CHECK_FREQ_HOURS),
                    wifiOnly = prefs[SettingsPrefs.WIFI_ONLY] ?: false,
                    onlyWhileCharging = prefs[SettingsPrefs.CHARGING_ONLY] ?: false,
                    quietHoursEnabled = prefs[SettingsPrefs.QUIET_HOURS] ?: false,
                    apiBaseUrl = prefs[SettingsPrefs.API_URL] ?: "https://api.twobits.app",
                    isPurchasing = purchasing,
                    purchaseError = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setCheckFrequency(hours: Int) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsPrefs.CHECK_FREQ] = hours }
                val prefs = dataStore.data.first()
                PriceCheckScheduler.schedule(
                    context = context,
                    freqHours = hours,
                    wifiOnly = prefs[SettingsPrefs.WIFI_ONLY] ?: false,
                    chargingOnly = prefs[SettingsPrefs.CHARGING_ONLY] ?: false,
                )
            }
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
                    combine(
                        providerStore.observeMode(p),
                        providerStore.observeKey(p),
                        providerStore.observeValidated(p),
                    ) { mode, key, validated ->
                        // Persisted validity surfaces the "Connected" badge on launch; the
                        // transient validationStates overlay (below) wins while testing.
                        p to ProviderState(mode = mode, key = key, isKeyValid = if (key.isNotBlank() && validated) true else null)
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

        val featureStates: StateFlow<Map<AiFeature, FeatureState>> =
            combine(
                AiFeature.entries.map { f ->
                    combine(
                        providerStore.observeFeatureSource(f),
                        providerStore.observeFeatureModel(f),
                        providerStore.observeFeatureProviders(f),
                    ) { source, model, providers ->
                        f to FeatureState(source, model, providers)
                    }
                },
            ) { pairs ->
                pairs.toMap()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        fun setFeatureSource(
            f: AiFeature,
            mode: ProviderMode,
        ) {
            viewModelScope.launch { providerStore.setFeatureSource(f, mode) }
        }

        fun setFeatureModel(
            f: AiFeature,
            modelId: String,
        ) {
            viewModelScope.launch { providerStore.setFeatureModel(f, modelId) }
        }

        fun toggleFeatureProvider(
            f: AiFeature,
            providerKey: String,
        ) {
            viewModelScope.launch {
                val current =
                    featureStates.value[f]?.enabledProviders
                        ?: f.providers.map { p -> p.key }.toSet()
                val next =
                    if (providerKey in current) {
                        current - providerKey
                    } else {
                        current + providerKey
                    }
                providerStore.setFeatureProviders(f, next)
            }
        }

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
                when (p) {
                    PriceDropProvider.OPENAI -> credentialClient.mirror(SharedCredentialId.OPENAI, key)
                    PriceDropProvider.WEB_SEARCH -> credentialClient.mirror(SharedCredentialId.JINA, key)
                    PriceDropProvider.SHOPPING -> credentialClient.mirror(SharedCredentialId.SEARCHAPI, key)
                    PriceDropProvider.SERPER -> credentialClient.mirror(SharedCredentialId.SERPER, key)
                    PriceDropProvider.COUPON -> credentialClient.mirror(SharedCredentialId.COUPON, key)
                    PriceDropProvider.RAINFOREST -> credentialClient.mirror(SharedCredentialId.RAINFOREST, key)
                }
                val formatCheck = CredentialCheck.check(p, key)
                if (!formatCheck.isValid) {
                    setValidation(p, ProviderValidation(message = formatCheck.message, isValid = false))
                    return@launch
                }
                setValidation(p, ProviderValidation(isValidating = true, message = "Checking connection…"))
                val result = providerKeyValidator.validate(p, key)
                providerStore.setValidated(p, result.isSuccess)
                setValidation(
                    p,
                    result.fold(
                        onSuccess = { msg -> ProviderValidation(message = msg, isValid = true) },
                        onFailure = { err -> ProviderValidation(message = err.message ?: "Validation failed", isValid = false) },
                    ),
                )
            }
        }

        fun testProviderKey(
            p: PriceDropProvider,
            key: String,
        ) {
            val formatCheck = CredentialCheck.check(p, key)
            if (!formatCheck.isValid) {
                setValidation(p, ProviderValidation(message = formatCheck.message, isValid = false))
                return
            }
            setValidation(p, ProviderValidation(isValidating = true, message = "Checking connection…"))
            viewModelScope.launch {
                val result = providerKeyValidator.validate(p, key)
                providerStore.setValidated(p, result.isSuccess)
                setValidation(
                    p,
                    result.fold(
                        onSuccess = { msg -> ProviderValidation(message = msg, isValid = true) },
                        onFailure = { err -> ProviderValidation(message = err.message ?: "Validation failed", isValid = false) },
                    ),
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
            viewModelScope.launch {
                val products = watchlistRepo.observeAll().first()
                val payload = mapOf("watchlist" to products)
                _exportEvent.emit(Gson().toJson(payload))
            }
        }
    }
