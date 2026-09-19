package com.twobits.common

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Which OS process the current code is running in.
 *
 * An Android app that declares a component with `android:process` runs in more than one process,
 * and **`Application.onCreate()` runs in every one of them** — the class is instantiated per
 * process, not per app. Anything in `onCreate` with a side effect outside that process's own memory
 * therefore happens more than once: installing a crash handler, opening a database, enqueuing
 * WorkManager jobs, reconciling rows left by a previous run, appending a launch entry to a shared
 * log file. Guarding on [isMainProcess] is how that is avoided; a second process that re-ran a
 * startup sweep could correct state the main process is actively using.
 */
object ProcessInfo {
    /**
     * True in the app's own process, false in any `android:process` sibling (`:inference` and the
     * like).
     *
     * The main process is the one whose name is exactly the package name; a declared sibling always
     * carries a suffix. [Application.getProcessName] is the supported reading from API 28; below
     * that the name comes from `/proc/self/cmdline`, which is what the framework itself read before
     * the API existed. If the name cannot be determined at all this answers true, because the
     * caller is then a single-process app and skipping its startup work would be the worse failure.
     */
    fun isMainProcess(context: Context): Boolean {
        val processName = currentProcessName(context) ?: return true
        return processName == context.packageName
    }

    private fun currentProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return runCatching {
            // Documented as NUL-padded, so the trailing bytes have to go before comparing.
            File("/proc/self/cmdline")
                .readText()
                .substringBefore('\u0000')
                .trim()
                .ifEmpty { null }
        }.getOrNull() ?: legacyProcessNameFromActivityManager(context)
    }

    /**
     * Last resort below API 28 when `/proc` cannot be read. Deprecated and rate-limited on newer
     * releases, which is exactly why it is not the first choice.
     */
    private fun legacyProcessNameFromActivityManager(context: Context): String? =
        runCatching {
            val pid = android.os.Process.myPid()
            context
                .getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }.getOrNull()
}
