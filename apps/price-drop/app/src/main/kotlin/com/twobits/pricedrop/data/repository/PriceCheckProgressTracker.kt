package com.twobits.pricedrop.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
        // Process-scoped, not the caller's own coroutine: moving only the *display* state to
        // this singleton wasn't enough on its own — ProductDetailViewModel's viewModelScope is
        // cancelled the moment the user backs out of the product, and until launchRefresh()
        // below, the actual price/offers/coupons calls were still tied to it, so pressing Back
        // mid-refresh killed it outright even though the new nav-root footer suggested it would
        // keep going.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var current: Deferred<*>? = null
        private val _state = MutableStateFlow<PriceCheckProgressState?>(null)
        val state: StateFlow<PriceCheckProgressState?> = _state.asStateFlow()

        /**
         * Runs [refresh] on [scope] instead of the caller's own coroutine. [refresh] receives an
         * `update` callback to report which step (price/offers/coupons) is running, folded into
         * [state] the same way as before. The returned [Deferred] is not a child of the caller's
         * job: awaiting it and having that await cancelled (screen torn down again before this
         * finishes) stops the *caller* from waiting, but does not stop the refresh. This only
         * needs to survive in-app navigation, not the app being backgrounded entirely — unlike
         * Scrybe's manual-retry transcription, which needs the stronger guarantee a foreground
         * `WorkManager` job gives it (see `RetranscribeWorker`), because a refresh is three quick
         * sequential API calls rather than something that can run for tens of minutes on-device.
         */
        fun <T> launchRefresh(
            productId: Long,
            initialStep: String,
            refresh: suspend ((String) -> Unit) -> T,
        ): Deferred<T> {
            _state.value = PriceCheckProgressState(productId = productId, step = initialStep, startedAtMs = System.currentTimeMillis())
            val deferred =
                scope.async {
                    try {
                        refresh { step -> _state.update { it?.copy(step = step) } }
                    } finally {
                        _state.value = null
                    }
                }
            current = deferred
            return deferred
        }

        /** Stops the refresh currently in flight, if any. */
        fun cancel() {
            current?.cancel()
        }
    }
