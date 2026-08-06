package com.shelfsnap.app.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One AI inference call (local or cloud), for on-device diagnosis without adb — never contains
 * the raw photo, full prompt, or full response, only short summaries. Mirrors Scrybe's
 * `AiCallDebugEntry`.
 *
 * [op] values ending in "-start" are written synchronously *before* a risky call (a native
 * model load or inference that could crash the process outright) and have no matching
 * [success]/[durationMs] — [AiCallDebugStore.record] returning means the entry is already on
 * disk, so if the process dies before the matching completed entry is ever written, a dangling
 * "-start" entry with no successor is itself the diagnostic: it pinpoints exactly which call was
 * in flight, with what model/inputs, at the moment of the crash — the only way to see that at all
 * for a native fault, since no Kotlin exception handler runs in time to catch it.
 */
data class AiCallDebugEntry(
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
 * Rolling, file-backed log of the most recent [AiCallDebugEntry] calls across every AI feature
 * (vision analysis, listing generation, market research) — local and cloud alike, so their speed
 * and success rate can be compared directly. Deliberately not in Room: diagnostic data should
 * never force a schema migration.
 */
@Singleton
class AiCallDebugStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val gson = Gson()
        private val entryListType = object : TypeToken<List<AiCallDebugEntry>>() {}.type
        private val mutex = Mutex()
        private val file: File get() = File(context.filesDir, FILE_NAME)

        suspend fun record(entry: AiCallDebugEntry) {
            mutex.withLock {
                runCatching {
                    val updated = (readAllLocked() + entry).takeLast(MAX_ENTRIES)
                    file.writeText(gson.toJson(updated))
                }.onFailure { Log.w(TAG, "Failed to record AI call debug entry: ${it.javaClass.simpleName}") }
            }
        }

        suspend fun readAll(): List<AiCallDebugEntry> = mutex.withLock { readAllLocked() }

        suspend fun clear() {
            mutex.withLock { runCatching { file.delete() } }
        }

        private fun readAllLocked(): List<AiCallDebugEntry> =
            runCatching {
                if (!file.exists()) return emptyList()
                gson.fromJson<List<AiCallDebugEntry>>(file.readText(), entryListType) ?: emptyList()
            }.getOrElse { emptyList() }

        private companion object {
            const val TAG = "AiCallDebug"
            const val FILE_NAME = "ai_call_debug.json"
            const val MAX_ENTRIES = 100
        }
    }
