package com.twobits.pricedrop.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One AI inference call (local or cloud), for on-device diagnosis without adb — never contains
 * the full prompt/response, only short summaries. Mirrors Scrybe's `AiCallDebugEntry`.
 *
 * [op] values ending in "-start" are written synchronously *before* a risky call (a native model
 * load or inference that could crash the process outright) and have no matching
 * [success]/[durationMs] — [AiCallDebugStore.record] returning means the entry is already on
 * disk, so if the process dies before the matching completed entry is ever written, a dangling
 * "-start" entry with no successor is itself the diagnostic: it pinpoints exactly which call was
 * in flight at the moment of the crash, since no Kotlin exception handler runs in time to catch
 * a native fault.
 */
@Serializable
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
 * (Ask, product search) — local and cloud alike, so their speed and success rate can be compared
 * directly. Deliberately not in Room: diagnostic data should never force a schema migration.
 */
@Singleton
class AiCallDebugStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) {
        private val mutex = Mutex()
        private val file: File get() = File(context.filesDir, FILE_NAME)

        suspend fun record(entry: AiCallDebugEntry) {
            mutex.withLock {
                runCatching {
                    val updated = (readAllLocked() + entry).takeLast(MAX_ENTRIES)
                    file.writeText(json.encodeToString(ListSerializer(AiCallDebugEntry.serializer()), updated))
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
                json.decodeFromString(ListSerializer(AiCallDebugEntry.serializer()), file.readText())
            }.getOrElse { emptyList() }

        private companion object {
            const val TAG = "AiCallDebug"
            const val FILE_NAME = "ai_call_debug.json"
            const val MAX_ENTRIES = 100
        }
    }
