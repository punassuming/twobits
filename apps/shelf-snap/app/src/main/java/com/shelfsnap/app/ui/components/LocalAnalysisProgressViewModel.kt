package com.shelfsnap.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.local.LocalAnalysisProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LocalAnalysisProgressUiState(
    val label: String? = null,
    val otherActiveCount: Int = 0,
)

@HiltViewModel
class LocalAnalysisProgressViewModel
    @Inject
    constructor(
        tracker: LocalAnalysisProgressTracker,
    ) : ViewModel() {
        val uiState: StateFlow<LocalAnalysisProgressUiState> =
            tracker.active
                .map { operations ->
                    LocalAnalysisProgressUiState(
                        label = operations.firstOrNull()?.label,
                        otherActiveCount = (operations.size - 1).coerceAtLeast(0),
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LocalAnalysisProgressUiState(),
                )
    }
