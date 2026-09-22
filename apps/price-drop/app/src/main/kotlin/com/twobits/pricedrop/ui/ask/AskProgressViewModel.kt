package com.twobits.pricedrop.ui.ask

import androidx.lifecycle.ViewModel
import com.twobits.pricedrop.data.repository.AskProgressState
import com.twobits.pricedrop.data.repository.AskProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AskProgressViewModel
    @Inject
    constructor(
        private val tracker: AskProgressTracker,
    ) : ViewModel() {
        val state: StateFlow<AskProgressState?> = tracker.state

        fun cancel() = tracker.cancel()
    }
