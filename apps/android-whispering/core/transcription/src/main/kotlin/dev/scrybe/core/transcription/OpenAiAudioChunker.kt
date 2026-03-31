package dev.scrybe.core.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class OpenAiAudioChunker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun createChunksIfNeeded(audioFile: File): List<File> {
            if (audioFile.length() <= MAX_DIRECT_UPLOAD_BYTES) return listOf(audioFile)

            val outputFormat = muxerOutputFormatFor(audioFile) ?: return listOf(audioFile)
            val durationMs = readDurationMs(audioFile) ?: return listOf(audioFile)
            val chunkDurationMs = computeChunkDurationMs(audioFile.length(), durationMs)

            Log.i(
                TAG,
                "Chunking ${audioFile.name} (${audioFile.length()} bytes) into about ${chunkDurationMs}ms segments",
            )

            val chunkDirectory =
                context.cacheDir
                    .resolve("transcription-chunks")
                    .resolve(UUID.randomUUID().toString())
                    .apply { mkdirs() }

            return splitAudioFile(
                audioFile = audioFile,
                outputFormat = outputFormat,
                outputDirectory = chunkDirectory,
                chunkDurationMs = chunkDurationMs,
            )
        }

        fun cleanupChunks(
            files: List<File>,
            originalFile: File,
        ) {
            files
                .filterNot { it.absolutePath == originalFile.absolutePath }
                .forEach { file ->
                    runCatching {
                        file.delete()
                        file.parentFile?.takeIf { parent -> parent.isDirectory }?.delete()
                    }
                }
        }

        private fun computeChunkDurationMs(
            fileSizeBytes: Long,
            durationMs: Long,
        ): Long {
            val safeTargetBytes = (MAX_DIRECT_UPLOAD_BYTES * CHUNK_TARGET_RATIO).roundToLong()
            val proportionalDuration = (durationMs * safeTargetBytes.toDouble() / fileSizeBytes).roundToLong()
            return proportionalDuration.coerceIn(MIN_CHUNK_DURATION_MS, durationMs)
        }

        private fun readDurationMs(audioFile: File): Long? =
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                } finally {
                    retriever.release()
                }
            }.getOrNull()

        private fun splitAudioFile(
            audioFile: File,
            outputFormat: Int,
            outputDirectory: File,
            chunkDurationMs: Long,
        ): List<File> {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)

            try {
                val trackIndex = findAudioTrackIndex(extractor)
                require(trackIndex >= 0) { "No audio track found in ${audioFile.name}" }
                extractor.selectTrack(trackIndex)

                val trackFormat = requireNotNull(extractor.getTrackFormat(trackIndex))
                val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES)
                val bufferInfo = MediaCodec.BufferInfo()
                val outputFiles = mutableListOf<File>()

                var muxerState: ChunkMuxerState? = null
                var chunkStartTimeUs = -1L
                val targetChunkDurationUs = chunkDurationMs * 1_000L

                while (true) {
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0L) break

                    if (muxerState == null) {
                        chunkStartTimeUs = sampleTimeUs
                        muxerState =
                            openChunkMuxer(
                                audioFile = audioFile,
                                outputDirectory = outputDirectory,
                                outputFormat = outputFormat,
                                trackFormat = trackFormat,
                                index = outputFiles.size,
                            ).also { outputFiles.add(it.file) }
                    } else if ((sampleTimeUs - chunkStartTimeUs) >= targetChunkDurationUs && muxerState.hasSamples) {
                        muxerState.close()
                        chunkStartTimeUs = sampleTimeUs
                        muxerState =
                            openChunkMuxer(
                                audioFile = audioFile,
                                outputDirectory = outputDirectory,
                                outputFormat = outputFormat,
                                trackFormat = trackFormat,
                                index = outputFiles.size,
                            ).also { outputFiles.add(it.file) }
                    }

                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize <= 0) break

                    bufferInfo.set(
                        0,
                        sampleSize,
                        sampleTimeUs - chunkStartTimeUs,
                        toCodecBufferFlags(extractor.sampleFlags),
                    )
                    muxerState.writeSampleData(buffer, bufferInfo)
                    extractor.advance()
                }

                muxerState?.close()
                return outputFiles.ifEmpty { listOf(audioFile) }
            } finally {
                extractor.release()
            }
        }

        private fun findAudioTrackIndex(extractor: MediaExtractor): Int =
            (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: -1

        private fun openChunkMuxer(
            audioFile: File,
            outputDirectory: File,
            outputFormat: Int,
            trackFormat: MediaFormat,
            index: Int,
        ): ChunkMuxerState {
            val chunkFile =
                outputDirectory.resolve(
                    "${audioFile.nameWithoutExtension}_part_${index + 1}.${audioFile.extension}",
                )
            val muxer = MediaMuxer(chunkFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(trackFormat)
            muxer.start()
            return ChunkMuxerState(
                file = chunkFile,
                muxer = muxer,
                muxerTrackIndex = muxerTrackIndex,
            )
        }

        private fun muxerOutputFormatFor(audioFile: File): Int? =
            when (audioFile.extension.lowercase()) {
                "aac", "m4a", "mp4" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                "webm" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else -> null
            }

        private fun toCodecBufferFlags(sampleFlags: Int): Int {
            var codecFlags = 0
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            return codecFlags
        }

        private data class ChunkMuxerState(
            val file: File,
            val muxer: MediaMuxer,
            val muxerTrackIndex: Int,
            var hasSamples: Boolean = false,
        ) {
            fun writeSampleData(
                buffer: ByteBuffer,
                bufferInfo: MediaCodec.BufferInfo,
            ) {
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                hasSamples = true
            }

            fun close() {
                runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }

        private companion object {
            const val TAG = "OpenAiAudioChunker"
            const val BUFFER_SIZE_BYTES = 1 shl 20
            const val MAX_DIRECT_UPLOAD_BYTES = 20L * 1024L * 1024L
            const val MIN_CHUNK_DURATION_MS = 60_000L
            const val CHUNK_TARGET_RATIO = 0.8
        }
    }
