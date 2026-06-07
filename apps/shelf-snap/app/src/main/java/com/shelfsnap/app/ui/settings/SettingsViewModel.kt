package com.shelfsnap.app.ui.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.local.LocalModelManager
import com.shelfsnap.app.data.local.LocalModelState
import com.shelfsnap.app.data.model.LocalGemmaModel
import com.shelfsnap.app.data.model.LocalMoondreamModel
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseCancelledException
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
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
    val isVerifyingKey: Boolean = false,
    /** null = not yet tested; true = verified OK; false = test failed */
    val isKeyVerified: Boolean? = null,
    val keyVerifyError: String? = null,
    val searchProvider: SearchProvider = SearchProvider.NONE,
    val savedSearchApiKey: String = "",
    val editSearchApiKey: String = "",
    val isSearchSaved: Boolean = false,
    val visionModel: VisionModel = VisionModel.default,
    val reasoningModel: ReasoningModel = ReasoningModel.default,
    val visionSource: String = "byok",
    val textSource: String = "byok",
    val moondreamStates: Map<LocalMoondreamModel, LocalModelState> = emptyMap(),
    val selectedMoondream: LocalMoondreamModel? = null,
    val gemmaStates: Map<LocalGemmaModel, LocalModelState> = emptyMap(),
    val selectedGemma: LocalGemmaModel? = null,
    val aiConditionDetection: Boolean = true,
    val autoPriceEstimate: Boolean = true,
    val multiPhotoAnalysis: Boolean = false,
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
    private val localModelManager: LocalModelManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _editKey = MutableStateFlow("")
    private val _isSaved = MutableStateFlow(false)
    private val _isKeyInvalid = MutableStateFlow(false)
    private val _isVerifyingKey = MutableStateFlow(false)
    private val _isKeyVerified = MutableStateFlow<Boolean?>(null)
    private val _keyVerifyError = MutableStateFlow<String?>(null)
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

    private val keyVerifyFlow = combine(
        _isVerifyingKey,
        _isKeyVerified,
        _keyVerifyError,
    ) { verifying, verified, error -> KeyVerifyState(verifying, verified, error) }

    private val searchFlow = combine(
        repository.observeSearchProvider(),
        repository.observeSearchApiKey(),
        _editSearchKey,
        _isSearchSaved,
        repository.observeVisionModel(),
    ) { provider, savedKey, editKey, saved, visionModel ->
        SearchState(provider, savedKey, editKey, saved, visionModel)
    }

    private val localModelsFlow = combine(
        localModelManager.moondreamStates,
        localModelManager.selectedMoondream,
        localModelManager.gemmaStates,
        localModelManager.selectedGemma,
    ) { moondreamStates, selectedMoondream, gemmaStates, selectedGemma ->
        LocalModelsState(moondreamStates, selectedMoondream, gemmaStates, selectedGemma)
    }

    private val modelsFlow = combine(
        repository.observeVisionModel(),
        repository.observeReasoningModel(),
        combine(
            repository.observeVisionSource(),
            repository.observeTextSource(),
        ) { vs, ts -> vs to ts },
        localModelsFlow,
    ) { vision, reasoning, (visionSource, textSource), localModels ->
        ModelsState(vision, reasoning, visionSource, textSource, localModels)
    }

    private val prefsFlow = combine(
        repository.observeAutoAnalyze(),
        repository.observeKeepPhotos(),
        combine(
            repository.observeAiConditionDetection(),
            repository.observeAutoPriceEstimate(),
            repository.observeMultiPhotoAnalysis(),
        ) { a, b, c -> Triple(a, b, c) },
        _storage,
        combine(_isPurchasing, _purchaseError) { p, e -> p to e },
    ) { autoAnalyze, keepPhotos, (conditionDetection, priceEstimate, multiPhoto), storage, (purchasing, purchaseError) ->
        PrefsState(autoAnalyze, keepPhotos, conditionDetection, priceEstimate, multiPhoto, storage, purchasing, purchaseError)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreFlow,
        searchFlow,
        prefsFlow,
        keyVerifyFlow,
        modelsFlow,
    ) { core, search, prefs, keyVerify, models ->
        SettingsUiState(
            savedApiKey = core.savedKey,
            editApiKey = core.editKey.ifBlank { core.savedKey },
            isSaved = core.isSaved,
            isKeyInvalid = core.isKeyInvalid,
            isVerifyingKey = keyVerify.isVerifying,
            isKeyVerified = keyVerify.isVerified,
            keyVerifyError = keyVerify.error,
            searchProvider = search.provider,
            savedSearchApiKey = search.savedKey,
            editSearchApiKey = search.editKey.ifBlank { search.savedKey },
            isSearchSaved = search.saved,
            visionModel = models.visionModel,
            reasoningModel = models.reasoningModel,
            visionSource = models.visionSource,
            textSource = models.textSource,
            moondreamStates = models.localModels.moondreamStates,
            selectedMoondream = models.localModels.selectedMoondream,
            gemmaStates = models.localModels.gemmaStates,
            selectedGemma = models.localModels.selectedGemma,
            aiConditionDetection = prefs.aiConditionDetection,
            autoPriceEstimate = prefs.autoPriceEstimate,
            multiPhotoAnalysis = prefs.multiPhotoAnalysis,
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
        _isKeyVerified.update { null }
        _keyVerifyError.update { null }
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
            // Test the key immediately after saving
            _isVerifyingKey.update { true }
            _isKeyVerified.update { null }
            _keyVerifyError.update { null }
            val result = repository.testApiKey()
            _isVerifyingKey.update { false }
            if (result.isSuccess) {
                _isKeyVerified.update { true }
            } else {
                _isKeyVerified.update { false }
                _keyVerifyError.update { result.exceptionOrNull()?.message }
            }
        }
    }

    fun onVisionModelChange(model: VisionModel) {
        viewModelScope.launch { repository.saveVisionModel(model) }
    }

    fun onReasoningModelChange(model: ReasoningModel) {
        viewModelScope.launch { repository.saveReasoningModel(model) }
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

    fun onVisionSourceChange(source: String) {
        viewModelScope.launch { repository.saveVisionSource(source) }
    }

    fun onTextSourceChange(source: String) {
        viewModelScope.launch { repository.saveTextSource(source) }
    }

    fun onAiConditionDetectionChange(enabled: Boolean) {
        viewModelScope.launch { repository.saveAiConditionDetection(enabled) }
    }

    fun onAutoPriceEstimateChange(enabled: Boolean) {
        viewModelScope.launch { repository.saveAutoPriceEstimate(enabled) }
    }

    fun onMultiPhotoAnalysisChange(enabled: Boolean) {
        viewModelScope.launch { repository.saveMultiPhotoAnalysis(enabled) }
    }

    fun importMoondream(uri: Uri, model: LocalMoondreamModel) {
        viewModelScope.launch { localModelManager.importMoondream(uri, model) }
    }

    fun deleteMoondream(model: LocalMoondreamModel) {
        localModelManager.deleteMoondream(model)
    }

    fun selectMoondream(model: LocalMoondreamModel) {
        localModelManager.selectMoondream(model)
    }

    fun importGemma(uri: Uri, model: LocalGemmaModel) {
        viewModelScope.launch { localModelManager.importGemma(uri, model) }
    }

    fun deleteGemma(model: LocalGemmaModel) {
        localModelManager.deleteGemma(model)
    }

    fun selectGemma(model: LocalGemmaModel) {
        localModelManager.selectGemma(model)
    }

    fun clearApiKey() {
        viewModelScope.launch {
            repository.saveApiKey("")
            _editKey.update { "" }
            _isKeyVerified.update { null }
            _keyVerifyError.update { null }
        }
    }

    fun testApiKey() {
        viewModelScope.launch {
            val key = _editKey.value.ifBlank { uiState.value.savedApiKey }.trim()
            if (key.isBlank()) return@launch
            _isVerifyingKey.update { true }
            _isKeyVerified.update { null }
            _keyVerifyError.update { null }
            val result = repository.testApiKey(key)
            _isVerifyingKey.update { false }
            if (result.isSuccess) {
                _isKeyVerified.update { true }
            } else {
                _isKeyVerified.update { false }
                _keyVerifyError.update { result.exceptionOrNull()?.message }
            }
        }
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

    private data class KeyVerifyState(
        val isVerifying: Boolean,
        val isVerified: Boolean?,
        val error: String?,
    )

    private data class SearchState(
        val provider: SearchProvider,
        val savedKey: String,
        val editKey: String,
        val saved: Boolean,
        val visionModel: VisionModel,
    )

    private data class LocalModelsState(
        val moondreamStates: Map<LocalMoondreamModel, LocalModelState>,
        val selectedMoondream: LocalMoondreamModel?,
        val gemmaStates: Map<LocalGemmaModel, LocalModelState>,
        val selectedGemma: LocalGemmaModel?,
    )

    private data class ModelsState(
        val visionModel: VisionModel,
        val reasoningModel: ReasoningModel,
        val visionSource: String,
        val textSource: String,
        val localModels: LocalModelsState,
    )

    private data class PrefsState(
        val autoAnalyze: Boolean,
        val keepPhotos: Boolean,
        val aiConditionDetection: Boolean,
        val autoPriceEstimate: Boolean,
        val multiPhotoAnalysis: Boolean,
        val storage: StorageInfo,
        val isPurchasing: Boolean,
        val purchaseError: String?,
    )
}
