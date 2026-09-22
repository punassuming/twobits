package com.shelfsnap.app.data.remote

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
        private var job: Job? = null
        private val _state = MutableStateFlow<MarketResearchState?>(null)
        val state: StateFlow<MarketResearchState?> = _state.asStateFlow()

        /** Captures the calling coroutine's own [Job] so [cancel] has something to stop. */
        suspend fun start(itemId: Long) {
            job = currentCoroutineContext()[Job]
            _state.value = MarketResearchState(itemId = itemId, progress = null, startedAtMs = System.currentTimeMillis())
        }

        fun update(progress: ResearchProgress) {
            _state.update { it?.copy(progress = progress) }
        }

        fun finish() {
            job = null
            _state.value = null
        }

        /** Stops the research run currently in flight, if any. */
        fun cancel() {
            job?.cancel()
        }
    }
