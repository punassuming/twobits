package dev.scrybe.core.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.scrybe.core.database.SCRYBE_DATABASE_VERSION

/**
 * Runs a backup, restore or export in the background.
 *
 * A worker rather than `viewModelScope.launch` for the same reason model downloads are: a recording
 * history is gigabytes and the copy outlives the screen. Follows `ModelDownloadWorker` exactly —
 * plain [CoroutineWorker] with a Hilt [EntryPoint] rather than `@HiltWorker`, so no custom
 * `WorkerFactory` is needed.
 *
 * The passphrase is **not** in `inputData`. WorkManager persists input to its own database, so a
 * passphrase passed that way would be written to disk in the clear and outlive the operation. It
 * comes through [BackupPassphraseHolder] in memory instead.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backupWriter(): BackupWriter

        fun backupReader(): BackupReader

        fun recordingExporter(): RecordingExporter

        fun progressTracker(): BackupProgressTracker

        fun passphraseHolder(): BackupPassphraseHolder
    }

    override suspend fun doWork(): Result {
        ensureChannel()
        val operation = inputData.getString(KEY_OPERATION)?.let { runCatching { BackupOperation.valueOf(it) }.getOrNull() }
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse)
        if (operation == null || uri == null) return Result.failure()

        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val tracker = deps.progressTracker()
        val passphrase = deps.passphraseHolder().take()

        tracker.start(operation)
        setForeground(foregroundInfo(operation, null))
        var summary: String? = null
        return try {
            summary =
                when (operation) {
                    BackupOperation.BACKUP -> runBackup(deps, uri, passphrase, tracker)
                    BackupOperation.RESTORE -> runRestore(deps, uri, passphrase, tracker)
                    BackupOperation.EXPORT -> runExport(deps, uri, tracker)
                }
            Result.success()
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // The message is written for the user — BackupFormatException and
            // BackupDecryptionException both carry text meant to be shown as-is.
            Result.failure(workDataOf(KEY_ERROR to (failure.message ?: "The operation could not be completed.")))
        } finally {
            passphrase?.fill('\u0000')
            tracker.finish(summary)
        }
    }

    private suspend fun runBackup(
        deps: Deps,
        uri: Uri,
        passphrase: CharArray?,
        tracker: BackupProgressTracker,
    ): String {
        val out = applicationContext.contentResolver.openOutputStream(uri) ?: error("Could not open the chosen file for writing.")
        val version =
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .versionName
                .orEmpty()
        val result =
            deps.backupWriter().write(
                destination = out,
                appVersionName = version,
                databaseSchemaVersion = SCRYBE_DATABASE_VERSION,
                passphrase = passphrase,
            ) { written, total -> tracker.update(written, total) }
        val missing =
            if (result.missingAudioCount > 0) {
                ", ${result.missingAudioCount} with no audio file left on this phone"
            } else {
                ""
            }
        return "Backed up ${result.sessionCount} recordings (${formatSize(result.totalAudioBytes)})$missing."
    }

    private suspend fun runRestore(
        deps: Deps,
        uri: Uri,
        passphrase: CharArray?,
        tracker: BackupProgressTracker,
    ): String {
        val input = applicationContext.contentResolver.openInputStream(uri) ?: error("Could not open the chosen file for reading.")
        val result =
            deps.backupReader().restore(
                source = input,
                currentDatabaseSchemaVersion = SCRYBE_DATABASE_VERSION,
                passphrase = passphrase,
            ) { restored, total -> tracker.update(restored, total) }
        return buildString {
            append("Restored ${result.sessionsRestored} recordings")
            if (result.sessionsAlreadyPresent > 0) append(", ${result.sessionsAlreadyPresent} already on this phone")
            if (result.sessionsMissingAudio > 0) append(", ${result.sessionsMissingAudio} without audio")
            append(".")
            // Said explicitly because a silently unconfigured provider looks like a broken app.
            if (result.providersNeedingKeys > 0) append(" Your API keys were not in the backup — add them again in Settings.")
        }
    }

    private suspend fun runExport(
        deps: Deps,
        uri: Uri,
        tracker: BackupProgressTracker,
    ): String {
        val result = deps.recordingExporter().exportAll(uri) { exported, total -> tracker.update(exported, total) }
        val skipped = if (result.skippedCount > 0) ", ${result.skippedCount} skipped" else ""
        return "Exported ${result.exportedCount} recordings (${formatSize(result.totalBytes)})$skipped."
    }

    private fun formatSize(bytes: Long): String =
        when {
            bytes >= GB -> "%.1f GB".format(bytes.toDouble() / GB)
            bytes >= MB -> "%.0f MB".format(bytes.toDouble() / MB)
            else -> "%.0f KB".format(bytes.toDouble() / KB)
        }

    private fun foregroundInfo(
        operation: BackupOperation,
        progressPercent: Int?,
    ): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            buildNotification(operation, progressPercent),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    private fun buildNotification(
        operation: BackupOperation,
        progressPercent: Int?,
    ): Notification {
        val title =
            when (operation) {
                BackupOperation.BACKUP -> "Backing up your recordings"
                BackupOperation.RESTORE -> "Restoring your recordings"
                BackupOperation.EXPORT -> "Exporting your recordings"
            }
        return NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(progressPercent?.let { "$it%" } ?: "Preparing…")
            .setProgress(100, progressPercent ?: 0, progressPercent == null)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backup & restore", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val KEY_ERROR = "error"
        const val WORK_NAME = "scrybe_backup"

        private const val KEY_OPERATION = "operation"
        private const val KEY_URI = "uri"
        private const val CHANNEL_ID = "backup_restore"
        private const val NOTIFICATION_ID = 4711
        private const val KB = 1024.0
        private const val MB = KB * 1024
        private const val GB = MB * 1024

        /**
         * [passphrase] is handed to [BackupPassphraseHolder] rather than the work request, and is
         * cleared by the worker once used.
         */
        fun enqueue(
            context: Context,
            operation: BackupOperation,
            uri: Uri,
            passphrase: CharArray? = null,
            passphraseHolder: BackupPassphraseHolder,
        ) {
            passphraseHolder.put(passphrase)
            val request =
                OneTimeWorkRequestBuilder<BackupWorker>()
                    .setInputData(workDataOf(KEY_OPERATION to operation.name, KEY_URI to uri.toString()))
                    .build()
            // REPLACE, not KEEP: these are explicit user actions, and a stale queued one should not
            // silently swallow the button press.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
