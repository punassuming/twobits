package com.twobits.pricedrop.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.DropsRepository
import com.twobits.pricedrop.data.repository.WatchlistRepository
import com.twobits.pricedrop.notifications.PriceDropNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class WatchViewModel
    @Inject
    constructor(
        private val watchlistRepo: WatchlistRepository,
        private val dropsRepo: DropsRepository,
    ) : ViewModel() {
        private val allProducts: StateFlow<List<WatchedProduct>> =
            watchlistRepo
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val activeDrops: StateFlow<List<Drop>> =
            dropsRepo
                .observeActiveDrops()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val activeDropCount: StateFlow<Int> =
            dropsRepo
                .observeActiveCount()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        val activeFilter = MutableStateFlow("All")

        val refreshingId = MutableStateFlow<Long?>(null)

        val watchlist: StateFlow<List<WatchedProduct>> =
            combine(allProducts, activeDrops, activeFilter) { products, drops, filter ->
                when (filter) {
                    "Below target" ->
                        products.filter { p ->
                            p.targetPrice != null && p.currentPrice > 0 && p.currentPrice <= p.targetPrice
                        }
                    "Coupons" -> {
                        val productIdsWithCoupons =
                            drops
                                .filter { it.type == PriceDropNotifier.TYPE_COUPON_FOUND || it.couponCode.isNotBlank() }
                                .map { it.productId }
                                .toSet()
                        products.filter { it.id in productIdsWithCoupons }
                    }
                    "Needs check" -> {
                        val staleThresholdMs = TimeUnit.HOURS.toMillis(12)
                        val now = System.currentTimeMillis()
                        products.filter { p ->
                            p.isActive && (p.lastCheckedAt == 0L || now - p.lastCheckedAt > staleThresholdMs)
                        }
                    }
                    "Paused" -> products.filter { !it.isActive }
                    else -> products
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun setFilter(filter: String) {
            activeFilter.value = filter
        }

        fun removeItem(id: Long) {
            viewModelScope.launch { watchlistRepo.remove(id) }
        }

        fun pauseItem(id: Long) {
            viewModelScope.launch {
                val product = watchlistRepo.getById(id) ?: return@launch
                watchlistRepo.update(product.copy(isActive = false))
            }
        }

        fun resumeItem(id: Long) {
            viewModelScope.launch {
                val product = watchlistRepo.getById(id) ?: return@launch
                watchlistRepo.update(product.copy(isActive = true))
            }
        }

        fun refreshItem(id: Long) {
            viewModelScope.launch {
                refreshingId.value = id
                runCatching { watchlistRepo.refreshPrice(id) }
                refreshingId.value = null
            }
        }
    }
