package dev.scrybe.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.AudioFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AndroidMediaRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioRecorder {
        private val _isRecording = MutableStateFlow(false)
        override val isRecording: Flow<Boolean> = _isRecording
        private val _telemetry = MutableStateFlow(RecordingTelemetry())
        override val telemetry: Flow<RecordingTelemetry> = _telemetry
        private val recorderScope = CoroutineScope(Dispatchers.Default)

        private var mediaRecorder: MediaRecorder? = null
        private var currentFile: File? = null
        private var startTimeMs: Long = 0L
        private var currentAudioFormat: AudioFormat = AudioFormat.AAC
        private var currentSampleRateHz: Int = 48_000
        private var currentEncodingBitRate: Int = 128_000
        private var currentChannelCount: Int = 1
        private var telemetryJob: Job? = null
        private var waveformSamples: MutableList<Float> = mutableListOf()
        private var smoothedAmplitudeRatio: Float = 0f
        private var pausedDurationMs: Long = 0L
        private var pausedAtMs: Long = 0L

        override suspend fun startRecording(config: RecordingConfig): Result<Unit> =
            runCatching {
                val outputDir = File(config.outputDir).apply { mkdirs() }
                val fileName = "recording_${UUID.randomUUID()}.${config.audioFormat.extension}"
                val outputFile = File(outputDir, fileName)

                val recorder =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                runCatching {
                    recorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(config.audioFormat.outputFormat)
                        setAudioEncoder(config.audioFormat.audioEncoder)
                        setAudioSamplingRate(config.sampleRateHz)
                        setAudioEncodingBitRate(config.encodingBitRate)
                        setAudioChannels(config.channelCount)
                        setOutputFile(outputFile.absolutePath)
                        setMaxDuration(config.maxDurationMs.toInt())
                        prepare()
                        start()
                    }
                }.onFailure { error ->
                    runCatching { recorder.reset() }
                    runCatching { recorder.release() }
                    runCatching { outputFile.delete() }
                    throw error
                }

                mediaRecorder = recorder
                currentFile = outputFile
                startTimeMs = System.currentTimeMillis()
                currentAudioFormat = config.audioFormat
                currentSampleRateHz = config.sampleRateHz
                currentEncodingBitRate = config.encodingBitRate
                currentChannelCount = config.channelCount
                waveformSamples = mutableListOf()
                smoothedAmplitudeRatio = 0f
                pausedDurationMs = 0L
                pausedAtMs = 0L
                _isRecording.value = true
                _telemetry.value = RecordingTelemetry()
                startTelemetryUpdates(recorder)
            }

        override suspend fun stopRecording(): Result<RecordedAudio> =
            runCatching {
                val recorder = requireNotNull(mediaRecorder) { "MediaRecorder is not recording" }
                val file = requireNotNull(currentFile) { "No current recording file" }
                val stoppedAt = System.currentTimeMillis()
                val effectivePaused = pausedDurationMs + if (pausedAtMs > 0L) stoppedAt - pausedAtMs else 0L
                val durationMs = stoppedAt - startTimeMs - effectivePaused
                stopTelemetryUpdates(recorder)

                try {
                    recorder.stop()
                    RecordedAudio(
                        filePath = file.absolutePath,
                        durationMs = durationMs,
                        fileSizeBytes = file.length(),
                        audioFormat = currentAudioFormat,
                        sampleRateHz = currentSampleRateHz,
                        encodingBitRate = currentEncodingBitRate,
                        channelCount = currentChannelCount,
                        waveformSamples = downsampleWaveform(waveformSamples, MAX_WAVEFORM_SAMPLES),
                    )
                } catch (error: RuntimeException) {
                    runCatching { file.delete() }
                    throw IllegalStateException(SHORT_RECORDING_MESSAGE, error)
                } finally {
                    runCatching { recorder.reset() }
                    runCatching { recorder.release() }
                    mediaRecorder = null
                    currentFile = null
                    telemetryJob?.cancel()
                    waveformSamples = mutableListOf()
                    smoothedAmplitudeRatio = 0f
                    pausedDurationMs = 0L
                    pausedAtMs = 0L
                    _isRecording.value = false
                    _telemetry.value = RecordingTelemetry()
                }
            }

        override suspend fun pauseRecording() {
            runCatching {
                mediaRecorder?.pause()
                pausedAtMs = System.currentTimeMillis()
                telemetryJob?.cancel()
                telemetryJob = null
            }
        }

        override suspend fun resumeRecording() {
            runCatching {
                mediaRecorder?.resume()
                if (pausedAtMs > 0L) {
                    pausedDurationMs += System.currentTimeMillis() - pausedAtMs
                    pausedAtMs = 0L
                }
                mediaRecorder?.let { startTelemetryUpdates(it) }
            }
        }

        override fun cancelRecording() {
            val recorder = mediaRecorder
            stopTelemetryUpdates(recorder)
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {
                // ignore
            }
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null
            waveformSamples = mutableListOf()
            smoothedAmplitudeRatio = 0f
            _isRecording.value = false
            _telemetry.value = RecordingTelemetry()
        }

        private fun startTelemetryUpdates(recorder: MediaRecorder) {
            telemetryJob?.cancel()
            telemetryJob =
                recorderScope.launch {
                    while (isActive && mediaRecorder === recorder) {
                        val elapsedMs = System.currentTimeMillis() - startTimeMs - pausedDurationMs
                        val rawAmplitudeRatio = recorder.readAmplitudeRatio() ?: break
                        val gatedAmplitudeRatio = if (rawAmplitudeRatio < SILENCE_GATE_RATIO) 0f else rawAmplitudeRatio
                        smoothedAmplitudeRatio = (smoothedAmplitudeRatio * SMOOTHING_DECAY) +
                            (gatedAmplitudeRatio * (1f - SMOOTHING_DECAY))

                        waveformSamples.add(smoothedAmplitudeRatio)
                        _telemetry.value =
                            RecordingTelemetry(
                                elapsedMs = elapsedMs,
                                amplitudeRatio = smoothedAmplitudeRatio,
                            )
                        delay(TELEMETRY_INTERVAL_MS)
                    }
                }
        }

        private fun stopTelemetryUpdates(recorder: MediaRecorder?) {
            telemetryJob?.cancel()
            telemetryJob = null
            if (mediaRecorder === recorder) {
                mediaRecorder = null
            }
        }

        private val AudioFormat.extension get() =
            when (this) {
                AudioFormat.AAC -> "m4a"
                AudioFormat.MP3 -> "mp3"
                AudioFormat.MP4 -> "mp4"
                AudioFormat.OGG -> "ogg"
                AudioFormat.WAV -> "wav"
                AudioFormat.WEBM -> "webm"
            }

        private val AudioFormat.outputFormat get() =
            when (this) {
                AudioFormat.AAC -> MediaRecorder.OutputFormat.MPEG_4
                AudioFormat.MP3 -> MediaRecorder.OutputFormat.MPEG_4
                AudioFormat.MP4 -> MediaRecorder.OutputFormat.MPEG_4
                AudioFormat.OGG -> MediaRecorder.OutputFormat.OGG
                AudioFormat.WAV -> MediaRecorder.OutputFormat.MPEG_4
                AudioFormat.WEBM -> MediaRecorder.OutputFormat.WEBM
            }

        private val AudioFormat.audioEncoder get() =
            when (this) {
                AudioFormat.AAC -> MediaRecorder.AudioEncoder.AAC
                AudioFormat.MP3 -> MediaRecorder.AudioEncoder.AAC
                AudioFormat.MP4 -> MediaRecorder.AudioEncoder.AAC
                AudioFormat.OGG -> MediaRecorder.AudioEncoder.VORBIS
                AudioFormat.WAV -> MediaRecorder.AudioEncoder.AAC
                AudioFormat.WEBM -> MediaRecorder.AudioEncoder.VORBIS
            }

        private companion object {
            const val MAX_AMPLITUDE = 32767
            const val MAX_WAVEFORM_SAMPLES = 120
            const val TELEMETRY_INTERVAL_MS = 60L
            const val SILENCE_GATE_RATIO = 0.015f
            const val SMOOTHING_DECAY = 0.62f
            const val SHORT_RECORDING_MESSAGE = "Recording was too short to save. Try holding record for a little longer."
        }

        private fun downsampleWaveform(
            samples: List<Float>,
            targetSize: Int,
        ): List<Float> {
            if (samples.isEmpty() || targetSize <= 0) return emptyList()
            if (samples.size <= targetSize) return samples

            val bucketSize = samples.size / targetSize.toFloat()
            return List(targetSize) { index ->
                val start = (index * bucketSize).roundToInt().coerceAtMost(samples.lastIndex)
                val endExclusive = (((index + 1) * bucketSize).roundToInt()).coerceIn(start + 1, samples.size)
                samples.subList(start, endExclusive).maxOrNull() ?: 0f
            }
        }

        private fun MediaRecorder.readAmplitudeRatio(): Float? =
            runCatching {
                (maxAmplitude / MAX_AMPLITUDE.toFloat()).coerceIn(0f, 1f)
            }.getOrNull()
    }
