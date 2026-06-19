package com.twobits.pricedrop

import android.app.Application
import com.twobits.pricedrop.data.settings.SettingsPrefs
import com.twobits.pricedrop.work.PriceCheckScheduler
import com.twobits.pricedrop.work.PriceCheckWorker
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class PriceDropApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val deps = EntryPointAccessors.fromApplication(this, PriceCheckWorker.Deps::class.java)
        deps.notifier().ensureChannels()
        appScope.launch {
            val prefs = deps.dataStore().data.first()
            PriceCheckScheduler.schedule(
                context = this@PriceDropApplication,
                freqHours = prefs[SettingsPrefs.CHECK_FREQ] ?: SettingsPrefs.DEFAULT_CHECK_FREQ_HOURS,
                wifiOnly = prefs[SettingsPrefs.WIFI_ONLY] ?: false,
                chargingOnly = prefs[SettingsPrefs.CHARGING_ONLY] ?: false,
            )
        }
    }
}
