package com.shelfsnap.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.remote.MarketResearchProgressTracker
import com.shelfsnap.app.data.remote.ResearchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MarketResearchProgressUiState(
    val itemId: Long? = null,
    val progress: ResearchProgress? = null,
    val startedAtMs: Long? = null,
)

@HiltViewModel
class MarketResearchProgressViewModel
    @Inject
    constructor(
        private val tracker: MarketResearchProgressTracker,
    ) : ViewModel() {
        val uiState: StateFlow<MarketResearchProgressUiState> =
            tracker.state
                .map { state ->
                    MarketResearchProgressUiState(
                        itemId = state?.itemId,
                        progress = state?.progress,
                        startedAtMs = state?.startedAtMs,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = MarketResearchProgressUiState(),
                )

        fun cancel() = tracker.cancel()
    }
