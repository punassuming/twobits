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

        fun search() {
            val q = query.value.trim()
            if (q.isBlank()) return
            if (q.startsWith("http://") || q.startsWith("https://")) {
                uiState.value = SearchUiState.Loading
                viewModelScope.launch {
                    val pageContent = runCatching { api.readPage(q) }.getOrDefault("")
                    val (title, price) =
                        if (pageContent.isNotBlank()) {
                            runCatching { api.extractProductFromPage(pageContent, q) }
                                .getOrDefault("Product from URL" to null)
                        } else {
                            "Product from URL" to null
                        }
                    uiState.value = SearchUiState.UrlConfirm(url = q, title = title, price = price)
                }
                return
            }
            uiState.value = SearchUiState.Loading
            viewModelScope.launch {
                runCatching { watchlistRepo.searchProducts(q) }
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
                    AddResult.LimitReached -> uiState.value = SearchUiState.Error(LIMIT_MESSAGE)
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
                    AddResult.LimitReached -> uiState.value = SearchUiState.Error(LIMIT_MESSAGE)
                }
            }
        }

        companion object {
            const val LIMIT_MESSAGE =
                "Free plan tracks up to 3 active products. Remove one or upgrade to " +
                    "PriceDrop Pro for unlimited tracking."
        }
    }
