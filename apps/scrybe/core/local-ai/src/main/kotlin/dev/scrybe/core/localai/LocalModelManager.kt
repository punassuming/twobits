package dev.scrybe.core.localai

import android.content.Context
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.localai.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.LocalWhisperModel
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val modelsDir: File get() = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

        private val _whisperStates =
            MutableStateFlow<Map<LocalWhisperModel, LocalModelState>>(
                LocalWhisperModel.entries.associateWith { LocalModelState.Absent },
            )
        val whisperStates: StateFlow<Map<LocalWhisperModel, LocalModelState>> = _whisperStates.asStateFlow()

        private val _selectedWhisperModel = MutableStateFlow(LocalWhisperModel.default)
        val selectedWhisperModel: StateFlow<LocalWhisperModel> = _selectedWhisperModel.asStateFlow()

        private val _llmStates =
            MutableStateFlow<Map<LocalLlmModel, LocalModelState>>(
                LocalLlmModel.entries.associateWith { LocalModelState.Absent },
            )
        val llmStates: StateFlow<Map<LocalLlmModel, LocalModelState>> = _llmStates.asStateFlow()

        init {
            // A .part file nobody has resumed in days is more likely abandoned than still
            // wanted, and unlike a Ready model file it's disk usage the UI never surfaces, so
            // it can't otherwise be noticed and cleaned up by hand.
            ModelDownloader.cleanupStalePartialFiles(modelsDir)
            refreshStates()
            scope.launch {
                preferencesDataStore.localWhisperModel.collect { model ->
                    _selectedWhisperModel.value = model
                }
            }
        }

        private fun refreshStates() {
            _whisperStates.value = LocalWhisperModel.entries.associateWith { resolveWhisperState(it) }
            _llmStates.value = LocalLlmModel.entries.associateWith { resolveLlmState(it) }
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

        fun llmModelFile(model: LocalLlmModel): File? {
            val file = File(modelsDir, model.fileName)
            return if (file.exists() && file.length() > 0) file else null
        }

        fun anyLlmReady(): LocalLlmModel? = LocalLlmModel.entries.firstOrNull { llmModelFile(it) != null }

        private fun resolveWhisperState(model: LocalWhisperModel): LocalModelState =
            whisperModelDir(model)?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.Absent

        private fun resolveLlmState(model: LocalLlmModel): LocalModelState =
            llmModelFile(model)
                ?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.Absent

        suspend fun downloadWhisper(model: LocalWhisperModel) {
            if (_whisperStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                try {
                    updateWhisperState(model, LocalModelState.Acquiring(0))
                    val archiveFile = File(modelsDir, model.archiveName)
                    ModelDownloader.downloadFile(okHttpClient, model.downloadUrl, archiveFile) { progress ->
                        updateWhisperState(model, LocalModelState.Acquiring(progress))
                    }
                    extractTarBz2(archiveFile, modelsDir)
                    archiveFile.delete()
                    updateWhisperState(model, resolveWhisperState(model))
                } catch (e: Exception) {
                    updateWhisperState(model, LocalModelState.Error(e.message ?: "Download failed"))
                }
            }
        }

        suspend fun downloadLlm(model: LocalLlmModel) {
            if (_llmStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                val destFile = File(modelsDir, model.fileName)
                try {
                    updateLlmState(model, LocalModelState.Acquiring(0))
                    ModelDownloader.downloadFile(
                        okHttpClient = okHttpClient,
                        url = model.downloadUrl,
                        dest = destFile,
                        expectedSha256 = model.sha256,
                    ) { progress -> updateLlmState(model, LocalModelState.Acquiring(progress)) }
                    updateLlmState(model, resolveLlmState(model))
                } catch (e: Exception) {
                    // The in-progress .part file is deliberately left alone here — downloadFile
                    // already discarded it for unrecoverable failures (bad checksum, HTTP 4xx,
                    // out of space) and otherwise preserved it so the next attempt (this button,
                    // or an automatic retry) resumes instead of starting over.
                    updateLlmState(model, LocalModelState.Error(e.message ?: "Download failed"))
                }
            }
        }

        fun deleteWhisper(model: LocalWhisperModel) {
            File(modelsDir, model.dirName).deleteRecursively()
            ModelDownloader.deletePartialFile(File(modelsDir, model.archiveName))
            updateWhisperState(model, LocalModelState.Absent)
        }

        fun deleteLlm(model: LocalLlmModel) {
            val destFile = File(modelsDir, model.fileName)
            destFile.delete()
            ModelDownloader.deletePartialFile(destFile)
            updateLlmState(model, LocalModelState.Absent)
        }

        fun selectWhisperModel(model: LocalWhisperModel) {
            scope.launch { preferencesDataStore.setLocalWhisperModel(model) }
        }

        private fun updateWhisperState(
            model: LocalWhisperModel,
            state: LocalModelState,
        ) {
            _whisperStates.value = _whisperStates.value + (model to state)
        }

        private fun updateLlmState(
            model: LocalLlmModel,
            state: LocalModelState,
        ) {
            _llmStates.value = _llmStates.value + (model to state)
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
