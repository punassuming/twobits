package com.shelfsnap.app.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.local.LocalModelManager
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.remote.search.BraveSearchService
import com.shelfsnap.app.data.remote.search.JinaAiSearchService
import com.shelfsnap.app.data.remote.search.SearchApiService
import com.shelfsnap.app.data.remote.search.SerperSearchService
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseDelegate
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.securestore.SharedCredentialId
import com.twobits.securestore.ipc.SharedCredentialClient
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
    val jinaSearchEnabled: Boolean = true,
    val braveSearchEnabled: Boolean = false,
    val savedJinaApiKey: String = "",
    val editJinaApiKey: String = "",
    val savedBraveApiKey: String = "",
    val editBraveApiKey: String = "",
    val isSearchSaved: Boolean = false,
    val isJinaTesting: Boolean = false,
    val jinaTestResult: Boolean? = null,
    val jinaTestMessage: String? = null,
    val isBraveTesting: Boolean = false,
    val braveTestResult: Boolean? = null,
    val braveTestMessage: String? = null,
    val searchapiSearchEnabled: Boolean = true,
    val savedSearchapiApiKey: String = "",
    val editSearchapiApiKey: String = "",
    val isSearchapiTesting: Boolean = false,
    val searchapiTestResult: Boolean? = null,
    val searchapiTestMessage: String? = null,
    val serperSearchEnabled: Boolean = false,
    val savedSerperApiKey: String = "",
    val editSerperApiKey: String = "",
    val isSerperTesting: Boolean = false,
    val serperTestResult: Boolean? = null,
    val serperTestMessage: String? = null,
    val visionModel: VisionModel = VisionModel.default,
    val reasoningModel: ReasoningModel = ReasoningModel.default,
    val visionSource: String = "byok",
    val textSource: String = "byok",
    val listingSource: String = "byok",
    val llmStates: Map<LocalLlmModel, LocalModelState> = emptyMap(),
    val selectedLlm: LocalLlmModel? = null,
    val aiConditionDetection: Boolean = true,
    val autoPriceEstimate: Boolean = true,
    val multiPhotoAnalysis: Boolean = false,
    val autoAnalyze: Boolean = false,
    val storage: StorageInfo = StorageInfo(),
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: ItemRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val billingManager: BillingManager,
        private val localModelManager: LocalModelManager,
        private val jinaSearchService: JinaAiSearchService,
        private val braveSearchService: BraveSearchService,
        private val searchapiService: SearchApiService,
        private val serperService: SerperSearchService,
        @ApplicationContext private val context: Context,
        private val credentialClient: SharedCredentialClient,
    ) : ViewModel() {
        private val _editKey = MutableStateFlow("")
        private val _isSaved = MutableStateFlow(false)
        private val _isKeyInvalid = MutableStateFlow(false)
        private val _isVerifyingKey = MutableStateFlow(false)
        private val _isKeyVerified = MutableStateFlow<Boolean?>(null)
        private val _keyVerifyError = MutableStateFlow<String?>(null)
        private val _editJinaKey = MutableStateFlow("")
        private val _editBraveKey = MutableStateFlow("")
        private val _isSearchSaved = MutableStateFlow(false)
        private val _isJinaTesting = MutableStateFlow(false)
        private val _jinaTestResult = MutableStateFlow<Boolean?>(null)
        private val _jinaTestMessage = MutableStateFlow<String?>(null)
        private val _isBraveTesting = MutableStateFlow(false)
        private val _braveTestResult = MutableStateFlow<Boolean?>(null)
        private val _braveTestMessage = MutableStateFlow<String?>(null)
        private val _editSearchapiKey = MutableStateFlow("")
        private val _isSearchapiTesting = MutableStateFlow(false)
        private val _searchapiTestResult = MutableStateFlow<Boolean?>(null)
        private val _searchapiTestMessage = MutableStateFlow<String?>(null)
        private val _editSerperKey = MutableStateFlow("")
        private val _isSerperTesting = MutableStateFlow(false)
        private val _serperTestResult = MutableStateFlow<Boolean?>(null)
        private val _serperTestMessage = MutableStateFlow<String?>(null)
        private val _storage = MutableStateFlow(StorageInfo())
        private val purchaseDelegate = PurchaseDelegate(billingManager, viewModelScope)

        init {
            viewModelScope.launch {
                _storage.value = computeStorage()
                subscriptionRepository.refresh()
                if (repository.getApiKey().isBlank()) {
                    credentialClient.readThrough(SharedCredentialId.OPENAI)?.let { sibling ->
                        repository.saveApiKey(sibling)
                    }
                }
                if (repository.getJinaApiKey().isBlank()) {
                    credentialClient.readThrough(SharedCredentialId.JINA)?.let { sibling ->
                        repository.saveJinaApiKey(sibling)
                    }
                }
                if (repository.getBraveApiKey().isBlank()) {
                    credentialClient.readThrough(SharedCredentialId.BRAVE)?.let { sibling ->
                        repository.saveBraveApiKey(sibling)
                    }
                }
                if (repository.getSearchapiApiKey().isBlank()) {
                    credentialClient.readThrough(SharedCredentialId.SEARCHAPI)?.let { sibling ->
                        repository.saveSearchapiApiKey(sibling)
                    }
                }
                if (repository.getSerperApiKey().isBlank()) {
                    credentialClient.readThrough(SharedCredentialId.SERPER)?.let { sibling ->
                        repository.saveSerperApiKey(sibling)
                    }
                }
            }
        }

        private val coreFlow =
            combine(
                repository.observeApiKey(),
                _editKey,
                _isSaved,
                _isKeyInvalid,
                subscriptionRepository.subscriptionTier,
            ) { savedKey, editKey, isSaved, isKeyInvalid, tier ->
                CoreState(savedKey, editKey, isSaved, isKeyInvalid, tier)
            }

        private val keyVerifyFlow =
            combine(
                _isVerifyingKey,
                _isKeyVerified,
                _keyVerifyError,
            ) { verifying, verified, error -> KeyVerifyState(verifying, verified, error) }

        private val jinaTestFlow =
            combine(_isJinaTesting, _jinaTestResult, _jinaTestMessage) { testing, result, message ->
                SearchTestState(testing, result, message)
            }

        private val braveTestFlow =
            combine(_isBraveTesting, _braveTestResult, _braveTestMessage) { testing, result, message ->
                SearchTestState(testing, result, message)
            }

        private val searchapiTestFlow =
            combine(_isSearchapiTesting, _searchapiTestResult, _searchapiTestMessage) { testing, result, message ->
                SearchTestState(testing, result, message)
            }

        private val searchapiGroupFlow =
            combine(
                repository.observeSearchapiSearchEnabled(),
                repository.observeSearchapiApiKey(),
                _editSearchapiKey,
                searchapiTestFlow,
            ) { enabled, saved, edit, test -> SearchApiGroup(enabled, saved, edit, test) }

        private val serperTestFlow =
            combine(_isSerperTesting, _serperTestResult, _serperTestMessage) { testing, result, message ->
                SearchTestState(testing, result, message)
            }

        private val serperGroupFlow =
            combine(
                repository.observeSerperSearchEnabled(),
                repository.observeSerperApiKey(),
                _editSerperKey,
                serperTestFlow,
            ) { enabled, saved, edit, test -> SearchApiGroup(enabled, saved, edit, test) }

        // combine() tops out at 5 flows directly — searchapi + serper are nested into one pair
        // so the outer combine below stays within that limit.
        private val extraProvidersFlow =
            combine(searchapiGroupFlow, serperGroupFlow) { sapi, serper -> sapi to serper }

        private val searchFlow =
            combine(
                combine(
                    repository.observeJinaSearchEnabled(),
                    repository.observeBraveSearchEnabled(),
                ) { jinaEnabled, braveEnabled -> jinaEnabled to braveEnabled },
                combine(repository.observeJinaApiKey(), _editJinaKey) { saved, edit -> saved to edit },
                combine(repository.observeBraveApiKey(), _editBraveKey) { saved, edit -> saved to edit },
                combine(_isSearchSaved, jinaTestFlow, braveTestFlow) { saved, jina, brave -> Triple(saved, jina, brave) },
                extraProvidersFlow,
            ) { (jinaEnabled, braveEnabled), (savedJina, editJina), (savedBrave, editBrave), (saved, jinaTest, braveTest), (sapi, serper) ->
                SearchState(
                    jinaEnabled,
                    braveEnabled,
                    savedJina,
                    editJina,
                    savedBrave,
                    editBrave,
                    saved,
                    jinaTest,
                    braveTest,
                    sapi.enabled,
                    sapi.savedKey,
                    sapi.editKey,
                    sapi.test,
                    serper.enabled,
                    serper.savedKey,
                    serper.editKey,
                    serper.test,
                )
            }

        private val localModelsFlow =
            combine(
                localModelManager.llmStates,
                localModelManager.selectedLlm,
            ) { llmStates, selectedLlm ->
                LocalModelsState(llmStates, selectedLlm)
            }

        private val modelsFlow =
            combine(
                repository.observeVisionModel(),
                repository.observeReasoningModel(),
                combine(
                    repository.observeVisionSource(),
                    repository.observeTextSource(),
                    repository.observeListingSource(),
                ) { vs, ts, ls -> Triple(vs, ts, ls) },
                localModelsFlow,
            ) { vision, reasoning, (visionSource, textSource, listingSource), localModels ->
                ModelsState(vision, reasoning, visionSource, textSource, listingSource, localModels)
            }

        private val prefsFlow =
            combine(
                repository.observeAutoAnalyze(),
                combine(
                    repository.observeAiConditionDetection(),
                    repository.observeAutoPriceEstimate(),
                    repository.observeMultiPhotoAnalysis(),
                ) { a, b, c -> Triple(a, b, c) },
                _storage,
                combine(purchaseDelegate.isPurchasing, purchaseDelegate.purchaseError) { p, e -> p to e },
            ) { autoAnalyze, (conditionDetection, priceEstimate, multiPhoto), storage, (purchasing, purchaseError) ->
                PrefsState(autoAnalyze, conditionDetection, priceEstimate, multiPhoto, storage, purchasing, purchaseError)
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
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
                    jinaSearchEnabled = search.jinaEnabled,
                    braveSearchEnabled = search.braveEnabled,
                    savedJinaApiKey = search.savedJinaKey,
                    editJinaApiKey = search.editJinaKey.ifBlank { search.savedJinaKey },
                    savedBraveApiKey = search.savedBraveKey,
                    editBraveApiKey = search.editBraveKey.ifBlank { search.savedBraveKey },
                    isSearchSaved = search.saved,
                    isJinaTesting = search.jinaTest.isTesting,
                    jinaTestResult = search.jinaTest.result,
                    jinaTestMessage = search.jinaTest.message,
                    isBraveTesting = search.braveTest.isTesting,
                    braveTestResult = search.braveTest.result,
                    braveTestMessage = search.braveTest.message,
                    searchapiSearchEnabled = search.searchapiEnabled,
                    savedSearchapiApiKey = search.savedSearchapiKey,
                    editSearchapiApiKey = search.editSearchapiKey.ifBlank { search.savedSearchapiKey },
                    isSearchapiTesting = search.searchapiTest.isTesting,
                    searchapiTestResult = search.searchapiTest.result,
                    searchapiTestMessage = search.searchapiTest.message,
                    serperSearchEnabled = search.serperEnabled,
                    savedSerperApiKey = search.savedSerperKey,
                    editSerperApiKey = search.editSerperKey.ifBlank { search.savedSerperKey },
                    isSerperTesting = search.serperTest.isTesting,
                    serperTestResult = search.serperTest.result,
                    serperTestMessage = search.serperTest.message,
                    visionModel = models.visionModel,
                    reasoningModel = models.reasoningModel,
                    visionSource = models.visionSource,
                    textSource = models.textSource,
                    listingSource = models.listingSource,
                    llmStates = models.localModels.llmStates,
                    selectedLlm = models.localModels.selectedLlm,
                    aiConditionDetection = prefs.aiConditionDetection,
                    autoPriceEstimate = prefs.autoPriceEstimate,
                    multiPhotoAnalysis = prefs.multiPhotoAnalysis,
                    autoAnalyze = prefs.autoAnalyze,
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
                credentialClient.mirror(SharedCredentialId.OPENAI, key)
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

        fun onJinaSearchEnabledChange(enabled: Boolean) {
            viewModelScope.launch { repository.saveJinaSearchEnabled(enabled) }
        }

        fun onBraveSearchEnabledChange(enabled: Boolean) {
            viewModelScope.launch { repository.saveBraveSearchEnabled(enabled) }
        }

        fun onJinaApiKeyChange(value: String) {
            _editJinaKey.update { value }
            _isSearchSaved.update { false }
            _jinaTestResult.update { null }
            _jinaTestMessage.update { null }
        }

        fun onBraveApiKeyChange(value: String) {
            _editBraveKey.update { value }
            _isSearchSaved.update { false }
            _braveTestResult.update { null }
            _braveTestMessage.update { null }
        }

        fun saveJinaKey() {
            val key = _editJinaKey.value.ifBlank { uiState.value.savedJinaApiKey }.trim()
            if (key.isBlank()) return
            viewModelScope.launch {
                repository.saveJinaApiKey(key)
                credentialClient.mirror(SharedCredentialId.JINA, key)
                _isSearchSaved.update { true }
            }
            validateJinaKey(key)
        }

        fun saveBraveKey() {
            val key = _editBraveKey.value.ifBlank { uiState.value.savedBraveApiKey }.trim()
            if (key.isBlank()) return
            viewModelScope.launch {
                repository.saveBraveApiKey(key)
                credentialClient.mirror(SharedCredentialId.BRAVE, key)
                _isSearchSaved.update { true }
            }
            validateBraveKey(key)
        }

        fun clearJinaKey() {
            viewModelScope.launch {
                repository.saveJinaApiKey("")
                _editJinaKey.update { "" }
                _isSearchSaved.update { false }
                _jinaTestResult.update { null }
                _jinaTestMessage.update { null }
            }
        }

        fun clearBraveKey() {
            viewModelScope.launch {
                repository.saveBraveApiKey("")
                _editBraveKey.update { "" }
                _isSearchSaved.update { false }
                _braveTestResult.update { null }
                _braveTestMessage.update { null }
            }
        }

        fun testJinaKey() {
            val key = _editJinaKey.value.ifBlank { uiState.value.savedJinaApiKey }.trim()
            if (key.isBlank()) return
            validateJinaKey(key)
        }

        fun testBraveKey() {
            val key = _editBraveKey.value.ifBlank { uiState.value.savedBraveApiKey }.trim()
            if (key.isBlank()) return
            validateBraveKey(key)
        }

        private fun validateJinaKey(key: String) {
            viewModelScope.launch {
                _isJinaTesting.update { true }
                _jinaTestResult.update { null }
                _jinaTestMessage.update { "Checking connection…" }
                val result = runCatching { jinaSearchService.search("used electronics price", key, 1) }
                _isJinaTesting.update { false }
                if (result.isSuccess) {
                    _jinaTestResult.update { true }
                    _jinaTestMessage.update { "Connected to Jina AI" }
                } else {
                    _jinaTestResult.update { false }
                    _jinaTestMessage.update { result.exceptionOrNull()?.message ?: "Connection failed" }
                }
            }
        }

        private fun validateBraveKey(key: String) {
            viewModelScope.launch {
                _isBraveTesting.update { true }
                _braveTestResult.update { null }
                _braveTestMessage.update { "Checking connection…" }
                val result = runCatching { braveSearchService.search("used electronics price", key, 1) }
                _isBraveTesting.update { false }
                if (result.isSuccess) {
                    _braveTestResult.update { true }
                    _braveTestMessage.update { "Connected to Brave Search" }
                } else {
                    _braveTestResult.update { false }
                    _braveTestMessage.update { result.exceptionOrNull()?.message ?: "Connection failed" }
                }
            }
        }

        fun onSearchapiSearchEnabledChange(enabled: Boolean) {
            viewModelScope.launch { repository.saveSearchapiSearchEnabled(enabled) }
        }

        fun onSearchapiApiKeyChange(value: String) {
            _editSearchapiKey.update { value }
            _isSearchSaved.update { false }
            _searchapiTestResult.update { null }
            _searchapiTestMessage.update { null }
        }

        fun saveSearchapiKey() {
            val key = _editSearchapiKey.value.ifBlank { uiState.value.savedSearchapiApiKey }.trim()
            if (key.isBlank()) return
            viewModelScope.launch {
                repository.saveSearchapiApiKey(key)
                credentialClient.mirror(SharedCredentialId.SEARCHAPI, key)
                _isSearchSaved.update { true }
            }
            validateSearchapiKey(key)
        }

        fun clearSearchapiKey() {
            viewModelScope.launch {
                repository.saveSearchapiApiKey("")
                _editSearchapiKey.update { "" }
                _isSearchSaved.update { false }
                _searchapiTestResult.update { null }
                _searchapiTestMessage.update { null }
            }
        }

        fun testSearchapiKey() {
            val key = _editSearchapiKey.value.ifBlank { uiState.value.savedSearchapiApiKey }.trim()
            if (key.isBlank()) return
            validateSearchapiKey(key)
        }

        private fun validateSearchapiKey(key: String) {
            viewModelScope.launch {
                _isSearchapiTesting.update { true }
                _searchapiTestResult.update { null }
                _searchapiTestMessage.update { "Checking connection…" }
                val result = runCatching { searchapiService.search("used electronics price", key, 1) }
                _isSearchapiTesting.update { false }
                if (result.isSuccess) {
                    _searchapiTestResult.update { true }
                    _searchapiTestMessage.update { "Connected to SearchAPI.io" }
                } else {
                    _searchapiTestResult.update { false }
                    _searchapiTestMessage.update { result.exceptionOrNull()?.message ?: "Connection failed" }
                }
            }
        }

        fun onSerperSearchEnabledChange(enabled: Boolean) {
            viewModelScope.launch { repository.saveSerperSearchEnabled(enabled) }
        }

        fun onSerperApiKeyChange(value: String) {
            _editSerperKey.update { value }
            _isSearchSaved.update { false }
            _serperTestResult.update { null }
            _serperTestMessage.update { null }
        }

        fun saveSerperKey() {
            val key = _editSerperKey.value.ifBlank { uiState.value.savedSerperApiKey }.trim()
            if (key.isBlank()) return
            viewModelScope.launch {
                repository.saveSerperApiKey(key)
                credentialClient.mirror(SharedCredentialId.SERPER, key)
                _isSearchSaved.update { true }
            }
            validateSerperKey(key)
        }

        fun clearSerperKey() {
            viewModelScope.launch {
                repository.saveSerperApiKey("")
                _editSerperKey.update { "" }
                _isSearchSaved.update { false }
                _serperTestResult.update { null }
                _serperTestMessage.update { null }
            }
        }

        fun testSerperKey() {
            val key = _editSerperKey.value.ifBlank { uiState.value.savedSerperApiKey }.trim()
            if (key.isBlank()) return
            validateSerperKey(key)
        }

        private fun validateSerperKey(key: String) {
            viewModelScope.launch {
                _isSerperTesting.update { true }
                _serperTestResult.update { null }
                _serperTestMessage.update { "Checking connection…" }
                val result = runCatching { serperService.search("used electronics price", key, 1) }
                _isSerperTesting.update { false }
                if (result.isSuccess) {
                    _serperTestResult.update { true }
                    _serperTestMessage.update { "Connected to Serper.dev" }
                } else {
                    _serperTestResult.update { false }
                    _serperTestMessage.update { result.exceptionOrNull()?.message ?: "Connection failed" }
                }
            }
        }

        fun onSearchSavedShown() {
            _isSearchSaved.update { false }
        }

        fun onAutoAnalyzeChange(enabled: Boolean) {
            viewModelScope.launch { repository.saveAutoAnalyze(enabled) }
        }

        fun onVisionSourceChange(source: String) {
            viewModelScope.launch { repository.saveVisionSource(source) }
        }

        fun onTextSourceChange(source: String) {
            viewModelScope.launch { repository.saveTextSource(source) }
        }

        fun onListingSourceChange(source: String) {
            viewModelScope.launch { repository.saveListingSource(source) }
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

        fun downloadLlmModel(model: LocalLlmModel) {
            viewModelScope.launch { localModelManager.downloadLlm(model) }
        }

        fun deleteLlmModel(model: LocalLlmModel) {
            localModelManager.deleteLlm(model)
        }

        fun selectLlmModel(model: LocalLlmModel) {
            localModelManager.selectLlm(model)
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

        fun startProPurchase(
            activity: Activity,
            plan: String = "monthly",
        ) = purchaseDelegate.startPurchase(activity, plan)

        fun restorePurchases() = purchaseDelegate.restore()

        fun dismissPurchaseError() = purchaseDelegate.dismissError()

        private suspend fun computeStorage(): StorageInfo =
            withContext(Dispatchers.IO) {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val photosBytes =
                    picturesDir
                        ?.walkTopDown()
                        ?.filter { it.isFile }
                        ?.sumOf { it.length() }
                        ?: 0L
                val dbBytes =
                    listOf("shelf_snap.db", "shelf_snap.db-wal", "shelf_snap.db-shm")
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

        private data class SearchTestState(
            val isTesting: Boolean,
            val result: Boolean?,
            val message: String?,
        )

        private data class SearchApiGroup(
            val enabled: Boolean,
            val savedKey: String,
            val editKey: String,
            val test: SearchTestState,
        )

        private data class SearchState(
            val jinaEnabled: Boolean,
            val braveEnabled: Boolean,
            val savedJinaKey: String,
            val editJinaKey: String,
            val savedBraveKey: String,
            val editBraveKey: String,
            val saved: Boolean,
            val jinaTest: SearchTestState = SearchTestState(false, null, null),
            val braveTest: SearchTestState = SearchTestState(false, null, null),
            val searchapiEnabled: Boolean = true,
            val savedSearchapiKey: String = "",
            val editSearchapiKey: String = "",
            val searchapiTest: SearchTestState = SearchTestState(false, null, null),
            val serperEnabled: Boolean = false,
            val savedSerperKey: String = "",
            val editSerperKey: String = "",
            val serperTest: SearchTestState = SearchTestState(false, null, null),
        )

        private data class LocalModelsState(
            val llmStates: Map<LocalLlmModel, LocalModelState>,
            val selectedLlm: LocalLlmModel?,
        )

        private data class ModelsState(
            val visionModel: VisionModel,
            val reasoningModel: ReasoningModel,
            val visionSource: String,
            val textSource: String,
            val listingSource: String,
            val localModels: LocalModelsState,
        )

        private data class PrefsState(
            val autoAnalyze: Boolean,
            val aiConditionDetection: Boolean,
            val autoPriceEstimate: Boolean,
            val multiPhotoAnalysis: Boolean,
            val storage: StorageInfo,
            val isPurchasing: Boolean,
            val purchaseError: String?,
        )
    }
