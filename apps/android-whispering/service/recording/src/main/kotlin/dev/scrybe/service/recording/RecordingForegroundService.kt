package dev.scrybe.service.recording

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.audio.RecordedAudio
import dev.scrybe.core.audio.RecordingConfig
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        notificationFactory.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            notificationFactory.buildNotification(this)
        )
        serviceScope.launch {
            val config = RecordingConfig(
                outputDir = filesDir.resolve("recordings").absolutePath
            )
            audioRecorder.startRecording(config)
        }
    }

    private fun handleStop() {
        serviceScope.launch {
            audioRecorder.stopRecording()
                .onSuccess { recordedAudio ->
                    val sessionId = withContext(Dispatchers.IO) { persistRecording(recordedAudio) }
                    withContext(Dispatchers.IO) {
                        sessionTranscriptionCoordinator.autoTranscribeIfEnabled(sessionId)
                    }
                }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleCancel() {
        audioRecorder.cancelRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
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
                status = SessionStatus.RECORDED.name,
                createdAt = createdAt,
                updatedAt = finishedAt,
            )
        )
        return sessionId
    }

    private companion object {
        val TITLE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
}
