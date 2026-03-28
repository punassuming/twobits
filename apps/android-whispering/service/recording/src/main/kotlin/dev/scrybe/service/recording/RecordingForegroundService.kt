package dev.scrybe.service.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.audio.RecordedAudio
import dev.scrybe.core.audio.RecordingConfig
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RecordingForegroundService : Service() {
    @Inject lateinit var audioRecorder: AudioRecorder

    @Inject lateinit var recordingSessionDao: RecordingSessionDao

    @Inject lateinit var notificationFactory: RecordingNotificationFactory

    @Inject lateinit var sessionTranscriptionCoordinator: SessionTranscriptionCoordinator

    @Inject lateinit var recordingSessionEvents: RecordingSessionEvents

    @Inject lateinit var preferencesDataStore: AppPreferencesDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotifiedSecond: Long = -1L

    override fun onCreate() {
        super.onCreate()
        notificationFactory.createChannel(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            RecordingServiceActions.ACTION_START -> handleStart()
            RecordingServiceActions.ACTION_STOP -> handleStop()
            RecordingServiceActions.ACTION_CANCEL -> handleCancel()
        }
        return START_STICKY
    }

    private fun handleStart() {
        startForeground(
            RecordingNotificationFactory.NOTIFICATION_ID,
            notificationFactory.buildNotification(this),
        )
        serviceScope.launch {
            audioRecorder.telemetry.collectLatest { telemetry ->
                val elapsedSecond = telemetry.elapsedMs / 1000
                if (elapsedSecond == lastNotifiedSecond) return@collectLatest
                lastNotifiedSecond = elapsedSecond
                if (hasNotificationPermission()) {
                    updateRecordingNotification(
                        elapsedMs = telemetry.elapsedMs,
                        amplitudeRatio = telemetry.amplitudeRatio,
                    )
                }
            }
        }
        serviceScope.launch {
            val config =
                RecordingConfig(
                    outputDir = filesDir.resolve("recordings").absolutePath,
                    audioFormat = preferencesDataStore.audioFormat.first(),
                    sampleRateHz = preferencesDataStore.sampleRateHz.first(),
                    encodingBitRate = preferencesDataStore.encodingBitRate.first(),
                    channelCount = preferencesDataStore.channelCount.first(),
                )
            audioRecorder.startRecording(config)
        }
    }

    private fun handleStop() {
        serviceScope.launch {
            audioRecorder.stopRecording()
                .onSuccess { recordedAudio ->
                    val sessionId = withContext(Dispatchers.IO) { persistRecording(recordedAudio) }
                    recordingSessionEvents.onSessionCompleted(sessionId)
                    transcriptionScope.launch {
                        sessionTranscriptionCoordinator.autoTranscribeIfEnabled(sessionId)
                            .onFailure {
                                android.util.Log.e(TAG, "Auto-transcription failed for session $sessionId", it)
                            }
                    }
                }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleCancel() {
        audioRecorder.cancelRecording()
        lastNotifiedSecond = -1L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        lastNotifiedSecond = -1L
        super.onDestroy()
    }

    private suspend fun persistRecording(recordedAudio: RecordedAudio): String {
        val finishedAt = System.currentTimeMillis()
        val createdAt = finishedAt - recordedAudio.durationMs
        val title = "Recording ${TITLE_FORMAT.format(Date(createdAt))}"
        val sessionId = UUID.randomUUID().toString()

        recordingSessionDao.insertSession(
            RecordingSessionEntity(
                id = sessionId,
                title = title,
                audioFilePath = recordedAudio.filePath,
                durationMs = recordedAudio.durationMs,
                fileSizeBytes = recordedAudio.fileSizeBytes,
                audioFormat = recordedAudio.audioFormat.name,
                sampleRateHz = recordedAudio.sampleRateHz,
                encodingBitRate = recordedAudio.encodingBitRate,
                channelCount = recordedAudio.channelCount,
                waveformSamples = WaveformCodec.encode(recordedAudio.waveformSamples),
                status = SessionStatus.RECORDED.name,
                isArchived = false,
                estimatedTranscriptionCostUsd = null,
                createdAt = createdAt,
                updatedAt = finishedAt,
            ),
        )
        return sessionId
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun updateRecordingNotification(
        elapsedMs: Long,
        amplitudeRatio: Float,
    ) {
        NotificationManagerCompat.from(this)
            .notify(
                RecordingNotificationFactory.NOTIFICATION_ID,
                notificationFactory.buildNotification(
                    context = this,
                    elapsedMs = elapsedMs,
                    amplitudeRatio = amplitudeRatio,
                ),
            )
    }

    private companion object {
        const val TAG = "RecordingService"
        val TITLE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
}
