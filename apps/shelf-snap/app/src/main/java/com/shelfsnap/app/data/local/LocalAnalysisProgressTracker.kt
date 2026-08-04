package com.shelfsnap.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether local (on-device Gemma) listing or vision analysis is currently running, for a
 * footer progress toast shown regardless of which screen triggered it (Camera, right after
 * capture, or an Item Detail re-analyze/refine) — same reasoning as Scrybe's equivalent
 * transcription-progress tracker: the work can outlive the screen that started it, and a user
 * shouldn't lose visibility into it just by navigating away. [LocalListingService] and
 * [LocalVisionService] update this directly around their own work, since they're already the
 * shared choke point every current and future caller goes through.
 */
@Singleton
class LocalAnalysisProgressTracker
    @Inject
    constructor() {
        private val _label = MutableStateFlow<String?>(null)
        val label: StateFlow<String?> = _label.asStateFlow()

        fun start(label: String) {
            _label.value = label
        }

        fun finish() {
            _label.value = null
        }
    }
