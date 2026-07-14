package com.twobits.pricedrop.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.Activity
import com.twobits.pricedrop.data.model.Coupon
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.Offer
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.DropsRepository
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val product: WatchedProduct? = null,
    val priceHistory: List<PriceEvent> = emptyList(),
    val drops: List<Drop> = emptyList(),
    val coupons: List<Coupon> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val activity: List<Activity> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
    val refreshError: String? = null,
)

@HiltViewModel
class ProductDetailViewModel
    @Inject
    constructor(
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
                    _uiState.value =
                        _uiState.value.copy(
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
            viewModelScope.launch {
                watchlistRepo.observeCoupons(productId).collect { coupons ->
                    _uiState.value = _uiState.value.copy(coupons = coupons)
                }
            }
            viewModelScope.launch {
                watchlistRepo.observeOffers(productId).collect { offers ->
                    _uiState.value = _uiState.value.copy(offers = offers)
                }
            }
            viewModelScope.launch {
                watchlistRepo.observeActivity(productId).collect { activity ->
                    _uiState.value = _uiState.value.copy(activity = activity)
                }
            }
        }

        /** Pull the latest price, offers, and coupons from the Worker for this product. */
        fun refresh(productId: Long) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isRefreshing = true, refreshError = null)
                val product = watchlistRepo.getById(productId)
                runCatching {
                    watchlistRepo.refreshPrice(productId, force = true)
                    watchlistRepo.refreshOffers(productId)
                    val query = product?.title.orEmpty().ifBlank { product?.brand.orEmpty() }
                    if (query.isNotBlank()) watchlistRepo.fetchCoupons(productId, query)
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(refreshError = e.message ?: "Refresh failed")
                }
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }

        fun clearRefreshError() {
            _uiState.value = _uiState.value.copy(refreshError = null)
        }

        fun addManualCoupon(
            productId: Long,
            code: String,
            description: String,
        ) {
            viewModelScope.launch { watchlistRepo.addManualCoupon(productId, code, description) }
        }

        fun updateTargetPrice(
            productId: Long,
            target: Double?,
        ) {
            viewModelScope.launch {
                val product = watchlistRepo.getById(productId) ?: return@launch
                watchlistRepo.update(product.copy(targetPrice = target))
            }
        }

        fun dismissAllDrops(productId: Long) {
            viewModelScope.launch { dropsRepo.dismissAllForProduct(productId) }
        }

        fun toggleActive(productId: Long) {
            viewModelScope.launch {
                val product = watchlistRepo.getById(productId) ?: return@launch
                watchlistRepo.update(product.copy(isActive = !product.isActive))
            }
        }
    }
