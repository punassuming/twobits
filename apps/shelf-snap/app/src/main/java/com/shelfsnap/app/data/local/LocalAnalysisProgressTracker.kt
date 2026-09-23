package com.shelfsnap.app.data.local

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class LocalAnalysisOperation(
    val id: Long,
    val label: String,
    val startedAtMs: Long,
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
        private val jobsById = ConcurrentHashMap<Long, Job>()
        private val _active = MutableStateFlow<List<LocalAnalysisOperation>>(emptyList())
        val active: StateFlow<List<LocalAnalysisOperation>> = _active.asStateFlow()

        /**
         * Returns an ID to pass to [update]/[finish] once this specific operation completes.
         * Suspend, not a plain function taking a `Job` parameter: it captures the calling
         * coroutine's own [Job] internally (mirroring Scrybe's
         * `SessionTranscriptionCoordinator.transcribeSession`), so every call site keeps calling
         * this exactly as before while [cancelAll] gains something real to stop.
         */
        suspend fun start(label: String): Long {
            val id = nextId.incrementAndGet()
            currentCoroutineContext()[Job]?.let { jobsById[id] = it }
            _active.update { it + LocalAnalysisOperation(id, label, System.currentTimeMillis()) }
            return id
        }

        fun finish(id: Long) {
            jobsById.remove(id)
            _active.update { list -> list.filterNot { it.id == id } }
        }

        /** Updates one running operation without disturbing concurrent local analyses. */
        fun update(
            id: Long,
            label: String,
        ) {
            _active.update { list -> list.map { if (it.id == id) it.copy(label = label) else it } }
        }

        /** Stops every local analysis currently tracked — the footer's single Cancel action. */
        fun cancelAll() {
            jobsById.values.toList().forEach { it.cancel() }
        }
    }
