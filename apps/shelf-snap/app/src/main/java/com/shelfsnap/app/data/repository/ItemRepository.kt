package com.shelfsnap.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shelfsnap.app.data.local.ItemDao
import com.shelfsnap.app.data.local.toDomain
import com.shelfsnap.app.data.local.toEntity
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.remote.PriceResearchResult
import com.shelfsnap.app.data.remote.PriceResearchService
import com.shelfsnap.app.data.remote.VisionAnalysisService
import com.shelfsnap.app.data.remote.search.SearchProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val dao: ItemDao,
    private val visionService: VisionAnalysisService,
    private val priceResearchService: PriceResearchService,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        private val KEY_SEARCH_API_KEY = stringPreferencesKey("search_api_key")
        private val KEY_AUTO_ANALYZE = booleanPreferencesKey("auto_analyze")
        private val KEY_KEEP_PHOTOS = booleanPreferencesKey("keep_original_photos")
        private val KEY_VISION_MODEL = stringPreferencesKey("vision_model")
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<Item>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Item>> =
        dao.observeFiltered(query.trim()).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Item? = dao.getById(id)?.toDomain()

    suspend fun save(item: Item): Long = dao.insert(item.toEntity())

    suspend fun update(item: Item) = dao.update(item.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    // ── AI Analysis ───────────────────────────────────────────────────────────

    /**
     * Sends [photoPaths] to the vision service for analysis.
     * Returns a [DraftItemResult]; caller must check [DraftItemResult.error].
     */
    suspend fun analysePhotos(photoPaths: List<String>): DraftItemResult {
        val apiKey = getApiKey()
        val model = getVisionModel()
        return visionService.analyse(photoPaths, apiKey, model.apiName)
    }

    // ── Price research ──────────────────────────────────────────────────────────

    /**
     * Researches a resale price for [item] using OpenAI inference plus the
     * configured web-search provider. Caller must check [PriceResearchResult.error].
     */
    suspend fun researchPrice(item: Item): PriceResearchResult {
        return priceResearchService.research(
            item = item,
            openAiKey = getApiKey(),
            searchProvider = getSearchProvider(),
            searchKey = getSearchApiKey()
        )
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun observeApiKey(): Flow<String> =
        dataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun getApiKey(): String =
        dataStore.data.firstOrNull()?.get(KEY_API_KEY) ?: ""

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[KEY_API_KEY] = key }
    }

    // ── Settings: web search for price research ─────────────────────────────────

    fun observeSearchProvider(): Flow<SearchProvider> =
        dataStore.data.map { SearchProvider.fromKey(it[KEY_SEARCH_PROVIDER] ?: "") }

    suspend fun getSearchProvider(): SearchProvider =
        SearchProvider.fromKey(dataStore.data.firstOrNull()?.get(KEY_SEARCH_PROVIDER) ?: "")

    suspend fun saveSearchProvider(provider: SearchProvider) {
        dataStore.edit { it[KEY_SEARCH_PROVIDER] = provider.key }
    }

    fun observeSearchApiKey(): Flow<String> =
        dataStore.data.map { it[KEY_SEARCH_API_KEY] ?: "" }

    suspend fun getSearchApiKey(): String =
        dataStore.data.firstOrNull()?.get(KEY_SEARCH_API_KEY) ?: ""

    suspend fun saveSearchApiKey(key: String) {
        dataStore.edit { it[KEY_SEARCH_API_KEY] = key }
    }

    // ── Settings: capture preferences ───────────────────────────────────────────

    /** Whether captured photos are analysed automatically (default off). */
    fun observeAutoAnalyze(): Flow<Boolean> =
        dataStore.data.map { it[KEY_AUTO_ANALYZE] ?: false }

    suspend fun getAutoAnalyze(): Boolean =
        dataStore.data.firstOrNull()?.get(KEY_AUTO_ANALYZE) ?: false

    suspend fun saveAutoAnalyze(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_ANALYZE] = enabled }
    }

    /** Whether the original full-resolution photos are kept on device (default on). */
    fun observeKeepPhotos(): Flow<Boolean> =
        dataStore.data.map { it[KEY_KEEP_PHOTOS] ?: true }

    suspend fun saveKeepPhotos(enabled: Boolean) {
        dataStore.edit { it[KEY_KEEP_PHOTOS] = enabled }
    }

    // ── Settings: vision model for BYOK users ───────────────────────────────────

    fun observeVisionModel(): Flow<VisionModel> =
        dataStore.data.map { VisionModel.fromApiName(it[KEY_VISION_MODEL]) }

    suspend fun getVisionModel(): VisionModel =
        VisionModel.fromApiName(dataStore.data.firstOrNull()?.get(KEY_VISION_MODEL))

    suspend fun saveVisionModel(model: VisionModel) {
        dataStore.edit { it[KEY_VISION_MODEL] = model.apiName }
    }
}
