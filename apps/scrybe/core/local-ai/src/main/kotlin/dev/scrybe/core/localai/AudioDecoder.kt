package dev.scrybe.core.localai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class DecodedAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
)

internal object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    /**
     * Returns PCM samples at the source file's own sample rate — Scrybe's recorder is
     * user-configurable from 8kHz to 48kHz (see `AppPreferencesDataStore.sampleRateHz`), so
     * callers must resample or pass [DecodedAudio.sampleRateHz] through to whatever expects a
     * fixed rate (e.g. [WhisperEngine], which needs 16kHz) rather than assuming 16kHz here.
     */
    fun decode(audioFile: File): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(audioFile.absolutePath)

        val trackIndex =
            (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("No audio track found in ${audioFile.name}")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // A plain byte[]-backed buffer, not a List<Byte> — the latter stores one boxed
        // reference per byte (8+ bytes of overhead per byte of actual audio on ART), which
        // OOMs on anything but the shortest recordings: a ~19MB raw PCM buffer (a few minutes
        // of audio) needs 150MB+ as a List<Byte>.
        val pcmBytes = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIdx >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIdx = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIdx >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIdx)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    pcmBytes.write(chunk)
                }
                codec.releaseOutputBuffer(outputIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        return DecodedAudio(convertToFloat(pcmBytes.toByteArray()), sourceSampleRate)
    }

    private fun convertToFloat(pcm16: ByteArray): FloatArray {
        val shorts = ShortArray(pcm16.size / 2)
        ByteBuffer
            .wrap(pcm16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return FloatArray(shorts.size) { i -> shorts[i] / 32768f }
    }
}
