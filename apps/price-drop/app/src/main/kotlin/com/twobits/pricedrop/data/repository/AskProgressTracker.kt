package com.twobits.pricedrop.data.repository

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AskProgressState(
    val label: String,
    val startedAtMs: Long,
)

/**
 * Live state for [com.twobits.pricedrop.ui.ask.AskViewModel.send], visible regardless of which
 * screen is showing. Previously a plain `isLoading` boolean on that screen's own `AskUiState`
 * with no cross-screen visibility and no cancel — the one long-running task in PriceDrop with
 * neither, unlike Scrybe's transcription or Shelf Snap's local analysis / market research.
 */
@Singleton
class AskProgressTracker
    @Inject
    constructor() {
        private var job: Job? = null
        private val _state = MutableStateFlow<AskProgressState?>(null)
        val state: StateFlow<AskProgressState?> = _state.asStateFlow()

        /** Captures the calling coroutine's own [Job] so [cancel] has something to stop. */
        suspend fun start(label: String) {
            job = currentCoroutineContext()[Job]
            _state.value = AskProgressState(label = label, startedAtMs = System.currentTimeMillis())
        }

        fun finish() {
            job = null
            _state.value = null
        }

        /** Stops the send() call currently in flight, if any. */
        fun cancel() {
            job?.cancel()
        }
    }
