package com.twobits.localai

import android.os.Build

/**
 * A one-line device fingerprint — manufacturer/model, Android version, CPU ABI, total RAM — meant
 * to be written once per app launch to whichever app's Debug Log. On-device inference crashes are
 * heavily device/chipset dependent (a missing NNAPI delegate, a 32-bit-only ABI, a low-RAM OEM
 * skin with an aggressive low-memory killer), so a crash or failure entry with no idea which
 * device it happened on is far harder to reproduce or triage. Pure string, no dependency on any
 * app-specific `DebugLogEntry` type, for the same reason as [ModelDownloadDiagnostics] — this
 * module is shared across Scrybe, Shelf Snap, and PriceDrop's separately-compiled debug logs.
 */
object DeviceDiagnostics {
    fun summary(totalRamMb: Long?): String {
        val abis = Build.SUPPORTED_ABIS.joinToString("/")
        val ram = totalRamMb?.let { "$it MB RAM" } ?: "RAM unknown"
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · $abis · $ram"
    }
}
