package com.shelfsnap.app

import android.app.Application
import com.shelfsnap.app.data.local.DebugLogEntry
import com.shelfsnap.app.data.local.DebugLogEntryType
import com.shelfsnap.app.data.local.DebugLogStore
import com.twobits.localai.DeviceDiagnostics
import com.twobits.localai.LocalInferenceMemoryGuard
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShelfSnapApplication : Application() {
    @Inject lateinit var debugLogStore: DebugLogStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        debugLogStore.install()
        // A device fingerprint once per launch — on-device inference crashes are heavily
        // device/chipset dependent, so a crash entry with no idea which device it happened on is
        // far harder to reproduce or triage than one timestamped next to this. Written off the
        // main thread: every DebugLogStore write re-reads, re-parses and rewrites the whole log
        // file, and install() above has already done that twice before this point.
        appScope.launch {
            debugLogStore.record(
                DebugLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    type = DebugLogEntryType.AI_CALL,
                    op = "app-launch",
                    endpoint = "device-info",
                    requestSummary = DeviceDiagnostics.summary(LocalInferenceMemoryGuard.snapshot(this@ShelfSnapApplication)?.totalMb),
                    success = true,
                ),
            )
        }
    }
}
