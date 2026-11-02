package com.twobits.pricedrop.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import com.twobits.pricedrop.data.repository.AddResult
import com.twobits.pricedrop.data.repository.WatchlistRepository
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val items: List<SearchResult>,
    ) : SearchUiState

    data class UrlConfirm(
        val url: String,
        val title: String,
        val price: Double?,
    ) : SearchUiState

    data class Error(
        val message: String,
    ) : SearchUiState
}

data class SearchResult(
    val title: String,
    val price: Double?,
    val source: String,
    val url: String,
    val canonicalProductId: String = "",
    val identity: ProductIdentity = ProductIdentity(),
    val imageUrl: String = "",
    val confidence: Int = 0,
    val offers: List<ProductOffer> = emptyList(),
)

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val watchlistRepo: WatchlistRepository,
        private val api: PriceDropApiClient,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        val uiState: MutableStateFlow<SearchUiState> = MutableStateFlow(SearchUiState.Idle)

        fun onQueryChange(q: String) {
            query.value = q
        }

        private var searchJob: Job? = null

        fun search() {
            val q = query.value.trim()
            if (q.isBlank()) return
            searchJob?.cancel()
            uiState.value = SearchUiState.Loading
            searchJob =
                if (q.startsWith("http://") || q.startsWith("https://")) {
                    viewModelScope.launch { readUrl(q) }
                } else {
                    viewModelScope.launch { searchByName(q) }
                }
        }

        /** Cancels an in-flight search or URL read and returns to the idle state. */
        fun cancelSearch() {
            searchJob?.cancel()
            searchJob = null
            uiState.value = SearchUiState.Idle
        }

        private suspend fun readUrl(url: String) {
            val pageContent = runCatching { api.readPage(url) }.getOrDefault("")
            currentCoroutineContext().ensureActive()
            val extracted =
                if (pageContent.isNotBlank()) {
                    runCatching { api.extractProductFromPage(pageContent, url) }.getOrNull()
                } else {
                    null
                }
            currentCoroutineContext().ensureActive()
            uiState.value =
                if (extracted == null || extracted.first == PriceDropApiClient.URL_FALLBACK_TITLE) {
                    // Don't offer to track a nameless, priceless placeholder.
                    SearchUiState.Error(URL_READ_FAILED_MESSAGE)
                } else {
                    SearchUiState.UrlConfirm(url = url, title = extracted.first, price = extracted.second)
                }
        }

        private suspend fun searchByName(q: String) {
            val outcome = runCatching { watchlistRepo.searchProducts(q) }
            currentCoroutineContext().ensureActive()
            outcome
                .onSuccess { hits ->
                    uiState.value =
                        SearchUiState.Results(
                            hits.map {
                                SearchResult(
                                    title = it.title,
                                    price = it.price,
                                    source = it.source,
                                    url = it.url,
                                    canonicalProductId = it.canonicalProductId,
                                    identity = it.identity,
                                    imageUrl = it.imageUrl,
                                    confidence = it.confidence,
                                    offers = it.offers,
                                )
                            },
                        )
                }.onFailure { e ->
                    uiState.value = SearchUiState.Error(e.message ?: "Search failed. Please try again.")
                }
        }

        fun addToWatchlist(
            result: SearchResult,
            targetPrice: Double?,
            alertType: String = "below_target",
            onAdded: (Long) -> Unit,
        ) {
            val product =
                WatchedProduct(
                    title = result.title,
                    currentPrice = result.price ?: 0.0,
                    targetPrice = targetPrice,
                    alertType = alertType,
                    brand = result.identity.brand.orEmpty(),
                    model = result.identity.model.orEmpty(),
                    manufacturerPartNumber = result.identity.manufacturerPartNumber.orEmpty(),
                    gtin = result.identity.gtin.orEmpty(),
                    upc = result.identity.upc.orEmpty(),
                    ean = result.identity.ean.orEmpty(),
                    asin = result.identity.asin.orEmpty(),
                    imageUrl = result.imageUrl,
                    productUrl = result.url,
                    canonicalProductId = result.canonicalProductId,
                    source = result.source,
                    confidence = result.confidence,
                )
            viewModelScope.launch {
                when (val result0 = watchlistRepo.add(product)) {
                    is AddResult.Added -> {
                        watchlistRepo.recordDiscoveredOffers(
                            result0.id,
                            result.canonicalProductId,
                            result.offers,
                            result.confidence,
                        )
                        onAdded(result0.id)
                        // Fire-and-forget: don't gate onAdded on a slow history fetch.
                        launch { watchlistRepo.backfillHistoryIfNeeded(result0.id, product.asin) }
                    }

                    AddResult.LimitReached -> {
                        uiState.value = SearchUiState.Error(LIMIT_MESSAGE)
                    }
                }
            }
        }

        fun confirmUrl(
            url: String,
            title: String,
            price: Double?,
            targetPrice: Double?,
            alertType: String = "below_target",
            onAdded: (Long) -> Unit,
        ) {
            val product =
                WatchedProduct(
                    title = title,
                    currentPrice = price ?: 0.0,
                    targetPrice = targetPrice,
                    alertType = alertType,
                    productUrl = url,
                )
            viewModelScope.launch {
                when (val result0 = watchlistRepo.add(product)) {
                    is AddResult.Added -> {
                        onAdded(result0.id)
                        // Fire-and-forget: don't gate onAdded on a slow history fetch.
                        launch { watchlistRepo.backfillHistoryIfNeeded(result0.id, product.asin) }
                    }

                    AddResult.LimitReached -> {
                        uiState.value = SearchUiState.Error(LIMIT_MESSAGE)
                    }
                }
            }
        }

        companion object {
            const val URL_READ_FAILED_MESSAGE =
                "Couldn't read that page. Try a different link, or search by product name."

            const val LIMIT_MESSAGE =
                "Free plan tracks up to 3 active products. Remove one or upgrade to " +
                    "PriceDrop Pro for unlimited tracking."
        }
    }
