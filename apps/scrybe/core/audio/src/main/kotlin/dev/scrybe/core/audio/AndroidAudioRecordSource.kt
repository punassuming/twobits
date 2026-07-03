package dev.scrybe.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RealtimeAudioSource] backed by [AudioRecord]. Runs concurrently with [AndroidMediaRecorder]'s
 * [MediaRecorder]-based file capture on the same physical microphone — this is a well-known but
 * not universally guaranteed pattern; some OEM audio stacks mute the second capture client or
 * deliver silence instead of failing outright, a failure mode this class's state checks cannot
 * detect. Verify on real hardware across OEMs before this ships.
 */
@Singleton
class AndroidAudioRecordSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RealtimeAudioSource {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var readJob: Job? = null
        private var audioRecord: AudioRecord? = null

        private val _pcmFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        override val pcmFrames: Flow<ByteArray> = _pcmFrames.asSharedFlow()

        override suspend fun start(sampleRateHz: Int): Result<Unit> {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure(SecurityException("RECORD_AUDIO permission not granted"))
            }
            return runCatching {
                val minBufferSize =
                    AudioRecord.getMinBufferSize(
                        sampleRateHz,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                check(minBufferSize > 0) { "Unsupported sample rate: $sampleRateHz" }
                val record =
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRateHz,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize * 2,
                    )
                check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
                audioRecord = record
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    record.release()
                    audioRecord = null
                    error("AudioRecord failed to enter recording state")
                }

                // ~100ms chunks — small enough for low-latency streaming, large enough to avoid
                // per-chunk overhead dominating. `2` is bytes per 16-bit sample.
                val chunkBytes = (sampleRateHz * 2 * 0.1).toInt()
                readJob =
                    scope.launch {
                        val buffer = ByteArray(chunkBytes)
                        while (isActive) {
                            val read = record.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                _pcmFrames.emit(buffer.copyOf(read))
                            }
                        }
                    }
            }
        }

        override fun stop() {
            readJob?.cancel()
            readJob = null
            audioRecord?.let { record ->
                runCatching {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                    record.release()
                }
            }
            audioRecord = null
        }
    }
