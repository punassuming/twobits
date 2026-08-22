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
                    ModelDownloadDiagnostics { model, success, message, stackTraceText, durationMs ->
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                type = DebugLogEntryType.SERVICE_CALL,
                                op = "model-download",
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
                    // Extracted to a temporary sibling directory and only moved into place with
                    // an atomic rename on full success — model.dirName itself must never exist in
                    // a half-written state, since resolveWhisperState() only checks dir.exists()
                    // and would otherwise report an interrupted extraction as "Ready." A plain
                    // try/catch(Exception) around extraction straight into dirName can't guarantee
                    // that: OutOfMemoryError (a real risk unpacking a multi-GB Whisper Medium
                    // archive) is an Error, not an Exception, so it skips that catch entirely, and
                    // a hard process kill runs neither catch nor finally block at all — only
                    // "extraction never touches the real name until it's fully done" is safe
                    // against every one of those. tempDir is cleaned up in the finally below
                    // regardless of how extraction ends, including the now-empty husk left behind
                    // after a successful rename.
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
                        // The archive is scratch space either way once extraction has been
                        // attempted — on success its contents are already on disk under dirName,
                        // and on failure nothing downstream needs it either.
                        archiveFile.delete()
                    }
                    updateWhisperState(model, resolveWhisperState(model))
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "model-download",
                            endpoint = model.downloadUrl,
                            model = model.archiveName,
                            success = true,
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                } catch (e: Exception) {
                    updateWhisperState(model, LocalModelState.Error(e.message ?: "Download failed"))
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "model-download",
                            endpoint = model.downloadUrl,
                            model = model.archiveName,
                            success = false,
                            responseSnippet = e.message ?: "Download failed",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }
            }
        }

        override suspend fun downloadLlm(model: LocalLlmModel) = llmCoordinator.download(model)

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
         * this is the full set of names [orphanedStorageBytes]/[deleteOrphanedFiles] treat as
         * legitimate, so only genuinely unaccounted-for bytes get flagged.
         */
        private fun whisperKnownFileNames(): Set<String> = LocalWhisperModel.entries.flatMap { listOf(it.dirName, it.archiveName, "${it.archiveName}.part") }.toSet()

        private fun knownFileNames(): Set<String> = llmCoordinator.knownFileNames() + whisperKnownFileNames()

        fun orphanedStorageBytes(): Long = ModelDownloader.orphanedBytes(modelsDir, knownFileNames())

        fun deleteOrphanedFiles(): Long = ModelDownloader.deleteOrphanedEntries(modelsDir, knownFileNames())

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
