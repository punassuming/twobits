package com.twobits.localai

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

/**
 * At most one native model resident per process, across *every* engine — LiteRT-LM and Whisper
 * alike.
 *
 * Overlapping local work is a documented, intended pattern for these apps (Shelf Snap fans out
 * listing refinement per platform while a vision analysis can still be running; Scrybe follows a
 * transcription with diarization and insights), and each of them would otherwise load its own
 * multi-gigabyte copy at the same time. The second caller waits for the first to close instead.
 *
 * Deliberately one gate shared by both engine types rather than one per engine: the failure it
 * exists to prevent is total resident bytes, and a Whisper model plus a LiteRT-LM model at once
 * is the same low-memory kill as two LiteRT-LM models at once.
 *
 * Not reentrant. Acquiring this while already holding it deadlocks, so no call path may load one
 * model inside another's [withGate] block. The bounded wait below turns a violation — or a leaked
 * release — into a reportable exception instead of a silent permanent hang.
 */
object LocalInferenceGate {
    private val gate = Mutex()

    /**
     * How long to wait for another feature's model to finish before giving up. Generous, because
     * a legitimate queue behind a slow generation is normal and worth waiting out — this exists to
     * convert a *leaked* gate from a permanent silent hang into a reportable failure, not to
     * police ordinary contention.
     */
    const val WAIT_TIMEOUT_MS = 180_000L

    /**
     * Takes the gate, or throws [LocalEngineBusyException]. The caller owns the release from here
     * and must pair every success with [release] — prefer [withGate], which cannot forget.
     */
    suspend fun acquire() {
        try {
            withTimeout(WAIT_TIMEOUT_MS) { gate.lock() }
        } catch (timeout: TimeoutCancellationException) {
            throw LocalEngineBusyException(WAIT_TIMEOUT_MS, timeout)
        }
    }

    fun release() = gate.unlock()

    /**
     * For a load-use-close caller. [LiteRtLmEngine.acquire] cannot use this — it hands the engine
     * back to its caller and so must release from [LiteRtLmEngine.close] instead.
     */
    suspend fun <T> withGate(block: suspend () -> T): T {
        acquire()
        return try {
            block()
        } finally {
            release()
        }
    }
}

/**
 * Only one model is resident per process, so a local call waits for the one ahead of it. Reaching
 * this means the wait was long enough that a leaked engine is the likelier explanation than a slow
 * one — the message is written for the person who sees it in an error banner.
 */
class LocalEngineBusyException(
    timeoutMs: Long,
    cause: Throwable,
) : RuntimeException(
        "Another on-device task has held the model for over ${timeoutMs / 60_000} minutes. " +
            "Reopen the app and try again.",
        cause,
    )
