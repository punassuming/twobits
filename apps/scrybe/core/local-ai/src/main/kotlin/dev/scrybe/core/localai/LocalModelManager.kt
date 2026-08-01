package dev.scrybe.core.localai

import android.content.Context
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.localai.LlmDownloadSource
import com.twobits.localai.LlmModelDownloadCoordinator
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
    ) : LlmDownloadSource {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val modelsDir: File = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
        private val llmCoordinator = LlmModelDownloadCoordinator(modelsDir, okHttpClient)

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

        override suspend fun downloadLlm(model: LocalLlmModel) = llmCoordinator.download(model)

        fun deleteWhisper(model: LocalWhisperModel) {
            File(modelsDir, model.dirName).deleteRecursively()
            ModelDownloader.deletePartialFile(File(modelsDir, model.archiveName))
            updateWhisperState(model, LocalModelState.Absent)
        }

        fun deleteLlm(model: LocalLlmModel) = llmCoordinator.delete(model)

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
