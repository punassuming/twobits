package com.shelfsnap.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.local.LocalAnalysisProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LocalAnalysisProgressViewModel
    @Inject
    constructor(
        tracker: LocalAnalysisProgressTracker,
    ) : ViewModel() {
        val label: StateFlow<String?> =
            tracker.label.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
    }
