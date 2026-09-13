package dev.scrybe.core.transcription

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

enum class DebugLogEntryType { CRASH, AI_CALL, SERVICE_CALL }

/**
 * One entry in the unified on-device debug log — a crash, an AI inference call (local or
 * cloud), or a supporting service call (web search, page reads) — so the full timeline of what
 * the app actually did can be seen in one place, in order, without adb. Never contains raw
 * audio/photo bytes or full prompt/response/page text, only short summaries.
 *
 * A flat union of fields across every [type], not a sealed class: kotlinx.serialization's
 * polymorphic support needs extra registration/annotation ceremony this handful of optional
 * fields doesn't warrant, and it keeps [DebugLogStore] itself type-agnostic.
 *
 * [op] values ending in "-start" are written *before* a risky call (a native model load or
 * inference that could crash the process outright) with no matching [success]/[durationMs] yet —
 * [DebugLogStore.record] returning means the entry is already on disk, so if the process dies
 * before the matching completed entry is ever written, a dangling "-start" entry with no
 * successor is itself the diagnostic: it pinpoints exactly which call was in flight, with what
 * model/inputs, at the moment of the crash — the only way to see that at all for a native fault,
 * since no Kotlin exception handler runs in time to catch it.
 */
@Serializable
data class DebugLogEntry(
    val timestampMs: Long,
    val type: DebugLogEntryType,
    // AI_CALL / SERVICE_CALL
    val op: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val requestSummary: String? = null,
    val success: Boolean? = null,
    val httpStatus: Int? = null,
    val responseSnippet: String? = null,
    val durationMs: Long? = null,
    // CRASH
    val threadName: String? = null,
    val exceptionType: String? = null,
    val message: String? = null,
    val stackTrace: String? = null,
)

/** Pre-merge `CrashLogStore` schema — kept only to decode `crash_log.json` during migration. */
@Serializable
private data class LegacyCrashLogEntry(
    val timestampMs: Long,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
)

/** Pre-merge `AiCallDebugStore` schema — kept only to decode `ai_call_debug.json` during migration. */
@Serializable
private data class LegacyAiCallDebugEntry(
    val timestampMs: Long,
    val op: String,
    val endpoint: String,
    val model: String? = null,
    val requestSummary: String,
    val success: Boolean,
    val httpStatus: Int? = null,
    val responseSnippet: String? = null,
    val durationMs: Long? = null,
)

/**
 * Rolling, file-backed log merging what were previously two separate signals — uncaught crashes
 * ([install]) and AI/service call outcomes ([record]) — into one chronological timeline, so
 * cause and effect (a service call that timed out right before a crash, say) can actually be
 * seen together instead of cross-referencing two different screens by eye.
 *
 * Deliberately synchronous throughout, not suspend: [install]'s crash handler runs on the
 * crashing thread with the process about to die, so there's no time to hop dispatchers or await
 * anything — every other write path is required to be just as synchronous so one lock protects
 * all of them. A caller on a suspend call path that cares about not blocking its own dispatcher
 * (e.g. reading the whole log for a settings screen) should wrap the call in
 * `withContext(Dispatchers.IO)` itself — this store makes no dispatcher decisions on its own.
 */
@Singleton
class DebugLogStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) {
        private val lock = Any()
        private val file: File get() = File(context.filesDir, FILE_NAME)
        private var previousHandler: Thread.UncaughtExceptionHandler? = null

        /**
         * Whether the log's last entry, as of [install], was a dangling "-start" with no
         * matching completion — the one signal available that a native crash killed the process
         * mid-inference last run (see [DebugLogEntry]'s doc comment; a native abort skips
         * [Thread.setDefaultUncaughtExceptionHandler] entirely, so there's nothing else to catch
         * it with). Read once at launch, not re-polled within this process. [dismissStaleStartWarning]
         * writes a closing entry so the dangling "-start" stops being the log's last entry —
         * without that, every future launch would re-detect the same unresolved marker and
         * re-show the warning indefinitely, not just once.
         */
        private val _staleStartWarning = MutableStateFlow<DebugLogEntry?>(null)
        val staleStartWarning: StateFlow<DebugLogEntry?> = _staleStartWarning.asStateFlow()

        fun dismissStaleStartWarning() {
            val entry = _staleStartWarning.value ?: return
            write(
                DebugLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    type = entry.type,
                    op = entry.op?.removeSuffix("-start"),
                    endpoint = entry.endpoint,
                    model = entry.model,
                    requestSummary = entry.requestSummary,
                    success = false,
                    responseSnippet = "Dismissed after an apparent crash — no completion was ever recorded for this call.",
                ),
            )
            _staleStartWarning.value = null
        }

        /** Idempotent — safe to call more than once. */
        fun install() {
            if (previousHandler != null) return
            migrateLegacyLogsIfPresent()
            // Process-exit entries (below) are appended after the fact and must not hide a
            // still-undismissed "-start" marker from an earlier launch.
            _staleStartWarning.value =
                readAll()
                    .lastOrNull { it.exceptionType != PROCESS_EXIT_TYPE }
                    ?.takeIf { it.op?.endsWith("-start") == true }
            recordPreviousExitReasonIfNew()
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { write(crashEntry(thread, throwable)) }
                    .onFailure { Log.e(TAG, "Failed to record crash log entry", it) }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        /**
         * The OS's own record of why this process last died — the one signal that tells a
         * low-memory kill apart from a native abort or an ANR, none of which ever reach
         * [Thread.setDefaultUncaughtExceptionHandler]. Read once per launch and de-duplicated
         * by the exit's own timestamp, so each death is recorded exactly once. Only abnormal
         * exits are recorded: a user swiping the app away, or the system trimming an idle
         * cached process, would otherwise bury the interesting ones. Pairs with the dangling
         * "-start" marker (see [DebugLogEntry]): that entry says what was running, this one
         * says why the process died.
         */
        private fun recordPreviousExitReasonIfNew() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            runCatching {
                val exit =
                    context
                        .getSystemService(ActivityManager::class.java)
                        ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                        ?.firstOrNull()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (exit != null && exit.timestamp > prefs.getLong(KEY_LAST_RECORDED_EXIT_TIMESTAMP, 0L)) {
                    prefs.edit().putLong(KEY_LAST_RECORDED_EXIT_TIMESTAMP, exit.timestamp).apply()
                    val reasonName =
                        when (exit.reason) {
                            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (killed by the low-memory killer)"
                            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (native abort or segfault)"
                            ApplicationExitInfo.REASON_CRASH -> "CRASH (uncaught exception)"
                            ApplicationExitInfo.REASON_ANR -> "ANR"
                            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (killed by signal ${exit.status})"
                            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
                            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
                            else -> null
                        }
                    if (reasonName != null) {
                        write(
                            DebugLogEntry(
                                timestampMs = exit.timestamp,
                                type = DebugLogEntryType.CRASH,
                                threadName = "process",
                                exceptionType = PROCESS_EXIT_TYPE,
                                message =
                                    "Previous run ended: $reasonName — ${exit.description ?: "no description"} " +
                                        "(importance ${exit.importance}, pss ${exit.pss / KB_PER_MB} MB)",
                            ),
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to record previous process exit reason: ${it.javaClass.simpleName}") }
        }

        /**
         * One-time upgrade path: entries recorded by the old, separate CrashLogStore/
         * AiCallDebugStore (before they merged into this one timeline) would otherwise vanish
         * the moment a user upgrades and opens the new Debug log screen — exactly when they're
         * most likely trying to diagnose a crash from right before the upgrade. Reads both
         * legacy files if present, converts their entries, merges them into the unified log in
         * timestamp order, then deletes the legacy files so this is a no-op on every later call.
         */
        private fun migrateLegacyLogsIfPresent() {
            synchronized(lock) {
                runCatching {
                    val legacyCrashFile = File(context.filesDir, LEGACY_CRASH_FILE_NAME)
                    val legacyAiCallFile = File(context.filesDir, LEGACY_AI_CALL_FILE_NAME)
                    if (!legacyCrashFile.exists() && !legacyAiCallFile.exists()) return

                    val migratedCrashes =
                        runCatching {
                            if (!legacyCrashFile.exists()) return@runCatching emptyList()
                            json
                                .decodeFromString(ListSerializer(LegacyCrashLogEntry.serializer()), legacyCrashFile.readText())
                                .map {
                                    DebugLogEntry(
                                        timestampMs = it.timestampMs,
                                        type = DebugLogEntryType.CRASH,
                                        threadName = it.threadName,
                                        exceptionType = it.exceptionType,
                                        message = it.message,
                                        stackTrace = it.stackTrace,
                                    )
                                }
                        }.getOrElse { emptyList() }

                    val migratedAiCalls =
                        runCatching {
                            if (!legacyAiCallFile.exists()) return@runCatching emptyList()
                            json
                                .decodeFromString(ListSerializer(LegacyAiCallDebugEntry.serializer()), legacyAiCallFile.readText())
                                .map {
                                    DebugLogEntry(
                                        timestampMs = it.timestampMs,
                                        type = DebugLogEntryType.AI_CALL,
                                        op = it.op,
                                        endpoint = it.endpoint,
                                        model = it.model,
                                        requestSummary = it.requestSummary,
                                        success = it.success,
                                        httpStatus = it.httpStatus,
                                        responseSnippet = it.responseSnippet,
                                        durationMs = it.durationMs,
                                    )
                                }
                        }.getOrElse { emptyList() }

                    if (migratedCrashes.isNotEmpty() || migratedAiCalls.isNotEmpty()) {
                        val existing =
                            runCatching {
                                if (!file.exists()) {
                                    emptyList()
                                } else {
                                    json.decodeFromString(ListSerializer(DebugLogEntry.serializer()), file.readText())
                                }
                            }.getOrElse { emptyList() }
                        val merged =
                            (existing + migratedCrashes + migratedAiCalls)
                                .sortedBy { it.timestampMs }
                                .takeLast(MAX_ENTRIES)
                        file.writeText(json.encodeToString(ListSerializer(DebugLogEntry.serializer()), merged))
                    }
                    legacyCrashFile.delete()
                    legacyAiCallFile.delete()
                }.onFailure { Log.w(TAG, "Failed to migrate legacy debug logs: ${it.javaClass.simpleName}") }
            }
        }

        /**
         * For failures a caller already caught and only reported as a short message elsewhere —
         * e.g. a UI error banner that only has room for `throwable.message`, so without this,
         * that failure would only ever be visible as a one-line banner with no full stack trace
         * to actually diagnose it from.
         */
        fun record(throwable: Throwable) {
            write(crashEntry(Thread.currentThread(), throwable))
        }

        fun record(entry: DebugLogEntry) = write(entry)

        fun readAll(): List<DebugLogEntry> =
            synchronized(lock) {
                runCatching {
                    if (!file.exists()) return emptyList()
                    json.decodeFromString(ListSerializer(DebugLogEntry.serializer()), file.readText())
                }.getOrElse { emptyList() }
            }

        fun clear() {
            synchronized(lock) { runCatching { file.delete() } }
        }

        private fun crashEntry(
            thread: Thread,
            throwable: Throwable,
        ): DebugLogEntry {
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            return DebugLogEntry(
                timestampMs = System.currentTimeMillis(),
                type = DebugLogEntryType.CRASH,
                threadName = thread.name,
                exceptionType = throwable.javaClass.name,
                message = throwable.message,
                stackTrace = stackTrace,
            )
        }

        private fun write(entry: DebugLogEntry) {
            synchronized(lock) {
                runCatching {
                    val existing =
                        runCatching {
                            if (!file.exists()) {
                                emptyList()
                            } else {
                                json.decodeFromString(ListSerializer(DebugLogEntry.serializer()), file.readText())
                            }
                        }.getOrElse { emptyList() }
                    val updated = (existing + entry).takeLast(MAX_ENTRIES)
                    file.writeText(json.encodeToString(ListSerializer(DebugLogEntry.serializer()), updated))
                }.onFailure { Log.w(TAG, "Failed to record debug log entry: ${it.javaClass.simpleName}") }
            }
        }

        private companion object {
            const val TAG = "DebugLog"
            const val FILE_NAME = "debug_log.json"
            const val LEGACY_CRASH_FILE_NAME = "crash_log.json"
            const val LEGACY_AI_CALL_FILE_NAME = "ai_call_debug.json"
            const val MAX_ENTRIES = 150
            const val PREFS_NAME = "debug_log_store"
            const val KEY_LAST_RECORDED_EXIT_TIMESTAMP = "last_recorded_exit_timestamp"
            const val PROCESS_EXIT_TYPE = "ProcessExit"
            const val KB_PER_MB = 1024L
        }
    }
