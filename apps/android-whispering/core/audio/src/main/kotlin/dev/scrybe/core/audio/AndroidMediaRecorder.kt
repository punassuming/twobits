package dev.scrybe.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.AudioFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject

class AndroidMediaRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: Flow<Boolean> = _isRecording

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

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
        _isRecording.value = true
    }

    override suspend fun stopRecording(): Result<RecordedAudio> = runCatching {
        val recorder = requireNotNull(mediaRecorder) { "MediaRecorder is not recording" }
        val file = requireNotNull(currentFile) { "No current recording file" }
        val durationMs = System.currentTimeMillis() - startTimeMs

        recorder.stop()
        recorder.release()
        mediaRecorder = null
        _isRecording.value = false

        RecordedAudio(
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            audioFormat = AudioFormat.AAC,
        )
    }

    override fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) { /* ignore */ }
        mediaRecorder = null
        currentFile?.delete()
        currentFile = null
        _isRecording.value = false
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
}
