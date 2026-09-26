package com.twobits.localai.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.localai.LlmDownloadSource
import com.twobits.localai.runLlmDownload
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs a Gemma download as a foreground-promoted worker so it survives the screen turning off or
 * the app backgrounding — see [runLlmDownload]'s doc for why a plain `viewModelScope.launch` (the
 * previous approach) can't guarantee that. Uses a Hilt [EntryPoint] targeting the shared
 * [LlmDownloadSource] interface (not any one app's concrete `LocalModelManager` type), so no
 * custom `WorkerFactory` is needed and this single class replaces what used to be three
 * near-identical per-app copies. Each app must `@Binds` its own `LocalModelManager` to
 * [LlmDownloadSource] for the [Deps] entry point to resolve.
 *
 * The notification's channel is created idempotently on every [doWork] — safe to call every time
 * ([NotificationManager.createNotificationChannel] is a no-op if the channel already exists) — so
 * this doesn't depend on any app having already created it elsewhere.
 */
class SharedModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun llmDownloadSource(): LlmDownloadSource
    }

    override suspend fun doWork(): Result {
        val model = LocalLlmModel.fromName(inputData.getString(KEY_MODEL) ?: return Result.failure())
        val notificationIcon = inputData.getInt(KEY_NOTIFICATION_ICON, 0)
        val channelId = inputData.getString(KEY_CHANNEL_ID) ?: DEFAULT_CHANNEL_ID
        val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: DEFAULT_CHANNEL_NAME
        if (notificationIcon == 0) return Result.failure()
        ensureChannel(applicationContext, channelId, channelName)
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        return runLlmDownload(
            model = model,
            source = deps.llmDownloadSource(),
            notificationId = NOTIFICATION_ID,
            notification = { progressPercent -> buildNotification(channelId, notificationIcon, model, progressPercent) },
        )
    }

    private fun buildNotification(
        channelId: String,
        notificationIcon: Int,
        model: LocalLlmModel,
        progressPercent: Int,
    ): Notification =
        NotificationCompat
            .Builder(applicationContext, channelId)
            .setSmallIcon(notificationIcon)
            .setContentTitle("Downloading ${model.displayName}")
            .setContentText("$progressPercent%")
            .setProgress(100, progressPercent, progressPercent <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 5301
        private const val KEY_MODEL = "model"
        private const val KEY_NOTIFICATION_ICON = "notification_icon"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        const val DEFAULT_CHANNEL_ID = "model_download"
        const val DEFAULT_CHANNEL_NAME = "Model downloads"

        private fun ensureChannel(
            context: Context,
            channelId: String,
            channelName: String,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress for on-device AI model downloads."
                },
            )
        }

        /**
         * [notificationIcon] must be a resource ID from the calling app's own `R` class — valid at
         * runtime because the worker always executes inside that same app's process/`Context`.
         */
        fun enqueue(
            context: Context,
            model: LocalLlmModel,
            notificationIcon: Int,
            channelId: String = DEFAULT_CHANNEL_ID,
            channelName: String = DEFAULT_CHANNEL_NAME,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SharedModelDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_MODEL to model.name,
                            KEY_NOTIFICATION_ICON to notificationIcon,
                            KEY_CHANNEL_ID to channelId,
                            KEY_CHANNEL_NAME to channelName,
                        ),
                    ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("model_download_${model.name}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
