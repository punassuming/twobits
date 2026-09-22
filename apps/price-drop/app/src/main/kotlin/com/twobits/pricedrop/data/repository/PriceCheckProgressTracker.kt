package com.twobits.pricedrop.data.repository

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PriceCheckProgressState(
    val productId: Long,
    val step: String,
    val startedAtMs: Long,
)

/**
 * Live state for [com.twobits.pricedrop.ui.product.ProductDetailViewModel.refresh], visible
 * regardless of which screen is showing. Previously a plain `isRefreshing` boolean on that
 * screen's own `ProductDetailUiState` with no cross-screen visibility and no cancel — the same
 * gap [AskProgressTracker] closes for Ask. Single-slot, not keyed like Shelf Snap's
 * `LocalAnalysisProgressTracker`: only one `ProductDetailScreen` can be on screen — and therefore
 * refreshing — at a time.
 */
@Singleton
class PriceCheckProgressTracker
    @Inject
    constructor() {
        private var job: Job? = null
        private val _state = MutableStateFlow<PriceCheckProgressState?>(null)
        val state: StateFlow<PriceCheckProgressState?> = _state.asStateFlow()

        /** Captures the calling coroutine's own [Job] so [cancel] has something to stop. */
        suspend fun start(
            productId: Long,
            step: String,
        ) {
            job = currentCoroutineContext()[Job]
            _state.value = PriceCheckProgressState(productId = productId, step = step, startedAtMs = System.currentTimeMillis())
        }

        /** Updates the running refresh's step label without disturbing its start time. */
        fun update(step: String) {
            _state.update { it?.copy(step = step) }
        }

        fun finish() {
            job = null
            _state.value = null
        }

        /** Stops the refresh currently in flight, if any. */
        fun cancel() {
            job?.cancel()
        }
    }
