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

@Singleton
class AndroidMediaRecorder @Inject constructor(
    @ApplicationContext private val context: Context
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
    private var telemetryJob: Job? = null

    override suspend fun startRecording(config: RecordingConfig): Result<Unit> = runCatching {
        val outputDir = File(config.outputDir).apply { mkdirs() }
        val fileName = "recording_${UUID.randomUUID()}.${config.audioFormat.extension}"
        val outputFile = File(outputDir, fileName)

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(config.audioFormat.outputFormat)
            setAudioEncoder(config.audioFormat.audioEncoder)
            setOutputFile(outputFile.absolutePath)
            setMaxDuration(config.maxDurationMs.toInt())
            prepare()
            start()
        }

        mediaRecorder = recorder
        currentFile = outputFile
        startTimeMs = System.currentTimeMillis()
        currentAudioFormat = config.audioFormat
        _isRecording.value = true
        _telemetry.value = RecordingTelemetry()
        startTelemetryUpdates(recorder)
    }

    override suspend fun stopRecording(): Result<RecordedAudio> = runCatching {
        val recorder = requireNotNull(mediaRecorder) { "MediaRecorder is not recording" }
        val file = requireNotNull(currentFile) { "No current recording file" }
        val durationMs = System.currentTimeMillis() - startTimeMs

        recorder.stop()
        recorder.release()
        mediaRecorder = null
        telemetryJob?.cancel()
        _isRecording.value = false
        _telemetry.value = RecordingTelemetry()

        RecordedAudio(
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            audioFormat = currentAudioFormat,
        )
    }

    override fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) { /* ignore */ }
        telemetryJob?.cancel()
        mediaRecorder = null
        currentFile?.delete()
        currentFile = null
        _isRecording.value = false
        _telemetry.value = RecordingTelemetry()
    }

    private fun startTelemetryUpdates(recorder: MediaRecorder) {
        telemetryJob?.cancel()
        telemetryJob = recorderScope.launch {
            while (isActive && mediaRecorder === recorder) {
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                val amplitudeRatio = (recorder.maxAmplitude / MAX_AMPLITUDE.toFloat())
                    .coerceIn(0f, 1f)
                _telemetry.value = RecordingTelemetry(
                    elapsedMs = elapsedMs,
                    amplitudeRatio = amplitudeRatio,
                )
                delay(100)
            }
        }
    }

    private val AudioFormat.extension get() = when (this) {
        AudioFormat.AAC -> "m4a"
        AudioFormat.MP4 -> "mp4"
        AudioFormat.OGG -> "ogg"
        AudioFormat.WEBM -> "webm"
    }

    private val AudioFormat.outputFormat get() = when (this) {
        AudioFormat.AAC -> MediaRecorder.OutputFormat.MPEG_4
        AudioFormat.MP4 -> MediaRecorder.OutputFormat.MPEG_4
        AudioFormat.OGG -> MediaRecorder.OutputFormat.OGG
        AudioFormat.WEBM -> MediaRecorder.OutputFormat.WEBM
    }

    private val AudioFormat.audioEncoder get() = when (this) {
        AudioFormat.AAC -> MediaRecorder.AudioEncoder.AAC
        AudioFormat.MP4 -> MediaRecorder.AudioEncoder.AAC
        AudioFormat.OGG -> MediaRecorder.AudioEncoder.VORBIS
        AudioFormat.WEBM -> MediaRecorder.AudioEncoder.VORBIS
    }

    private companion object {
        const val MAX_AMPLITUDE = 32767
    }
}
