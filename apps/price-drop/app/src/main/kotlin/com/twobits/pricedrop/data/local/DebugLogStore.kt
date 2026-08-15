package com.twobits.pricedrop.data.local

import android.content.Context
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

@Serializable
enum class DebugLogEntryType { CRASH, AI_CALL, SERVICE_CALL }

/**
 * One entry in the unified on-device debug log — a crash, an AI inference call (local or
 * cloud), or a supporting service call — so the full timeline of what the app actually did can
 * be seen in one place, in order, without adb. Never contains raw prompt/response/page text,
 * only short summaries.
 *
 * A flat union of fields across every [type], not a sealed class — avoids kotlinx.serialization's
 * polymorphic-serialization ceremony while keeping [DebugLogStore] itself type-agnostic. Matches
 * Scrybe/Shelf Snap's identical entry shape.
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
 * ([install]) and AI call outcomes ([record]) — into one chronological timeline, so cause and
 * effect can actually be seen together instead of cross-referencing two different screens by eye.
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
         * it with). Read once at launch, not re-polled: the moment any new entry is recorded this
         * session, it's no longer the log's last entry, so this stays a one-shot per-process
         * signal without needing a separate "acknowledged" flag written back to disk.
         */
        private val _staleStartWarning = MutableStateFlow<DebugLogEntry?>(null)
        val staleStartWarning: StateFlow<DebugLogEntry?> = _staleStartWarning.asStateFlow()

        fun dismissStaleStartWarning() {
            _staleStartWarning.value = null
        }

        /** Idempotent — safe to call more than once. */
        fun install() {
            if (previousHandler != null) return
            migrateLegacyLogsIfPresent()
            _staleStartWarning.value = readAll().lastOrNull()?.takeIf { it.op?.endsWith("-start") == true }
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { write(crashEntry(thread, throwable)) }
                    .onFailure { Log.e(TAG, "Failed to record crash log entry", it) }
                previousHandler?.uncaughtException(thread, throwable)
            }
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
        }
    }
