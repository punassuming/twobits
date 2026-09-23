package com.shelfsnap.app.data.remote

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

data class MarketResearchState(
    val itemId: Long,
    val progress: ResearchProgress?,
    val startedAtMs: Long,
)

/**
 * Live state for [PriceResearchService]-backed market research, visible regardless of which
 * screen is showing. Previously a plain field on `ItemDetailViewModel`, so the progress footer
 * disappeared the instant the user navigated away from the item mid-research — the one footer in
 * the app that didn't survive navigation, unlike [com.shelfsnap.app.data.local.LocalAnalysisProgressTracker]'s
 * equivalent for local analysis. Single-slot, not keyed like that tracker: only one
 * `ItemDetailScreen` can be on screen — and therefore researching — at a time.
 */
@Singleton
class MarketResearchProgressTracker
    @Inject
    constructor() {
        // Process-scoped, not the caller's own coroutine: moving only the *display* state to
        // this singleton wasn't enough on its own — ItemDetailViewModel's viewModelScope is
        // cancelled the moment the user backs out of the item, and until launchResearch() below,
        // the actual network+LLM work was still tied to it, so pressing Back mid-research killed
        // the run outright even though the new nav-root footer suggested it would keep going.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var current: Deferred<*>? = null
        private val _state = MutableStateFlow<MarketResearchState?>(null)
        val state: StateFlow<MarketResearchState?> = _state.asStateFlow()

        /**
         * Runs [research] on [scope] instead of the caller's own coroutine. The returned
         * [Deferred] is not a child of the caller's job: awaiting it and having that await
         * cancelled (screen torn down again before this finishes) stops the *caller* from
         * waiting, but does not stop the research itself. This only needs to survive in-app
         * navigation, not the app being backgrounded entirely — unlike Scrybe's manual-retry
         * transcription, which needs the stronger guarantee a foreground `WorkManager` job gives
         * it (see `RetranscribeWorker`), because a research run is a single web-search-and-LLM
         * pass rather than something that can run for tens of minutes on-device. [ResearchProgress]
         * updates from [research]'s own progress callback flow into [state] the same way as
         * before.
         */
        fun <T> launchResearch(
            itemId: Long,
            research: suspend ((ResearchProgress) -> Unit) -> T,
        ): Deferred<T> {
            _state.value = MarketResearchState(itemId = itemId, progress = null, startedAtMs = System.currentTimeMillis())
            val deferred =
                scope.async {
                    try {
                        research { progress -> _state.update { it?.copy(progress = progress) } }
                    } finally {
                        _state.value = null
                    }
                }
            current = deferred
            return deferred
        }

        /** Stops the research run currently in flight, if any. */
        fun cancel() {
            current?.cancel()
        }
    }
