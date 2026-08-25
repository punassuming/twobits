package dev.scrybe.core.transcription

import kotlinx.coroutines.Job
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

        fun register(
            sessionId: String,
            job: Job,
        ) {
            jobsBySessionId[sessionId] = job
        }

        // Conditional remove: guards against a fast retry re-registering sessionId with a new
        // job right before this fires, which would otherwise remove the new registration instead
        // of the stale one it actually belongs to.
        fun unregister(
            sessionId: String,
            job: Job,
        ) {
            jobsBySessionId.remove(sessionId, job)
        }

        /** Cancels every transcription currently tracked — the progress toast's single Cancel action. */
        fun cancelAll() {
            jobsBySessionId.values.toSet().forEach { it.cancel() }
        }
    }
