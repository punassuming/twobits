package com.twobits.pricedrop.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import com.twobits.pricedrop.data.repository.AddResult
import com.twobits.pricedrop.data.repository.WatchlistRepository
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
                                hits.map { SearchResult(it.title, it.price, it.source, it.url) },
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
            viewModelScope.launch {
                when (
                    val result0 =
                        watchlistRepo.add(
                            WatchedProduct(
                                title = result.title,
                                currentPrice = result.price ?: 0.0,
                                targetPrice = targetPrice,
                                alertType = alertType,
                                productUrl = result.url,
                            ),
                        )
                ) {
                    is AddResult.Added -> onAdded(result0.id)
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
            viewModelScope.launch {
                when (
                    val result0 =
                        watchlistRepo.add(
                            WatchedProduct(
                                title = title,
                                currentPrice = price ?: 0.0,
                                targetPrice = targetPrice,
                                alertType = alertType,
                                productUrl = url,
                            ),
                        )
                ) {
                    is AddResult.Added -> onAdded(result0.id)
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
