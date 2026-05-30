package dev.scrybe.core.localai

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.LocalGemmaModel
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
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
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
                LocalWhisperModel.entries.associateWith { LocalModelState.NotDownloaded },
            )
        val whisperStates: StateFlow<Map<LocalWhisperModel, LocalModelState>> = _whisperStates.asStateFlow()

        private val _selectedWhisperModel = MutableStateFlow(LocalWhisperModel.default)
        val selectedWhisperModel: StateFlow<LocalWhisperModel> = _selectedWhisperModel.asStateFlow()

        private val _gemmaStates =
            MutableStateFlow<Map<LocalGemmaModel, LocalModelState>>(
                LocalGemmaModel.entries.associateWith { LocalModelState.NotDownloaded },
            )
        val gemmaStates: StateFlow<Map<LocalGemmaModel, LocalModelState>> = _gemmaStates.asStateFlow()

        init {
            refreshStates()
            scope.launch {
                preferencesDataStore.localWhisperModel.collect { model ->
                    _selectedWhisperModel.value = model
                }
            }
        }

        private fun refreshStates() {
            _whisperStates.value = LocalWhisperModel.entries.associateWith { resolveWhisperState(it) }
            _gemmaStates.value = LocalGemmaModel.entries.associateWith { resolveGemmaState(it) }
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

        fun gemmaModelFile(model: LocalGemmaModel): File? {
            val file = File(modelsDir, model.fileName)
            return if (file.exists() && file.length() > 0) file else null
        }

        fun anyGemmaReady(): LocalGemmaModel? = LocalGemmaModel.entries.firstOrNull { gemmaModelFile(it) != null }

        private fun resolveWhisperState(model: LocalWhisperModel): LocalModelState =
            whisperModelDir(model)?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.NotDownloaded

        private fun resolveGemmaState(model: LocalGemmaModel): LocalModelState =
            gemmaModelFile(model)
                ?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.NotDownloaded

        suspend fun downloadWhisper(model: LocalWhisperModel) {
            if (_whisperStates.value[model] is LocalModelState.Downloading) return
            withContext(Dispatchers.IO) {
                try {
                    updateWhisperState(model, LocalModelState.Downloading(0))
                    val archiveFile = File(modelsDir, model.archiveName)
                    downloadFile(model.downloadUrl, archiveFile) { progress ->
                        updateWhisperState(model, LocalModelState.Downloading(progress))
                    }
                    extractTarBz2(archiveFile, modelsDir)
                    archiveFile.delete()
                    updateWhisperState(model, resolveWhisperState(model))
                } catch (e: Exception) {
                    updateWhisperState(model, LocalModelState.Error(e.message ?: "Download failed"))
                }
            }
        }

        suspend fun importGemmaFromUri(
            uri: Uri,
            model: LocalGemmaModel,
        ) {
            if (_gemmaStates.value[model] is LocalModelState.Downloading) return
            withContext(Dispatchers.IO) {
                try {
                    updateGemmaState(model, LocalModelState.Downloading(0))
                    val destFile = File(modelsDir, model.fileName)
                    val sizeBytes =
                        context.contentResolver
                            .openFileDescriptor(uri, "r")
                            ?.use { it.statSize }
                            ?: -1L
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(65_536)
                            var bytesRead = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (sizeBytes > 0) {
                                    updateGemmaState(
                                        model,
                                        LocalModelState.Downloading(((bytesRead * 100) / sizeBytes).toInt()),
                                    )
                                }
                            }
                        }
                    } ?: throw IOException("Could not open selected file")
                    updateGemmaState(model, resolveGemmaState(model))
                } catch (e: Exception) {
                    updateGemmaState(model, LocalModelState.Error(e.message ?: "Import failed"))
                }
            }
        }

        fun deleteWhisper(model: LocalWhisperModel) {
            File(modelsDir, model.dirName).deleteRecursively()
            updateWhisperState(model, LocalModelState.NotDownloaded)
        }

        fun deleteGemma(model: LocalGemmaModel) {
            File(modelsDir, model.fileName).delete()
            updateGemmaState(model, LocalModelState.NotDownloaded)
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

        private fun updateGemmaState(
            model: LocalGemmaModel,
            state: LocalModelState,
        ) {
            _gemmaStates.value = _gemmaStates.value + (model to state)
        }

        private fun downloadFile(
            url: String,
            dest: File,
            onProgress: (Int) -> Unit,
        ) {
            val client =
                okHttpClient
                    .newBuilder()
                    .callTimeout(0, TimeUnit.MILLISECONDS)
                    .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) onProgress(((bytesRead * 100) / contentLength).toInt())
                    }
                }
            }
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
