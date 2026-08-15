package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CrashWarningViewModel
    @Inject
    constructor(
        private val debugLogStore: DebugLogStore,
    ) : ViewModel() {
        val staleStartWarning: StateFlow<DebugLogEntry?> = debugLogStore.staleStartWarning

        fun dismiss() = debugLogStore.dismissStaleStartWarning()
    }
