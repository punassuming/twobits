package com.twobits.pricedrop.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.twobits.pricedrop.data.settings.SettingsPrefs
import java.util.concurrent.TimeUnit

/** Schedules the periodic background price check honoring the user's settings. */
object PriceCheckScheduler {
    private const val WORK_NAME = "pricedrop_price_check"

    fun schedule(
        context: Context,
        freqHours: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
    ) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(chargingOnly)
                .build()
        val boundedFreqHours =
            freqHours.coerceIn(SettingsPrefs.MIN_CHECK_FREQ_HOURS, SettingsPrefs.MAX_CHECK_FREQ_HOURS)
        val request =
            PeriodicWorkRequestBuilder<PriceCheckWorker>(boundedFreqHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
