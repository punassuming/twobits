package com.twobits.pricedrop.ui.product

import androidx.lifecycle.ViewModel
import com.twobits.pricedrop.data.repository.PriceCheckProgressState
import com.twobits.pricedrop.data.repository.PriceCheckProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PriceCheckProgressViewModel
    @Inject
    constructor(
        private val tracker: PriceCheckProgressTracker,
    ) : ViewModel() {
        val state: StateFlow<PriceCheckProgressState?> = tracker.state

        fun cancel() = tracker.cancel()
    }
