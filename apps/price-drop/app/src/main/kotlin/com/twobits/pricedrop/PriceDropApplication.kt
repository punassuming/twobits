package com.twobits.pricedrop

import android.app.Application
import com.twobits.localai.DeviceDiagnostics
import com.twobits.localai.LocalInferenceMemoryGuard
import com.twobits.pricedrop.data.local.DebugLogEntry
import com.twobits.pricedrop.data.local.DebugLogEntryType
import com.twobits.pricedrop.data.local.DebugLogStore
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.settings.SettingsPrefs
import com.twobits.pricedrop.work.PriceCheckScheduler
import com.twobits.pricedrop.work.PriceCheckWorker
import com.twobits.securestore.SharedCredentialId
import com.twobits.securestore.ipc.SharedCredentialClient
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PriceDropApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var providerSettingsStore: ProviderSettingsStore

    @Inject
    lateinit var credentialClient: SharedCredentialClient

    @Inject
    lateinit var debugLogStore: DebugLogStore

    override fun onCreate() {
        super.onCreate()
        debugLogStore.install()
        // A device fingerprint once per launch — on-device inference crashes are heavily
        // device/chipset dependent, so a crash entry with no idea which device it happened on is
        // far harder to reproduce or triage than one timestamped next to this. Written off the
        // main thread: every DebugLogStore write re-reads, re-parses and rewrites the whole log
        // file, and install() above has already done that twice before this point.
        appScope.launch(Dispatchers.IO) {
            debugLogStore.record(
                DebugLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    type = DebugLogEntryType.AI_CALL,
                    op = "app-launch",
                    endpoint = "device-info",
                    requestSummary = DeviceDiagnostics.summary(LocalInferenceMemoryGuard.snapshot(this@PriceDropApplication)?.totalMb),
                    success = true,
                ),
            )
        }
        val deps = EntryPointAccessors.fromApplication(this, PriceCheckWorker.Deps::class.java)
        deps.notifier().ensureChannels()
        appScope.launch {
            if (providerSettingsStore.migrateCouponProvider()) {
                credentialClient.mirror(SharedCredentialId.COUPON, "")
            }
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
