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
        private val startedAtMsBySessionId = ConcurrentHashMap<String, Long>()

        // Guards the map-mutate + snapshot-read + StateFlow-publish sequence in register()/
        // unregister() as one atomic unit. Each of those three steps is individually safe
        // (ConcurrentHashMap, StateFlow.value), but the sequence as a whole is not: two
        // concurrent calls (e.g. auto-transcribe and a manual retry finishing within moments of
        // each other) could each mutate the map, then read+publish a snapshot in an order that
        // doesn't match their mutations — a snapshot read *before* the other call's mutation can
        // still publish *after* it, overwriting a correct, newer snapshot with a stale one. Worst
        // case: the last publish is a non-empty set even though the map is actually empty, and
        // the footer never clears.
        private val lock = Any()

        private val _activeSessionIds = MutableStateFlow<Set<String>>(emptySet())
        private val _earliestStartedAtMs = MutableStateFlow<Long?>(null)

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

        /**
         * When the oldest currently-running transcription in this process started, or null if
         * none are running. The minimum, not any one particular session's start time: the footer
         * shows one aggregate elapsed-time reading regardless of how many sessions are active at
         * once (a "transcribe selected" batch, or auto-transcribe racing a manual retry), and "how
         * long has this whole thing been going" is the more useful reading of the two in that case.
         */
        val earliestStartedAtMs: StateFlow<Long?> = _earliestStartedAtMs.asStateFlow()

        fun register(
            sessionId: String,
            job: Job,
        ) {
            synchronized(lock) {
                jobsBySessionId[sessionId] = job
                startedAtMsBySessionId[sessionId] = System.currentTimeMillis()
                _activeSessionIds.value = jobsBySessionId.keys.toSet()
                _earliestStartedAtMs.value = startedAtMsBySessionId.values.minOrNull()
            }
        }

        // Conditional remove: guards against a fast retry re-registering sessionId with a new
        // job right before this fires, which would otherwise remove the new registration instead
        // of the stale one it actually belongs to.
        fun unregister(
            sessionId: String,
            job: Job,
        ) {
            synchronized(lock) {
                val removed = jobsBySessionId.remove(sessionId, job)
                if (removed) startedAtMsBySessionId.remove(sessionId)
                _activeSessionIds.value = jobsBySessionId.keys.toSet()
                _earliestStartedAtMs.value = startedAtMsBySessionId.values.minOrNull()
            }
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
