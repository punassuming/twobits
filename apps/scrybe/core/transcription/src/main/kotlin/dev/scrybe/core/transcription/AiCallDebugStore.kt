package dev.scrybe.core.transcription

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
 * One network call made to an AI provider, for on-device diagnosis without adb — never contains
 * raw audio bytes or the full transcript/prompt text, only short redacted summaries.
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
)

/**
 * Rolling, file-backed log of the most recent [AiCallDebugEntry] calls across every AI feature
 * (transcription, diarization, insights, transforms) — a superset of the older per-session
 * [DiarizationDebugStore], for diagnosing failures that never reach a specific session's debug
 * record (e.g. a transcription call that fails before anything is persisted). Deliberately not in
 * Room, same reasoning as [DiarizationDebugStore]: diagnostic data should never force a schema
 * migration.
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
