package com.twobits.localai

import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs [model]'s download from inside a [CoroutineWorker.doWork], promoted to a foreground
 * service for the duration via [CoroutineWorker.setForeground] — WorkManager's own answer to
 * "must survive the screen turning off or the app backgrounding," which a plain
 * `viewModelScope.launch` is not: that scope survives configuration changes but not process
 * death, and isn't exempt from Doze/App Standby network suspension once the app loses
 * foreground status.
 *
 * [notification] builds the ongoing progress notification; it's called once up front at 0% and
 * again each time [source]'s progress changes, re-promoting the same notification ID each time
 * (a supported, documented way to update it — no separate NotificationManager reference needed
 * here). Each app supplies its own [source] (its `LocalModelManager`, reached from
 * [CoroutineWorker.getApplicationContext] via a Hilt [dagger.hilt.EntryPoint] — see
 * `PriceCheckWorker` for the pattern this follows, chosen specifically so apps don't need a
 * custom `WorkerFactory` or the AndroidX Hilt compiler) and its own notification appearance; the
 * foreground-promotion/progress-observation/result-mapping logic here is otherwise identical
 * across apps.
 *
 * Retries up to [maxAttempts] times (via [Result.retry], honoring WorkManager's own backoff
 * policy) before giving up with [Result.failure] — layered on top of, not a replacement for,
 * [ModelDownloader.downloadFile]'s own internal retry/resume: that one handles a single run's
 * transient network hiccups over seconds; this one handles the *worker itself* not surviving to
 * finish that run at all (a device reboot, the OS reclaiming it under severe memory pressure).
 */
suspend fun CoroutineWorker.runLlmDownload(
    model: LocalLlmModel,
    source: LlmDownloadSource,
    notificationId: Int,
    maxAttempts: Int = 3,
    notification: (progressPercent: Int) -> Notification,
): Result {
    suspend fun promote(progressPercent: Int) {
        setForeground(
            ForegroundInfo(
                notificationId,
                notification(progressPercent),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            ),
        )
    }

    promote(0)
    return coroutineScope {
        val progressJob =
            launch {
                source.llmStates
                    .map { it[model] }
                    .filterIsInstance<LocalModelState.Acquiring>()
                    .distinctUntilChangedBy { it.progressPercent }
                    .collect { promote(it.progressPercent) }
            }
        source.downloadLlm(model)
        progressJob.cancel()
        when (source.llmStates.value[model]) {
            is LocalModelState.Ready -> Result.success()
            else -> if (runAttemptCount < maxAttempts) Result.retry() else Result.failure()
        }
    }
}
