package com.shelfsnap.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class LocalAnalysisOperation(
    val id: Long,
    val label: String,
)

/**
 * Tracks every local (on-device Gemma) listing/vision analysis currently running, for a footer
 * progress toast shown regardless of which screen triggered it. Keyed by an opaque per-operation
 * ID rather than a single shared label: `ItemDetailViewModel.refineAllListings()` calls
 * `refineListing()` once per draft platform, and each of those launches its own independent
 * `viewModelScope.launch {}` — so multiple [LocalListingService.refine]/[LocalVisionService.analyse]
 * calls can genuinely overlap. A single shared "is anything running" flag would have the first of
 * several concurrent calls to finish clear the toast while the others were still working.
 */
@Singleton
class LocalAnalysisProgressTracker
    @Inject
    constructor() {
        private val nextId = AtomicLong(0)
        private val _active = MutableStateFlow<List<LocalAnalysisOperation>>(emptyList())
        val active: StateFlow<List<LocalAnalysisOperation>> = _active.asStateFlow()

        /** Returns an ID to pass to [finish] once this specific operation completes. */
        fun start(label: String): Long {
            val id = nextId.incrementAndGet()
            _active.update { it + LocalAnalysisOperation(id, label) }
            return id
        }

        fun finish(id: Long) {
            _active.update { list -> list.filterNot { it.id == id } }
        }

        /** Updates one running operation without disturbing concurrent local analyses. */
        fun update(
            id: Long,
            label: String,
        ) {
            _active.update { list -> list.map { if (it.id == id) it.copy(label = label) else it } }
        }
    }
