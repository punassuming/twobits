package com.shelfsnap.app.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseCancelledException
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageInfo(
    val photosBytes: Long = 0L,
    val dbBytes: Long = 0L,
) {
    val totalBytes: Long get() = photosBytes + dbBytes
}

data class SettingsUiState(
    val savedApiKey: String = "",
    val editApiKey: String = "",
    val isSaved: Boolean = false,
    val isKeyInvalid: Boolean = false,
    val searchProvider: SearchProvider = SearchProvider.NONE,
    val savedSearchApiKey: String = "",
    val editSearchApiKey: String = "",
    val isSearchSaved: Boolean = false,
    val autoAnalyze: Boolean = false,
    val keepPhotos: Boolean = true,
    val storage: StorageInfo = StorageInfo(),
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val billingManager: BillingManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _editKey = MutableStateFlow("")
    private val _isSaved = MutableStateFlow(false)
    private val _isKeyInvalid = MutableStateFlow(false)
    private val _editSearchKey = MutableStateFlow("")
    private val _isSearchSaved = MutableStateFlow(false)
    private val _storage = MutableStateFlow(StorageInfo())
    private val _isPurchasing = MutableStateFlow(false)
    private val _purchaseError = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _storage.value = computeStorage()
            subscriptionRepository.refresh()
        }
    }

    private val coreFlow = combine(
        repository.observeApiKey(),
        _editKey,
        _isSaved,
        _isKeyInvalid,
        subscriptionRepository.subscriptionTier,
    ) { savedKey, editKey, isSaved, isKeyInvalid, tier ->
        CoreState(savedKey, editKey, isSaved, isKeyInvalid, tier)
    }

    private val searchFlow = combine(
        repository.observeSearchProvider(),
        repository.observeSearchApiKey(),
        _editSearchKey,
        _isSearchSaved,
    ) { provider, savedKey, editKey, saved ->
        SearchState(provider, savedKey, editKey, saved)
    }

    private val prefsFlow = combine(
        repository.observeAutoAnalyze(),
        repository.observeKeepPhotos(),
        _storage,
        _isPurchasing,
        _purchaseError,
    ) { autoAnalyze, keepPhotos, storage, purchasing, purchaseError ->
        PrefsState(autoAnalyze, keepPhotos, storage, purchasing, purchaseError)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreFlow,
        searchFlow,
        prefsFlow,
    ) { core, search, prefs ->
        SettingsUiState(
            savedApiKey = core.savedKey,
            editApiKey = core.editKey.ifBlank { core.savedKey },
            isSaved = core.isSaved,
            isKeyInvalid = core.isKeyInvalid,
            searchProvider = search.provider,
            savedSearchApiKey = search.savedKey,
            editSearchApiKey = search.editKey.ifBlank { search.savedKey },
            isSearchSaved = search.saved,
            autoAnalyze = prefs.autoAnalyze,
            keepPhotos = prefs.keepPhotos,
            storage = prefs.storage,
            subscriptionTier = core.tier,
            isPurchasing = prefs.isPurchasing,
            purchaseError = prefs.purchaseError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onApiKeyChange(value: String) {
        _editKey.update { value }
        _isSaved.update { false }
        _isKeyInvalid.update { false }
    }

    fun onSavedShown() {
        _isSaved.update { false }
    }

    fun save() {
        val key = _editKey.value.ifBlank { uiState.value.savedApiKey }.trim()
        if (!ApiKeyValidator.isValid(key)) {
            _isKeyInvalid.update { true }
            return
        }
        viewModelScope.launch {
            repository.saveApiKey(key)
            _isKeyInvalid.update { false }
            _isSaved.update { true }
        }
    }

    fun onSearchProviderChange(provider: SearchProvider) {
        viewModelScope.launch { repository.saveSearchProvider(provider) }
    }

    fun onSearchApiKeyChange(value: String) {
        _editSearchKey.update { value }
        _isSearchSaved.update { false }
    }

    fun saveSearchSettings() {
        val key = _editSearchKey.value.ifBlank { uiState.value.savedSearchApiKey }.trim()
        viewModelScope.launch {
            repository.saveSearchApiKey(key)
            _isSearchSaved.update { true }
        }
    }

    fun onSearchSavedShown() {
        _isSearchSaved.update { false }
    }

    fun onAutoAnalyzeChange(enabled: Boolean) {
        viewModelScope.launch { repository.saveAutoAnalyze(enabled) }
    }

    fun onKeepPhotosChange(enabled: Boolean) {
        viewModelScope.launch { repository.saveKeepPhotos(enabled) }
    }

    fun startProPurchase(activity: Activity) {
        viewModelScope.launch {
            _isPurchasing.value = true
            _purchaseError.value = null
            val pkg = billingManager.getMonthlyPackage()
            if (pkg == null) {
                _purchaseError.value = "Subscription not available — try again shortly."
                _isPurchasing.value = false
                return@launch
            }
            billingManager.purchase(activity, pkg)
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
            billingManager.restorePurchases()
                .onFailure { _purchaseError.value = it.message ?: "Restore failed." }
            _isPurchasing.value = false
        }
    }

    fun dismissPurchaseError() {
        _purchaseError.value = null
    }

    private suspend fun computeStorage(): StorageInfo = withContext(Dispatchers.IO) {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photosBytes = picturesDir
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L
        val dbBytes = listOf("shelf_snap.db", "shelf_snap.db-wal", "shelf_snap.db-shm")
            .sumOf { name ->
                val file = context.getDatabasePath(name)
                if (file.exists()) file.length() else 0L
            }
        StorageInfo(photosBytes = photosBytes, dbBytes = dbBytes)
    }

    private data class CoreState(
        val savedKey: String,
        val editKey: String,
        val isSaved: Boolean,
        val isKeyInvalid: Boolean,
        val tier: SubscriptionTier,
    )

    private data class SearchState(
        val provider: SearchProvider,
        val savedKey: String,
        val editKey: String,
        val saved: Boolean,
    )

    private data class PrefsState(
        val autoAnalyze: Boolean,
        val keepPhotos: Boolean,
        val storage: StorageInfo,
        val isPurchasing: Boolean,
        val purchaseError: String?,
    )
}
