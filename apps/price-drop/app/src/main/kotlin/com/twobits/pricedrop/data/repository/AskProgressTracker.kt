package com.twobits.pricedrop.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
        // Process-scoped, not the caller's own coroutine: moving only the *display* state to
        // this singleton wasn't enough on its own — AskViewModel's viewModelScope is cancelled
        // the moment the user backs out of the Ask screen, and until launchSend() below, the
        // actual chat call was still tied to it, so pressing Back mid-send killed the reply
        // outright even though the new nav-root footer suggested it would keep going.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var current: Deferred<*>? = null
        private val _state = MutableStateFlow<AskProgressState?>(null)
        val state: StateFlow<AskProgressState?> = _state.asStateFlow()

        /**
         * Runs [send] on [scope] instead of the caller's own coroutine. The returned [Deferred]
         * is not a child of the caller's job: awaiting it and having that await cancelled (screen
         * torn down again before this finishes) stops the *caller* from waiting, but does not
         * stop the send itself. This only needs to survive in-app navigation, not the app being
         * backgrounded entirely — unlike Scrybe's manual-retry transcription, which needs the
         * stronger guarantee a foreground `WorkManager` job gives it (see `RetranscribeWorker`),
         * because one chat reply is a single, usually-quick call rather than something that can
         * run for tens of minutes on-device.
         */
        fun <T> launchSend(
            label: String,
            send: suspend () -> T,
        ): Deferred<T> {
            _state.value = AskProgressState(label = label, startedAtMs = System.currentTimeMillis())
            val deferred =
                scope.async {
                    try {
                        send()
                    } finally {
                        _state.value = null
                    }
                }
            current = deferred
            return deferred
        }

        /** Stops the send() call currently in flight, if any. */
        fun cancel() {
            current?.cancel()
        }
    }
