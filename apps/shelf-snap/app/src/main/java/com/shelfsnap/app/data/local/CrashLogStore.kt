package com.shelfsnap.app.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/** One uncaught exception that would otherwise have only shown up as a truncated banner message. */
data class CrashLogEntry(
    val timestampMs: Long,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
)

/**
 * Rolling, file-backed log of uncaught exceptions, installed as the process's
 * [Thread.UncaughtExceptionHandler] from `ShelfSnapApplication.onCreate()`. Captures anything
 * that reaches the top of any thread uncaught, including bugs outside any try/catch, with the
 * full stack trace, not just a truncated `.message`.
 *
 * All I/O here is deliberately synchronous, not suspend: a crash handler runs on the crashing
 * thread with the process about to die, so there's no time to hop dispatchers or await anything.
 */
@Singleton
class CrashLogStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val gson = Gson()
        private val entryListType = object : TypeToken<List<CrashLogEntry>>() {}.type
        private val file: File get() = File(context.filesDir, FILE_NAME)
        private var previousHandler: Thread.UncaughtExceptionHandler? = null

        /** Idempotent — safe to call more than once. */
        fun install() {
            if (previousHandler != null) return
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { recordSync(thread, throwable) }
                    .onFailure { Log.e(TAG, "Failed to record crash log entry", it) }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        /** For failures a caller already caught and only reported as a short message elsewhere. */
        fun record(throwable: Throwable) {
            runCatching { recordSync(Thread.currentThread(), throwable) }
                .onFailure { Log.e(TAG, "Failed to record crash log entry", it) }
        }

        fun readAll(): List<CrashLogEntry> =
            runCatching {
                if (!file.exists()) return emptyList()
                gson.fromJson<List<CrashLogEntry>>(file.readText(), entryListType) ?: emptyList()
            }.getOrElse { emptyList() }

        fun clear() {
            runCatching { file.delete() }
        }

        private fun recordSync(
            thread: Thread,
            throwable: Throwable,
        ) {
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val entry =
                CrashLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    threadName = thread.name,
                    exceptionType = throwable.javaClass.name,
                    message = throwable.message,
                    stackTrace = stackTrace,
                )
            val updated = (readAll() + entry).takeLast(MAX_ENTRIES)
            file.writeText(gson.toJson(updated))
        }

        private companion object {
            const val TAG = "CrashLog"
            const val FILE_NAME = "crash_log.json"
            const val MAX_ENTRIES = 20
        }
    }
