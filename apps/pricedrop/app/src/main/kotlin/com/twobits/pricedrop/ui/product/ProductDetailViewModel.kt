package com.twobits.pricedrop.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.DropsRepository
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val product: WatchedProduct? = null,
    val priceHistory: List<PriceEvent> = emptyList(),
    val drops: List<Drop> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val watchlistRepo: WatchlistRepository,
    private val dropsRepo: DropsRepository,
) : ViewModel() {

    private val _product = MutableStateFlow<WatchedProduct?>(null)
    val product: StateFlow<WatchedProduct?> = _product

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState

    fun load(productId: Long) {
        viewModelScope.launch {
            val product = watchlistRepo.getById(productId)
            _product.value = product
            watchlistRepo.observePriceHistory(productId).collect { history ->
                _uiState.value = _uiState.value.copy(
                    product = product,
                    priceHistory = history,
                    isLoading = false,
                )
            }
        }
        viewModelScope.launch {
            dropsRepo.observeDropsForProduct(productId).collect { drops ->
                _uiState.value = _uiState.value.copy(drops = drops)
            }
        }
    }

    fun updateTargetPrice(productId: Long, target: Double?) {
        viewModelScope.launch {
            val product = watchlistRepo.getById(productId) ?: return@launch
            watchlistRepo.update(product.copy(targetPrice = target))
        }
    }

    fun dismissAllDrops(productId: Long) {
        viewModelScope.launch { dropsRepo.dismissAllForProduct(productId) }
    }
}
