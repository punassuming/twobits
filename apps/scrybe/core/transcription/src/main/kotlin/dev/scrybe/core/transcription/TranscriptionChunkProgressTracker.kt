package dev.scrybe.core.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How far the transcription currently in flight has gotten, in the one unit both paths already
 * produce: chunks. `WhisperTranscriptionProvider` (in `:core:local-ai`) reports as each of
 * WhisperEngine's in-memory sub-30s windows finishes decoding; [BatchTranscriptionService]
 * reports as each of OpenAI's per-request-cap file chunks finishes. Both already compute this —
 * the on-device path collects it only for a post-hoc debug-log timing summary, the cloud path
 * only to know which chunks to skip on a resumed retry — neither surfaced it live before this.
 *
 * The two producers are mutually exclusive at runtime (a transcription is local or cloud, never
 * both), so one shared, unkeyed slot is enough — [TranscriptionProgressViewModel] already
 * collapses "which session is transcribing" to a single current one the same way, via
 * `sessions.firstOrNull()`.
 */
@Singleton
class TranscriptionChunkProgressTracker
    @Inject
    constructor() {
        data class Progress(
            val completed: Int,
            val total: Int,
        )

        private val _progress = MutableStateFlow<Progress?>(null)
        val progress: StateFlow<Progress?> = _progress.asStateFlow()

        /**
         * [completed] is a count ("this many chunks are done"), not a 0-based index. A single-chunk
         * transcription (`total <= 1`) clears instead of reporting "chunk 1 of 1" — that fires right
         * as the whole call finishes, so it is never in-progress information, only noise on a clip
         * too short for progress to mean anything.
         */
        fun update(
            completed: Int,
            total: Int,
        ) {
            _progress.value = if (total > 1) Progress(completed, total) else null
        }

        /**
         * Call when a transcription ends, success or failure. Without this a stale reading lingers
         * into the next transcription's "Transcribing…" until that one's own first chunk lands.
         */
        fun clear() {
            _progress.value = null
        }
    }
