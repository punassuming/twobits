package dev.scrybe.core.transcription

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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

/**
 * Runs a manual "retry transcription" (the session-detail screen's retry button) as a
 * foreground-promoted worker so it survives the screen turning off or the app backgrounding —
 * the same reason SharedModelDownloadWorker and BackupWorker are workers rather than
 * `viewModelScope.launch`. A retry has no other durable owner: it isn't part of an active
 * recording (no [dev.scrybe.service.recording.RecordingForegroundService] to run inside of), and
 * an on-device transcription of a long recording can run for many minutes — easily longer than
 * the user is willing to keep the screen open and watching. Follows
 * SharedModelDownloadWorker/BackupWorker exactly — plain [CoroutineWorker] with a Hilt [EntryPoint]
 * rather than `@HiltWorker`, so no custom `WorkerFactory` is needed.
 *
 * mediaProcessing, not dataSync: this is the identical operation
 * [dev.scrybe.service.recording.RecordingForegroundService] already runs inline and classifies
 * that way when auto-transcribe fires right after a recording stops; this worker is the same
 * transcription call, just triggered manually with no live recording behind it.
 */
class RetranscribeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun coordinator(): SessionTranscriptionCoordinator
    }

    override suspend fun doWork(): Result {
        ensureChannel(applicationContext)
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        setForeground(foregroundInfo())
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        return deps
            .coordinator()
            .transcribeSession(sessionId)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.failure(workDataOf(KEY_ERROR to (it.message ?: "Transcription failed"))) },
            )
    }

    private fun foregroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
        )

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("Transcribing…")
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val KEY_ERROR = "error"

        private const val KEY_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "retranscribe"
        private const val NOTIFICATION_ID = 6201

        /**
         * Unique work keyed by [sessionId], KEEP policy: a double-tap of the retry button (or a
         * screen recomposition re-invoking this) joins the already-running attempt instead of
         * restarting a possibly long transcription from scratch.
         */
        fun enqueue(
            context: Context,
            sessionId: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<RetranscribeWorker>()
                    .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("retranscribe_$sessionId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
