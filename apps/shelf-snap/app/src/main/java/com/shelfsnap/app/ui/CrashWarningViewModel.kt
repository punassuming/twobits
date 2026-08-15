package com.shelfsnap.app.ui

import androidx.lifecycle.ViewModel
import com.shelfsnap.app.data.local.DebugLogEntry
import com.shelfsnap.app.data.local.DebugLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
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
