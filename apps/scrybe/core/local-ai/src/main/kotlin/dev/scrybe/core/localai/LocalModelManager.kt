package dev.scrybe.core.localai

import android.content.Context
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.localai.LlmDownloadSource
import com.twobits.localai.LlmModelDownloadCoordinator
import com.twobits.localai.ModelDownloadDiagnostics
import com.twobits.localai.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Scrybe's on-device models: Gemma (text) and Whisper (transcription). Gemma's
 * download/state-tracking logic lives in [LlmModelDownloadCoordinator], shared with Shelf Snap
 * and PriceDrop's equivalents; Whisper (archive download + extract) stays here since it's
 * Scrybe-only and a different acquisition shape entirely.
 */
@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val debugLogStore: DebugLogStore,
    ) : LlmDownloadSource {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val modelsDir: File = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
        private val llmCoordinator =
            LlmModelDownloadCoordinator(
                modelsDir,
                okHttpClient,
                diagnostics =
                    ModelDownloadDiagnostics { model, op, success, message, stackTraceText, durationMs ->
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                type = DebugLogEntryType.SERVICE_CALL,
                                op = op,
                                endpoint = model.downloadUrl,
                                model = model.fileName,
                                success = success,
                                responseSnippet = message,
                                durationMs = durationMs,
                                stackTrace = stackTraceText,
                            ),
                        )
                    },
            )

        private val _whisperStates =
            MutableStateFlow<Map<LocalWhisperModel, LocalModelState>>(
                LocalWhisperModel.entries.associateWith { LocalModelState.Absent },
            )
        val whisperStates: StateFlow<Map<LocalWhisperModel, LocalModelState>> = _whisperStates.asStateFlow()

        private val _selectedWhisperModel = MutableStateFlow(LocalWhisperModel.default)
        val selectedWhisperModel: StateFlow<LocalWhisperModel> = _selectedWhisperModel.asStateFlow()

        override val llmStates: StateFlow<Map<LocalLlmModel, LocalModelState>> = llmCoordinator.states

        init {
            refreshWhisperStates()
            scope.launch {
                preferencesDataStore.localWhisperModel.collect { model ->
                    _selectedWhisperModel.value = model
                }
            }
        }

        private fun refreshWhisperStates() {
            _whisperStates.value = LocalWhisperModel.entries.associateWith { resolveWhisperState(it) }
        }

        fun whisperModelDir(model: LocalWhisperModel): File? {
            val dir = File(modelsDir, model.dirName)
            return if (dir.exists() && dir.isDirectory) dir else null
        }

        fun activeWhisperDir(): Pair<File, LocalWhisperModel>? {
            val selected = _selectedWhisperModel.value
            whisperModelDir(selected)?.let { return it to selected }
            return LocalWhisperModel.entries.firstNotNullOfOrNull { m ->
                whisperModelDir(m)?.let { it to m }
            }
        }

        fun llmModelFile(model: LocalLlmModel): File? = llmCoordinator.file(model)

        fun anyLlmReady(): LocalLlmModel? = llmCoordinator.anyReady()

        private fun resolveWhisperState(model: LocalWhisperModel): LocalModelState =
            whisperModelDir(model)?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.Absent

        suspend fun downloadWhisper(model: LocalWhisperModel) {
            if (_whisperStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                try {
                    updateWhisperState(model, LocalModelState.Acquiring(0))
                    val archiveFile = File(modelsDir, model.archiveName)
                    ModelDownloader.downloadFile(okHttpClient, model.downloadUrl, archiveFile) { progress ->
                        updateWhisperState(model, LocalModelState.Acquiring(progress))
                    }
                    extractAndInstallWhisperArchive(model, archiveFile)
                    updateWhisperState(model, resolveWhisperState(model))
                    debugLogStore.record(
                        whisperLogEntry(model, op = "model-download", success = true, durationMs = System.currentTimeMillis() - startedAtMs),
                    )
                } catch (e: Exception) {
                    updateWhisperState(model, LocalModelState.Error(e.message ?: "Download failed"))
                    debugLogStore.record(
                        whisperLogEntry(
                            model,
                            op = "model-download",
                            success = false,
                            message = e.message ?: "Download failed",
                            stackTrace = e.stackTraceToString(),
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }
            }
        }

        /** Same install path as [downloadWhisper], sourced from a SAF-picked file instead of a network download. */
        suspend fun importWhisper(
            model: LocalWhisperModel,
            source: InputStream,
        ) {
            if (_whisperStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                try {
                    updateWhisperState(model, LocalModelState.Acquiring(0))
                    val archiveFile = File(modelsDir, model.archiveName)
                    val tempArchive = File(modelsDir, "${model.archiveName}.importing")
                    source.use { input -> tempArchive.outputStream().use { output -> input.copyTo(output) } }
                    if (!tempArchive.renameTo(archiveFile)) {
                        tempArchive.delete()
                        throw IOException("Couldn't finalize import (rename failed)")
                    }
                    extractAndInstallWhisperArchive(model, archiveFile)
                    updateWhisperState(model, resolveWhisperState(model))
                    debugLogStore.record(
                        whisperLogEntry(model, op = "model-import", success = true, durationMs = System.currentTimeMillis() - startedAtMs),
                    )
                } catch (e: Exception) {
                    updateWhisperState(model, LocalModelState.Error(e.message ?: "Import failed"))
                    debugLogStore.record(
                        whisperLogEntry(
                            model,
                            op = "model-import",
                            success = false,
                            message = e.message ?: "Import failed",
                            stackTrace = e.stackTraceToString(),
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }
            }
        }

        /**
         * Extracted to a temporary sibling directory and only moved into place with an atomic
         * rename on full success — model.dirName itself must never exist in a half-written
         * state, since resolveWhisperState() only checks dir.exists() and would otherwise report
         * an interrupted extraction as "Ready." A plain try/catch(Exception) around extraction
         * straight into dirName can't guarantee that: OutOfMemoryError (a real risk unpacking a
         * multi-GB Whisper Medium archive) is an Error, not an Exception, so it skips that catch
         * entirely, and a hard process kill runs neither catch nor finally block at all — only
         * "extraction never touches the real name until it's fully done" is safe against every
         * one of those. tempDir is cleaned up in the finally below regardless of how extraction
         * ends, including the now-empty husk left behind after a successful rename. Shared by
         * [downloadWhisper] and [importWhisper] — identical once the archive is on disk,
         * regardless of how it got there.
         */
        private fun extractAndInstallWhisperArchive(
            model: LocalWhisperModel,
            archiveFile: File,
        ) {
            val tempDir = File(modelsDir, ".tmp-${model.dirName}")
            try {
                tempDir.deleteRecursively()
                extractTarBz2(archiveFile, tempDir)
                val extractedDir = File(tempDir, model.dirName)
                if (!extractedDir.isDirectory) {
                    throw IOException("Archive ${model.archiveName} didn't produce the expected ${model.dirName} directory")
                }
                val finalDir = File(modelsDir, model.dirName)
                finalDir.deleteRecursively()
                if (!extractedDir.renameTo(finalDir)) {
                    throw IOException("Couldn't finalize Whisper model install (rename failed)")
                }
            } finally {
                tempDir.deleteRecursively()
                // The archive is scratch space either way once extraction has been attempted —
                // on success its contents are already on disk under dirName, and on failure
                // nothing downstream needs it either.
                archiveFile.delete()
            }
        }

        private fun whisperLogEntry(
            model: LocalWhisperModel,
            op: String,
            success: Boolean,
            message: String? = null,
            stackTrace: String? = null,
            durationMs: Long,
        ) = DebugLogEntry(
            timestampMs = System.currentTimeMillis(),
            type = DebugLogEntryType.SERVICE_CALL,
            op = op,
            endpoint = model.downloadUrl,
            model = model.archiveName,
            success = success,
            responseSnippet = message,
            durationMs = durationMs,
            stackTrace = stackTrace,
        )

        override suspend fun downloadLlm(model: LocalLlmModel) = llmCoordinator.download(model)

        suspend fun importLlm(
            model: LocalLlmModel,
            source: InputStream,
        ): Result<Unit> = llmCoordinator.importFrom(model, source)

        fun deleteWhisper(model: LocalWhisperModel) {
            File(modelsDir, model.dirName).deleteRecursively()
            File(modelsDir, ".tmp-${model.dirName}").deleteRecursively()
            // Belt-and-suspenders alongside downloadWhisper()'s own cleanup: a device already
            // carrying a pre-fix leaked archive (extraction failed before this existed, or the
            // process died before the finally block ran) still needs this delete button to
            // actually reclaim it.
            File(modelsDir, model.archiveName).delete()
            ModelDownloader.deletePartialFile(File(modelsDir, model.archiveName))
            updateWhisperState(model, LocalModelState.Absent)
        }

        fun deleteLlm(model: LocalLlmModel) = llmCoordinator.delete(model)

        /**
         * Names under [modelsDir] Whisper's downloads produce or are entitled to leave behind —
         * the extracted directory, the archive (transient, but see [downloadWhisper]'s cleanup;
         * a pre-fix leak may still be sitting on disk), and the archive's `.part`. Combined with
         * [LlmModelDownloadCoordinator.knownFileNames] (Gemma/Qwen share this same directory)
         * this is the full set of names [orphanedFileDetails]/[deleteOrphanedFiles] treat as
         * legitimate, so only genuinely unaccounted-for bytes get flagged.
         */
        private fun whisperKnownFileNames(): Set<String> = LocalWhisperModel.entries.flatMap { listOf(it.dirName, it.archiveName, "${it.archiveName}.part") }.toSet()

        private fun knownFileNames(): Set<String> = llmCoordinator.knownFileNames() + whisperKnownFileNames()

        fun orphanedFileDetails(): List<Pair<String, Long>> = ModelDownloader.orphanedEntries(modelsDir, knownFileNames()).map { it.name to ModelDownloader.sizeBytes(it) }

        fun deleteOrphanedFiles(): Long = ModelDownloader.deleteOrphanedEntries(modelsDir, knownFileNames())

        /** Every installed model file/directory on disk (Whisper + Gemma/Qwen), as (name, sizeBytes) pairs — for a storage viewer. */
        fun installedFileDetails(): List<Pair<String, Long>> =
            llmCoordinator.installedFileDetails() +
                LocalWhisperModel.entries.mapNotNull { model -> whisperModelDir(model)?.let { model.dirName to ModelDownloader.sizeBytes(it) } }

        /** Absolute path to the directory models are stored under — informational only; not independently browsable outside the app (Android scopes `Android/data/<package>` to this app). */
        fun storageDirPath(): String = modelsDir.absolutePath

        fun selectWhisperModel(model: LocalWhisperModel) {
            scope.launch { preferencesDataStore.setLocalWhisperModel(model) }
        }

        private fun updateWhisperState(
            model: LocalWhisperModel,
            state: LocalModelState,
        ) {
            _whisperStates.value = _whisperStates.value + (model to state)
        }

        private fun extractTarBz2(
            archive: File,
            destDir: File,
        ) {
            BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { tar.copyTo(it) }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
