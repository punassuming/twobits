package dev.scrybe.service.recording

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.audio.RecordingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordingForegroundService : Service() {

    @Inject lateinit var audioRecorder: AudioRecorder
    @Inject lateinit var notificationFactory: RecordingNotificationFactory

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
}
