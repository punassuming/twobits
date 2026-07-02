package com.shelfsnap.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shelfsnap.app.data.listing.ListingCopy
import com.shelfsnap.app.data.local.ItemDao
import com.shelfsnap.app.data.local.toDomain
import com.shelfsnap.app.data.local.toEntity
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.pro.executionModeFromSourceKey
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.remote.ListingGenerationService
import com.shelfsnap.app.data.remote.PriceResearchResult
import com.shelfsnap.app.data.remote.PriceResearchService
import com.shelfsnap.app.data.remote.VisionAnalysisService
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.core.pro.ExecutionMode
import com.twobits.securestore.CredentialCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository
    @Inject
    constructor(
        private val dao: ItemDao,
        private val visionService: VisionAnalysisService,
        private val priceResearchService: PriceResearchService,
        private val listingGenerationService: ListingGenerationService,
        private val dataStore: DataStore<Preferences>,
        private val subscriptionRepository: SubscriptionRepository,
        private val crypto: CredentialCrypto,
    ) {
        companion object {
            private const val WORKER_BASE = "https://api.twobits.app"
            private val KEY_API_KEY = stringPreferencesKey("openai_api_key")
            private val KEY_SEARCH_PROVIDER = stringPreferencesKey("search_provider")
            private val KEY_SEARCH_API_KEY = stringPreferencesKey("search_api_key") // legacy — migration fallback for Jina
            private val KEY_JINA_API_KEY = stringPreferencesKey("jina_search_api_key")
            private val KEY_BRAVE_API_KEY = stringPreferencesKey("brave_search_api_key")
            private val KEY_SEARCHAPI_API_KEY = stringPreferencesKey("searchapi_search_api_key")
            private val KEY_JINA_SEARCH_ENABLED = booleanPreferencesKey("jina_search_enabled")
            private val KEY_BRAVE_SEARCH_ENABLED = booleanPreferencesKey("brave_search_enabled")
            private val KEY_SEARCHAPI_SEARCH_ENABLED = booleanPreferencesKey("searchapi_search_enabled")
            private val KEY_AUTO_ANALYZE = booleanPreferencesKey("auto_analyze")
            private val KEY_KEEP_PHOTOS = booleanPreferencesKey("keep_original_photos")
            private val KEY_VISION_MODEL = stringPreferencesKey("vision_model")
            private val KEY_REASONING_MODEL = stringPreferencesKey("reasoning_model")
            private val KEY_VISION_SOURCE = stringPreferencesKey("vision_source")

            // Market-research source. Historically named "text_source" and shared with listing
            // generation; listing generation now has its own KEY_LISTING_SOURCE below, so this key
            // is kept (not renamed) to avoid silently resetting existing users' saved preference.
            private val KEY_TEXT_SOURCE = stringPreferencesKey("text_source")
            private val KEY_LISTING_SOURCE = stringPreferencesKey("listing_source")
            private val KEY_AI_CONDITION_DETECTION = booleanPreferencesKey("ai_condition_detection")
            private val KEY_AUTO_PRICE_ESTIMATE = booleanPreferencesKey("auto_price_estimate")
            private val KEY_MULTI_PHOTO_ANALYSIS = booleanPreferencesKey("multi_photo_analysis")
        }

        // ── Inventory ─────────────────────────────────────────────────────────────

        fun observeAll(): Flow<List<Item>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        fun search(query: String): Flow<List<Item>> = dao.observeFiltered(query.trim()).map { list -> list.map { it.toDomain() } }

        suspend fun getById(id: Long): Item? = dao.getById(id)?.toDomain()

        suspend fun save(item: Item): Long = dao.insert(item.toEntity())

        suspend fun update(item: Item) = dao.update(item.toEntity())

        suspend fun delete(id: Long) = dao.deleteById(id)

        // ── AI Analysis ───────────────────────────────────────────────────────────

        /**
         * Sends [photoPaths] to the vision service for analysis.
         * Returns a [DraftItemResult]; caller must check [DraftItemResult.error].
         */
        suspend fun analysePhotos(
            photoPaths: List<String>,
            modelOverride: VisionModel? = null,
        ): DraftItemResult {
            val model = (modelOverride ?: getVisionModel()).apiName
            val sourceKey = dataStore.data.firstOrNull()?.get(KEY_VISION_SOURCE) ?: "byok"
            return when (executionModeFromSourceKey(sourceKey)) {
                ExecutionMode.PRO -> {
                    val appUserId = subscriptionRepository.getAppUserId()
                    visionService.analyse(
                        photoPaths = photoPaths,
                        apiKey = appUserId,
                        model = model,
                        baseUrl = WORKER_BASE,
                        authHeader = "Bearer $appUserId",
                    )
                }
                ExecutionMode.LOCAL -> DraftItemResult(error = "Local vision inference is not yet available in this build.")
                ExecutionMode.BYOK, ExecutionMode.OFF -> visionService.analyse(photoPaths, getApiKey(), model)
            }
        }

        // ── Price research ──────────────────────────────────────────────────────────

        /**
         * Researches a resale price for [item] using OpenAI inference plus the
         * configured web-search provider. Caller must check [PriceResearchResult.error].
         *
         * Preflights the selected source before doing any network work, so a missing BYOK key, an
         * inactive Pro subscription, or the feature being turned off fails immediately with a clear
         * message instead of spinning for several seconds before the underlying call errors out.
         */
        suspend fun researchPrice(item: Item): PriceResearchResult {
            val sourceKey = dataStore.data.firstOrNull()?.get(KEY_TEXT_SOURCE) ?: "byok"
            val mode = executionModeFromSourceKey(sourceKey)

            when (mode) {
                ExecutionMode.BYOK -> {
                    val hasSearchProvider = getSearchapiSearchEnabled() || getJinaSearchEnabled() || getBraveSearchEnabled()
                    if (getApiKey().isBlank() || !hasSearchProvider) {
                        return PriceResearchResult(
                            error = "Add an OpenAI key and enable at least one search provider in AI configuration.",
                        )
                    }
                }
                ExecutionMode.PRO ->
                    if (subscriptionRepository.subscriptionTier.value !is SubscriptionTier.Pro) {
                        return PriceResearchResult(error = "Market research needs an active Shelf Snap Pro subscription.")
                    }
                ExecutionMode.OFF -> return PriceResearchResult(error = "Market research is turned off in AI configuration.")
                ExecutionMode.LOCAL -> {}
            }

            return when (mode) {
                ExecutionMode.PRO -> {
                    val appUserId = subscriptionRepository.getAppUserId()
                    priceResearchService.research(
                        item = item,
                        openAiKey = appUserId,
                        model = getReasoningModel().apiName,
                        openAiBaseUrl = WORKER_BASE,
                        openAiAuthHeader = "Bearer $appUserId",
                        workerSearchUrl = "$WORKER_BASE/v1/shelfsnap/search",
                        workerAuthHeader = "Bearer $appUserId",
                    )
                }
                ExecutionMode.LOCAL -> PriceResearchResult(error = "Local LLM inference is not yet available in this build.")
                ExecutionMode.BYOK, ExecutionMode.OFF -> {
                    val jinaKey = if (getJinaSearchEnabled()) getJinaApiKey() else ""
                    val searchapiKey = if (getSearchapiSearchEnabled()) getSearchapiApiKey() else ""
                    priceResearchService.research(
                        item = item,
                        openAiKey = getApiKey(),
                        searchProviders =
                            buildList {
                                // SearchAPI.io first — it honors site: and returns real links
                                // where Jina returns eBay error pages.
                                if (searchapiKey.isNotBlank()) add(SearchProvider.SEARCHAPI to searchapiKey)
                                if (jinaKey.isNotBlank()) add(SearchProvider.JINA to jinaKey)
                                if (getBraveSearchEnabled()) add(SearchProvider.BRAVE to getBraveApiKey())
                            },
                        // Page reading is Jina-only — Brave/SearchAPI have no reader endpoint.
                        readerKey = jinaKey.ifBlank { null },
                        model = getReasoningModel().apiName,
                    )
                }
            }
        }

        /**
         * AI-refines a platform listing's copy, routed by the Listing feature's own source
         * (independent of Market Research's [KEY_TEXT_SOURCE]) — mirrors [analysePhotos]/
         * [researchPrice]'s Pro/BYOK/Local branching. [ListingGenerationService] already falls back
         * to returning [current] unchanged on any failure, which also covers the Local case here
         * since no local listing-generation implementation exists yet.
         */
        suspend fun refineListing(
            item: Item,
            platform: Platform,
            current: ListingCopy,
        ): ListingCopy {
            val sourceKey = dataStore.data.firstOrNull()?.get(KEY_LISTING_SOURCE) ?: "byok"
            val model = getReasoningModel().apiName
            return when (executionModeFromSourceKey(sourceKey)) {
                ExecutionMode.PRO -> {
                    val appUserId = subscriptionRepository.getAppUserId()
                    listingGenerationService.refine(
                        item = item,
                        platform = platform,
                        current = current,
                        openAiKey = appUserId,
                        openAiBaseUrl = WORKER_BASE,
                        openAiAuthHeader = "Bearer $appUserId",
                        model = model,
                    )
                }
                ExecutionMode.LOCAL -> current
                ExecutionMode.BYOK, ExecutionMode.OFF -> listingGenerationService.refine(item, platform, current, getApiKey(), model = model)
            }
        }

        fun observeListingSource(): Flow<String> = dataStore.data.map { it[KEY_LISTING_SOURCE] ?: "byok" }

        suspend fun getListingSource(): String = dataStore.data.firstOrNull()?.get(KEY_LISTING_SOURCE) ?: "byok"

        suspend fun saveListingSource(source: String) {
            dataStore.edit { it[KEY_LISTING_SOURCE] = source }
        }

        /** Verifies that the saved OpenAI API key is accepted by the API. */
        suspend fun testApiKey(): Result<Unit> = visionService.testKey(getApiKey())

        /** Verifies a specific key without saving it first. */
        suspend fun testApiKey(key: String): Result<Unit> = visionService.testKey(key)

        // ── Settings ──────────────────────────────────────────────────────────────

        fun observeApiKey(): Flow<String> = dataStore.data.map { crypto.tryDecryptOrPassthrough(it[KEY_API_KEY] ?: "") }

        suspend fun getApiKey(): String = crypto.tryDecryptOrPassthrough(dataStore.data.firstOrNull()?.get(KEY_API_KEY) ?: "")

        suspend fun saveApiKey(key: String) {
            dataStore.edit { it[KEY_API_KEY] = crypto.encrypt(key) }
        }

        // ── Settings: web search for price research ─────────────────────────────────

        fun observeSearchProvider(): Flow<SearchProvider> = dataStore.data.map { SearchProvider.fromKey(it[KEY_SEARCH_PROVIDER] ?: "") }

        suspend fun getSearchProvider(): SearchProvider = SearchProvider.fromKey(dataStore.data.firstOrNull()?.get(KEY_SEARCH_PROVIDER) ?: "")

        suspend fun saveSearchProvider(provider: SearchProvider) {
            dataStore.edit { it[KEY_SEARCH_PROVIDER] = provider.key }
        }

        fun observeJinaApiKey(): Flow<String> =
            dataStore.data.map { prefs ->
                val raw = prefs[KEY_JINA_API_KEY] ?: prefs[KEY_SEARCH_API_KEY] ?: ""
                crypto.tryDecryptOrPassthrough(raw)
            }

        fun observeBraveApiKey(): Flow<String> = dataStore.data.map { crypto.tryDecryptOrPassthrough(it[KEY_BRAVE_API_KEY] ?: "") }

        suspend fun getJinaApiKey(): String {
            val prefs = dataStore.data.firstOrNull()
            val raw = prefs?.get(KEY_JINA_API_KEY) ?: prefs?.get(KEY_SEARCH_API_KEY) ?: ""
            return crypto.tryDecryptOrPassthrough(raw)
        }

        suspend fun getBraveApiKey(): String {
            val raw = dataStore.data.firstOrNull()?.get(KEY_BRAVE_API_KEY) ?: ""
            return crypto.tryDecryptOrPassthrough(raw)
        }

        suspend fun saveJinaApiKey(key: String) {
            dataStore.edit { it[KEY_JINA_API_KEY] = crypto.encrypt(key) }
        }

        suspend fun saveBraveApiKey(key: String) {
            dataStore.edit { it[KEY_BRAVE_API_KEY] = crypto.encrypt(key) }
        }

        fun observeJinaSearchEnabled(): Flow<Boolean> = dataStore.data.map { it[KEY_JINA_SEARCH_ENABLED] ?: true }

        suspend fun getJinaSearchEnabled(): Boolean = dataStore.data.firstOrNull()?.get(KEY_JINA_SEARCH_ENABLED) ?: true

        suspend fun saveJinaSearchEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_JINA_SEARCH_ENABLED] = enabled }
        }

        fun observeBraveSearchEnabled(): Flow<Boolean> = dataStore.data.map { it[KEY_BRAVE_SEARCH_ENABLED] ?: false }

        suspend fun getBraveSearchEnabled(): Boolean = dataStore.data.firstOrNull()?.get(KEY_BRAVE_SEARCH_ENABLED) ?: false

        suspend fun saveBraveSearchEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_BRAVE_SEARCH_ENABLED] = enabled }
        }

        fun observeSearchapiApiKey(): Flow<String> = dataStore.data.map { crypto.tryDecryptOrPassthrough(it[KEY_SEARCHAPI_API_KEY] ?: "") }

        suspend fun getSearchapiApiKey(): String {
            val raw = dataStore.data.firstOrNull()?.get(KEY_SEARCHAPI_API_KEY) ?: ""
            return crypto.tryDecryptOrPassthrough(raw)
        }

        suspend fun saveSearchapiApiKey(key: String) {
            dataStore.edit { it[KEY_SEARCHAPI_API_KEY] = crypto.encrypt(key) }
        }

        fun observeSearchapiSearchEnabled(): Flow<Boolean> = dataStore.data.map { it[KEY_SEARCHAPI_SEARCH_ENABLED] ?: true }

        suspend fun getSearchapiSearchEnabled(): Boolean = dataStore.data.firstOrNull()?.get(KEY_SEARCHAPI_SEARCH_ENABLED) ?: true

        suspend fun saveSearchapiSearchEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_SEARCHAPI_SEARCH_ENABLED] = enabled }
        }

        suspend fun getSearchApiKey(): String =
            when (getSearchProvider()) {
                SearchProvider.BRAVE -> getBraveApiKey()
                SearchProvider.JINA -> getJinaApiKey()
                SearchProvider.SEARCHAPI -> getSearchapiApiKey()
                SearchProvider.NONE -> ""
            }

        @Deprecated("Use saveJinaApiKey or saveBraveApiKey")
        suspend fun saveSearchApiKey(key: String) {
            dataStore.edit { it[KEY_SEARCH_API_KEY] = key }
        }

        // ── Settings: capture preferences ───────────────────────────────────────────

        /** Whether captured photos are analysed automatically (default off). */
        fun observeAutoAnalyze(): Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_ANALYZE] ?: false }

        suspend fun getAutoAnalyze(): Boolean = dataStore.data.firstOrNull()?.get(KEY_AUTO_ANALYZE) ?: false

        suspend fun saveAutoAnalyze(enabled: Boolean) {
            dataStore.edit { it[KEY_AUTO_ANALYZE] = enabled }
        }

        /** Whether the original full-resolution photos are kept on device (default on). */
        fun observeKeepPhotos(): Flow<Boolean> = dataStore.data.map { it[KEY_KEEP_PHOTOS] ?: true }

        suspend fun saveKeepPhotos(enabled: Boolean) {
            dataStore.edit { it[KEY_KEEP_PHOTOS] = enabled }
        }

        // ── Settings: vision model for BYOK users ───────────────────────────────────

        fun observeVisionModel(): Flow<VisionModel> = dataStore.data.map { VisionModel.fromApiName(it[KEY_VISION_MODEL]) }

        suspend fun getVisionModel(): VisionModel = VisionModel.fromApiName(dataStore.data.firstOrNull()?.get(KEY_VISION_MODEL))

        suspend fun saveVisionModel(model: VisionModel) {
            dataStore.edit { it[KEY_VISION_MODEL] = model.apiName }
        }

        fun observeReasoningModel(): Flow<ReasoningModel> = dataStore.data.map { ReasoningModel.fromApiName(it[KEY_REASONING_MODEL]) }

        suspend fun getReasoningModel(): ReasoningModel = ReasoningModel.fromApiName(dataStore.data.firstOrNull()?.get(KEY_REASONING_MODEL))

        suspend fun saveReasoningModel(model: ReasoningModel) {
            dataStore.edit { it[KEY_REASONING_MODEL] = model.apiName }
        }

        // ── Settings: AI source (pro / byok / local) ─────────────────────────────

        suspend fun getVisionSource(): String = dataStore.data.firstOrNull()?.get(KEY_VISION_SOURCE) ?: "byok"

        fun observeVisionSource(): Flow<String> = dataStore.data.map { it[KEY_VISION_SOURCE] ?: "byok" }

        suspend fun saveVisionSource(source: String) {
            dataStore.edit { it[KEY_VISION_SOURCE] = source }
        }

        fun observeTextSource(): Flow<String> = dataStore.data.map { it[KEY_TEXT_SOURCE] ?: "byok" }

        suspend fun saveTextSource(source: String) {
            dataStore.edit { it[KEY_TEXT_SOURCE] = source }
        }

        // ── Settings: AI analysis toggles ────────────────────────────────────────

        fun observeAiConditionDetection(): Flow<Boolean> = dataStore.data.map { it[KEY_AI_CONDITION_DETECTION] ?: true }

        suspend fun saveAiConditionDetection(enabled: Boolean) {
            dataStore.edit { it[KEY_AI_CONDITION_DETECTION] = enabled }
        }

        fun observeAutoPriceEstimate(): Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_PRICE_ESTIMATE] ?: true }

        suspend fun saveAutoPriceEstimate(enabled: Boolean) {
            dataStore.edit { it[KEY_AUTO_PRICE_ESTIMATE] = enabled }
        }

        fun observeMultiPhotoAnalysis(): Flow<Boolean> = dataStore.data.map { it[KEY_MULTI_PHOTO_ANALYSIS] ?: false }

        suspend fun saveMultiPhotoAnalysis(enabled: Boolean) {
            dataStore.edit { it[KEY_MULTI_PHOTO_ANALYSIS] = enabled }
        }
    }
