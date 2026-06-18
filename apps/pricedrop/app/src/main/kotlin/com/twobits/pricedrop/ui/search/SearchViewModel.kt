package com.twobits.pricedrop.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val items: List<SearchResult>) : SearchUiState
    data class UrlConfirm(val url: String, val title: String, val price: Double?) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

data class SearchResult(
    val title: String,
    val price: Double?,
    val source: String,
    val url: String,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val watchlistRepo: WatchlistRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val uiState: MutableStateFlow<SearchUiState> = MutableStateFlow(SearchUiState.Idle)

    fun onQueryChange(q: String) { query.value = q }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        if (q.startsWith("http://") || q.startsWith("https://")) {
            uiState.value = SearchUiState.UrlConfirm(url = q, title = "Product from URL", price = null)
            return
        }
        uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            // Simulate results — real implementation posts to /v1/pricedrop/search
            kotlinx.coroutines.delay(800)
            uiState.value = SearchUiState.Results(
                listOf(
                    SearchResult("$q (Amazon)", 49.99, "amazon.com", "https://amazon.com"),
                    SearchResult("$q (Walmart)", 44.99, "walmart.com", "https://walmart.com"),
                ),
            )
        }
    }

    fun addToWatchlist(result: SearchResult, targetPrice: Double?, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val id = watchlistRepo.add(
                WatchedProduct(
                    title = result.title,
                    currentPrice = result.price ?: 0.0,
                    targetPrice = targetPrice,
                    productUrl = result.url,
                ),
            )
            onAdded(id)
        }
    }

    fun confirmUrl(url: String, targetPrice: Double?, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val id = watchlistRepo.add(
                WatchedProduct(
                    title = "Product from URL",
                    currentPrice = 0.0,
                    targetPrice = targetPrice,
                    productUrl = url,
                ),
            )
            onAdded(id)
        }
    }
}
