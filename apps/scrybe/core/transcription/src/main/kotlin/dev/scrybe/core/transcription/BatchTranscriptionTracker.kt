package dev.scrybe.core.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many sessions are still waiting behind the one currently transcribing in a
 * "transcribe selected" batch, which processes one at a time. A DB status query alone
 * can't answer this: only the session actually being worked on is ever `TRANSCRIBING`, so
 * the rest of the batch is indistinguishable from "nothing else pending" until this
 * tracker's caller decrements it as each one finishes.
 */
@Singleton
class BatchTranscriptionTracker
    @Inject
    constructor() {
        private val _remaining = MutableStateFlow(0)
        val remaining: StateFlow<Int> = _remaining.asStateFlow()

        fun setRemaining(count: Int) {
            _remaining.value = count
        }

        fun decrement() {
            _remaining.value = (_remaining.value - 1).coerceAtLeast(0)
        }
    }
