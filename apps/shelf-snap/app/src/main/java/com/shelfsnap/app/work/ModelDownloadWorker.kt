package com.shelfsnap.app.work

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
import com.shelfsnap.app.R
import com.shelfsnap.app.data.local.LocalModelManager
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.localai.runLlmDownload
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs a Gemma download as a foreground-promoted worker so it survives the screen turning off
 * or the app backgrounding — see [runLlmDownload]'s doc for why a plain `viewModelScope.launch`
 * (the previous approach) can't guarantee that. Uses the same Hilt [EntryPoint] pattern
 * PriceDrop's `PriceCheckWorker` follows, so no custom `WorkerFactory` is needed.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun localModelManager(): LocalModelManager
    }

    override suspend fun doWork(): Result {
        ensureChannel(applicationContext)
        val model = LocalLlmModel.fromName(inputData.getString(KEY_MODEL) ?: return Result.failure())
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        return runLlmDownload(
            model = model,
            source = deps.localModelManager(),
            notificationId = NOTIFICATION_ID,
            notification = { progressPercent -> buildNotification(model, progressPercent) },
        )
    }

    private fun buildNotification(
        model: LocalLlmModel,
        progressPercent: Int,
    ): Notification =
        NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading ${model.displayName}")
            .setContentText("$progressPercent%")
            .setProgress(100, progressPercent, progressPercent <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 6401
        private const val KEY_MODEL = "model"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress for on-device AI model downloads."
                },
            )
        }

        fun enqueue(
            context: Context,
            model: LocalLlmModel,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setInputData(workDataOf(KEY_MODEL to model.name))
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("model_download_${model.name}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
