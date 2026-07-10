package dev.scrybe.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Decodes an audio file and reduces it to a fixed number of RMS amplitude samples (0..1) — the
 * same signal [AndroidMediaRecorder] captures live. Used when a session is created from an
 * existing file (import, orphan recovery) and to backfill sessions saved before waveforms were
 * stored. Decoding is blocking and CPU-heavy: call from an IO/background context.
 */
@Singleton
class WaveformExtractor
    @Inject
    constructor() {
        /** Returns an empty list when the file has no audio track or can't be decoded. */
        fun extract(
            file: File,
            targetSampleCount: Int = DEFAULT_SAMPLE_COUNT,
        ): List<Float> = runCatching { decode(file, targetSampleCount) }.getOrDefault(emptyList())

        private fun decode(
            file: File,
            targetSampleCount: Int,
        ): List<Float> {
            val extractor = MediaExtractor()
            FileInputStream(file).use { fis -> extractor.setDataSource(fis.fd) }
            val trackIndex =
                (0 until extractor.trackCount).firstOrNull { i ->
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
            if (trackIndex == null) {
                extractor.release()
                return emptyList()
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime == null) {
                extractor.release()
                return emptyList()
            }
            val codec = MediaCodec.createDecoderByType(mime)
            val chunkRms = mutableListOf<Float>()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            val sz = extractor.readSampleData(buf, 0)
                            if (sz < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(idx, 0, sz, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null && bufferInfo.size >= 2) {
                            val bytes = ByteArray(bufferInfo.size)
                            buf.get(bytes)
                            val shorts = ShortArray(bytes.size / 2)
                            ByteBuffer
                                .wrap(bytes)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .get(shorts)
                            var sumSq = 0.0
                            for (s in shorts) sumSq += (s / 32768.0).let { it * it }
                            chunkRms.add(sqrt(sumSq / shorts.size).toFloat().coerceIn(0f, 1f))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                codec.stop()
            } finally {
                codec.release()
                extractor.release()
            }
            if (chunkRms.isEmpty()) return emptyList()
            val step = chunkRms.size.toFloat() / targetSampleCount
            return List(targetSampleCount) { i ->
                chunkRms[(i * step).toInt().coerceAtMost(chunkRms.size - 1)]
            }
        }

        companion object {
            /** Matches [AndroidMediaRecorder.MAX_WAVEFORM_SAMPLES] so all sources look alike. */
            const val DEFAULT_SAMPLE_COUNT = 120
        }
    }
