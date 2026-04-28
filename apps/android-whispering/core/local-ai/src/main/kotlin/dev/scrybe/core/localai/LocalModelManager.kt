package dev.scrybe.core.localai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.LocalGemmaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
    ) {
        private val modelsDir: File get() = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

        private val _whisperState = MutableStateFlow<LocalModelState>(LocalModelState.NotDownloaded)
        val whisperState: StateFlow<LocalModelState> = _whisperState.asStateFlow()

        private val _gemmaStates =
            MutableStateFlow<Map<LocalGemmaModel, LocalModelState>>(
                LocalGemmaModel.entries.associateWith { LocalModelState.NotDownloaded },
            )
        val gemmaStates: StateFlow<Map<LocalGemmaModel, LocalModelState>> = _gemmaStates.asStateFlow()

        companion object {
            private const val WHISPER_ARCHIVE_URL =
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-whisper-tiny.tar.bz2"
            private const val WHISPER_DIR_NAME = "sherpa-onnx-whisper-tiny"
        }

        init {
            refreshStates()
        }

        private fun refreshStates() {
            _whisperState.value = resolveWhisperState()
            _gemmaStates.value = LocalGemmaModel.entries.associateWith { resolveGemmaState(it) }
        }

        fun whisperModelDir(): File? {
            val dir = File(modelsDir, WHISPER_DIR_NAME)
            return if (dir.exists() && dir.isDirectory) dir else null
        }

        fun gemmaModelFile(model: LocalGemmaModel): File? {
            val file = File(modelsDir, model.fileName)
            return if (file.exists() && file.length() > 0) file else null
        }

        fun anyGemmaReady(): LocalGemmaModel? = LocalGemmaModel.entries.firstOrNull { gemmaModelFile(it) != null }

        private fun resolveWhisperState(): LocalModelState =
            if (whisperModelDir() != null) {
                LocalModelState.Ready(File(modelsDir, WHISPER_DIR_NAME).absolutePath)
            } else {
                LocalModelState.NotDownloaded
            }

        private fun resolveGemmaState(model: LocalGemmaModel): LocalModelState =
            gemmaModelFile(model)
                ?.let { LocalModelState.Ready(it.absolutePath) }
                ?: LocalModelState.NotDownloaded

        suspend fun downloadWhisper() {
            if (_whisperState.value is LocalModelState.Downloading) return
            withContext(Dispatchers.IO) {
                try {
                    _whisperState.value = LocalModelState.Downloading(0)
                    val archiveFile = File(modelsDir, "whisper-tiny.tar.bz2")
                    downloadFile(WHISPER_ARCHIVE_URL, archiveFile) { progress ->
                        _whisperState.value = LocalModelState.Downloading(progress)
                    }
                    extractTarBz2(archiveFile, modelsDir)
                    archiveFile.delete()
                    _whisperState.value = resolveWhisperState()
                } catch (e: Exception) {
                    _whisperState.value = LocalModelState.Error(e.message ?: "Download failed")
                }
            }
        }

        suspend fun downloadGemma(model: LocalGemmaModel) {
            val current = _gemmaStates.value[model]
            if (current is LocalModelState.Downloading) return
            withContext(Dispatchers.IO) {
                try {
                    updateGemmaState(model, LocalModelState.Downloading(0))
                    val destFile = File(modelsDir, model.fileName)
                    downloadFile(model.downloadUrl, destFile) { progress ->
                        updateGemmaState(model, LocalModelState.Downloading(progress))
                    }
                    updateGemmaState(model, resolveGemmaState(model))
                } catch (e: Exception) {
                    updateGemmaState(model, LocalModelState.Error(e.message ?: "Download failed"))
                }
            }
        }

        fun deleteWhisper() {
            File(modelsDir, WHISPER_DIR_NAME).deleteRecursively()
            _whisperState.value = LocalModelState.NotDownloaded
        }

        fun deleteGemma(model: LocalGemmaModel) {
            File(modelsDir, model.fileName).delete()
            updateGemmaState(model, LocalModelState.NotDownloaded)
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
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
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
                        if (contentLength > 0) {
                            onProgress(((bytesRead * 100) / contentLength).toInt())
                        }
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
