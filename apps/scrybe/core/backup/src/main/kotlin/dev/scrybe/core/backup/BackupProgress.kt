package dev.scrybe.core.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which long-running backup operation is in flight. */
enum class BackupOperation { BACKUP, RESTORE, EXPORT }

/**
 * Progress of the running operation, or [idle] when there is none.
 *
 * [total] is 0 before the item count is known — a backup has to read the session list before it can
 * say how many there are, and a progress bar showing "1 of 0" is worse than an indeterminate one.
 */
data class BackupProgress(
    val operation: BackupOperation? = null,
    val completed: Int = 0,
    val total: Int = 0,
) {
    val isRunning: Boolean get() = operation != null
    val fraction: Float? get() = if (total > 0) completed.toFloat() / total else null

    companion object {
        val idle = BackupProgress()
    }
}

/**
 * Shared progress for the backup screen.
 *
 * A singleton `StateFlow` rather than WorkManager's `getWorkInfoByIdFlow`, because Scrybe does not
 * use WorkManager progress anywhere — `TranscriptionProgressViewModel` derives its state from Room
 * status rows instead. Matching the app's existing approach keeps one way of doing this rather than
 * two.
 */
@Singleton
class BackupProgressTracker
    @Inject
    constructor() {
        private val _progress = MutableStateFlow(BackupProgress.idle)
        val progress: StateFlow<BackupProgress> = _progress.asStateFlow()

        private val _lastResult = MutableStateFlow<String?>(null)

        /** A one-line summary of the last completed operation, or null if none has finished. */
        val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

        fun start(operation: BackupOperation) {
            _lastResult.value = null
            _progress.value = BackupProgress(operation = operation)
        }

        fun update(
            completed: Int,
            total: Int,
        ) {
            val current = _progress.value
            if (current.isRunning) _progress.value = current.copy(completed = completed, total = total)
        }

        /**
         * Ends the operation and leaves [lastResult] describing what it did, so the screen can say
         * "42 recordings backed up" rather than simply stopping. Null for a failure — the error
         * message is carried separately and is the more useful thing to show.
         */
        fun finish(result: String? = null) {
            _lastResult.value = result
            _progress.value = BackupProgress.idle
        }

        fun clearResult() {
            _lastResult.value = null
        }
    }

/**
 * Holds a passphrase in memory for the moment between the user typing it and the worker using it.
 *
 * Deliberately **not** passed through `WorkManager`'s `inputData`: that is persisted to
 * WorkManager's own database, so a passphrase sent that way would be written to disk in the clear
 * and survive the operation — which would defeat the point of offering encryption at all.
 *
 * [take] returns the value once and clears it, so a passphrase lives no longer than the operation
 * that needs it. Nothing here survives process death; a worker that resumes after one fails with a
 * missing-passphrase error and the user starts again, which is the correct outcome.
 */
@Singleton
class BackupPassphraseHolder
    @Inject
    constructor() {
        @Volatile
        private var passphrase: CharArray? = null

        fun put(value: CharArray?) {
            clear()
            passphrase = value
        }

        fun take(): CharArray? {
            val value = passphrase
            passphrase = null
            return value
        }

        fun clear() {
            passphrase?.fill('\u0000')
            passphrase = null
        }
    }
