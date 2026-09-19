package com.twobits.debuglogui

import androidx.lifecycle.ViewModel
import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Backs each app's own crash-warning dialog. Only the ViewModel is shared: the dialog's wording
 * names the app and the specific feature that died ("on-device transcription" vs "Ask" vs
 * "analysis"), so `CrashWarningDialog` stays per-app while this — which was byte-identical in all
 * three — does not.
 */
@HiltViewModel
class CrashWarningViewModel
    @Inject
    constructor(
        private val debugLogStore: DebugLogStore,
    ) : ViewModel() {
        val staleStartWarning: StateFlow<DebugLogEntry?> = debugLogStore.staleStartWarning

        fun dismiss() = debugLogStore.dismissStaleStartWarning()
    }
