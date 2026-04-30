package dev.scrybe.core.localai

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

internal class WhisperEngine(
    modelDir: File,
) : Closeable {
    private val recognizer: OfflineRecognizer

    init {
        val encoderPath = File(modelDir, "tiny-encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "tiny-decoder.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "tiny-tokens.txt").absolutePath

        val whisperConfig =
            OfflineWhisperModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                language = "en",
                task = "transcribe",
            )
        val modelConfig = OfflineModelConfig(whisper = whisperConfig, tokens = tokensPath)
        val config = OfflineRecognizerConfig(modelConfig = modelConfig)
        recognizer = OfflineRecognizer(config = config)
    }

    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int = 16000,
    ): String =
        withContext(Dispatchers.Default) {
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            stream.release()
            result.text.trim()
        }

    override fun close() {
        recognizer.release()
    }
}
