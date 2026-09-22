package dev.scrybe.core.transcription

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the coroutine [Job] backing each in-flight [SessionTranscriptionCoordinator.transcribeSession]
 * call, keyed by session id, so the progress toast's Cancel action has something concrete to
 * cancel. Registered/unregistered entirely inside that one method — every caller (auto-transcribe,
 * manual retry, batch) funnels through it, so no call site needs to know this exists.
 *
 * A "transcribe selected" batch run calls `transcribeSession()` directly inside a `forEach`
 * within one launched coroutine — not via a nested `launch`/`async` — so the [Job] captured
 * there IS that outer batch coroutine's own job.
 * Cancelling it stops both the item currently in flight and every item still queued behind it,
 * not just the current one, which matches what a single visible Cancel action should do.
 */
@Singleton
class TranscriptionCancellationController
    @Inject
    constructor() {
        private val jobsBySessionId = ConcurrentHashMap<String, Job>()

        private val _activeSessionIds = MutableStateFlow<Set<String>>(emptySet())

        /**
         * Session ids with a live transcription job in *this* process, mirrored from
         * [jobsBySessionId] synchronously inside [register]/[unregister]. This exists because a
         * session's TRANSCRIBING row in the database is not a reliable liveness signal on its
         * own: a fast on-device transcription can write TRANSCRIBING and then overwrite it with
         * TRANSCRIBED before Room's `InvalidationTracker` gets a chance to run the observing
         * query in between, so a UI driven solely off that DB Flow can miss the whole thing. This
         * flow can't miss it — it flips synchronously the moment [register]/[unregister] run,
         * with no query/coalescing step in between. See [TranscriptionProgressViewModel] for the
         * consumer this was added for.
         */
        val activeSessionIds: StateFlow<Set<String>> = _activeSessionIds.asStateFlow()

        fun register(
            sessionId: String,
            job: Job,
        ) {
            jobsBySessionId[sessionId] = job
            _activeSessionIds.value = jobsBySessionId.keys.toSet()
        }

        // Conditional remove: guards against a fast retry re-registering sessionId with a new
        // job right before this fires, which would otherwise remove the new registration instead
        // of the stale one it actually belongs to.
        fun unregister(
            sessionId: String,
            job: Job,
        ) {
            jobsBySessionId.remove(sessionId, job)
            _activeSessionIds.value = jobsBySessionId.keys.toSet()
        }

        /** Cancels every transcription currently tracked — the progress toast's single Cancel action. */
        fun cancelAll() {
            jobsBySessionId.values.toSet().forEach { it.cancel() }
        }

        /**
         * Whether [sessionId] has a live, still-running transcription job in *this* process.
         * A session's `TRANSCRIBING` status in the database can otherwise not be told apart from
         * a stale row left by a killed process (see `ScrybeApplication`'s app-start reconciliation
         * for that case) — this is the check a caller needs before treating `TRANSCRIBING` as
         * definitely-stale, so it doesn't wrongly stomp a transcription that's actually running
         * right now just because it observed the status mid-flight.
         */
        fun isActive(sessionId: String): Boolean = jobsBySessionId[sessionId]?.isActive == true
    }
