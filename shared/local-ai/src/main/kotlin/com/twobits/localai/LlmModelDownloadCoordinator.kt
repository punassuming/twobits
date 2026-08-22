package com.twobits.localai

import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Download + install-state tracking for [LocalLlmModel] — every app that offers the on-device
 * Gemma model (Scrybe, Shelf Snap, PriceDrop) held a near-identical copy of this same state
 * flow / resolve-from-disk / download-orchestration / delete logic in its own `LocalModelManager`
 * before this was extracted; only what's genuinely app-specific (persisting which model is
 * *selected*, and — Scrybe only — the separate Whisper model family) stays app-owned. Each app's
 * `LocalModelManager` holds one instance of this.
 */
class LlmModelDownloadCoordinator(
    private val modelsDir: File,
    private val okHttpClient: OkHttpClient,
    private val diagnostics: ModelDownloadDiagnostics? = null,
) {
    init {
        // A .part file nobody has resumed in days is more likely abandoned than still wanted,
        // and unlike a Ready model file it's disk usage the UI never surfaces, so it can't
        // otherwise be noticed and cleaned up by hand.
        ModelDownloader.cleanupStalePartialFiles(modelsDir)
    }

    private val _states = MutableStateFlow(LocalLlmModel.entries.associateWith { resolveState(it) })
    val states: StateFlow<Map<LocalLlmModel, LocalModelState>> = _states.asStateFlow()

    fun file(model: LocalLlmModel): File? {
        val f = File(modelsDir, model.fileName)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun anyReady(predicate: (LocalLlmModel) -> Boolean = { true }): LocalLlmModel? =
        LocalLlmModel.entries.firstOrNull { predicate(it) && file(it) != null }

    private fun resolveState(model: LocalLlmModel): LocalModelState =
        file(model)?.let { LocalModelState.Ready(it.absolutePath) } ?: LocalModelState.Absent

    suspend fun download(model: LocalLlmModel) {
        if (_states.value[model] is LocalModelState.Acquiring) return
        withContext(Dispatchers.IO) {
            val destFile = File(modelsDir, model.fileName)
            val startedAtMs = System.currentTimeMillis()
            try {
                update(model, LocalModelState.Acquiring(0))
                ModelDownloader.downloadFile(
                    okHttpClient = okHttpClient,
                    url = model.downloadUrl,
                    dest = destFile,
                    expectedSha256 = model.sha256,
                ) { progress -> update(model, LocalModelState.Acquiring(progress)) }
                update(model, resolveState(model))
                diagnostics?.record(model, success = true, message = null, stackTraceText = null, durationMs = System.currentTimeMillis() - startedAtMs)
            } catch (e: Exception) {
                // The in-progress .part file is deliberately left alone here — downloadFile
                // already discarded it for unrecoverable failures (bad checksum, HTTP 4xx, out
                // of space) and otherwise preserved it so the next attempt (Retry, or an
                // automatic future retry) resumes instead of starting over.
                update(model, LocalModelState.Error(e.message ?: "Download failed"))
                diagnostics?.record(
                    model,
                    success = false,
                    message = e.message ?: "Download failed",
                    stackTraceText = e.stackTraceToString(),
                    durationMs = System.currentTimeMillis() - startedAtMs,
                )
            }
        }
    }

    fun delete(model: LocalLlmModel) {
        val destFile = File(modelsDir, model.fileName)
        destFile.delete()
        ModelDownloader.deletePartialFile(destFile)
        update(model, LocalModelState.Absent)
    }

    /**
     * Names under [modelsDir] this coordinator's downloads produce or are entitled to leave
     * behind (a finished file, or a `.part` still in progress) — a caller that shares
     * [modelsDir] with another download family entirely (Scrybe's Whisper archives, downloaded
     * to the same directory) needs this to correctly tell "someone else's file" apart from "an
     * orphan of ours" when scanning the whole directory, since this coordinator only ever
     * tracks [LocalLlmModel] entries by name, never the directory's actual contents.
     */
    fun knownFileNames(): Set<String> = LocalLlmModel.entries.flatMap { listOf(it.fileName, "${it.fileName}.part") }.toSet()

    /** Bytes under [modelsDir] that don't belong to any current [LocalLlmModel] — see [knownFileNames]. */
    fun orphanedStorageBytes(): Long = ModelDownloader.orphanedBytes(modelsDir, knownFileNames())

    /** Deletes every orphaned entry under [modelsDir] and returns the bytes reclaimed. */
    fun deleteOrphanedFiles(): Long = ModelDownloader.deleteOrphanedEntries(modelsDir, knownFileNames())

    private fun update(
        model: LocalLlmModel,
        state: LocalModelState,
    ) {
        _states.value = _states.value + (model to state)
    }
}
