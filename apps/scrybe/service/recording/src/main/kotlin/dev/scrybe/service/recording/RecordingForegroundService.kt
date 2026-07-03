package dev.scrybe.service.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.audio.RecordedAudio
import dev.scrybe.core.audio.RecordingConfig
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.CustomRecordingTypeDao
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import dev.scrybe.core.transforms.SessionTransformCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    @Inject lateinit var sessionTransformCoordinator: SessionTransformCoordinator

    @Inject lateinit var recordingSessionEvents: RecordingSessionEvents

    @Inject lateinit var preferencesDataStore: AppPreferencesDataStore

    @Inject lateinit var locationProvider: LocationProvider

    @Inject lateinit var customRecordingTypeDao: CustomRecordingTypeDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotifiedSecond: Long = -1L
    private var telemetryJob: Job? = null
    private var locationDeferred: Deferred<Triple<Double, Double, String?>?>? = null
    private var pendingMode: String = RecordingMode.JOURNAL.name
    private var pendingCustomTypeId: String? = null
    private var pendingSkipTransform: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory.createChannel(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        pendingMode =
            intent?.getStringExtra(RecordingServiceActions.EXTRA_RECORDING_MODE)
                ?: RecordingMode.JOURNAL.name
        if (intent?.action == RecordingServiceActions.ACTION_START) {
            pendingCustomTypeId = intent.getStringExtra(RecordingServiceActions.EXTRA_CUSTOM_TYPE_ID)
        }
        if (intent?.action == RecordingServiceActions.ACTION_STOP) {
            pendingSkipTransform = intent.getBooleanExtra(RecordingServiceActions.EXTRA_SKIP_TRANSFORM, false)
        }
        when (intent?.action) {
            RecordingServiceActions.ACTION_START -> handleStart()
            RecordingServiceActions.ACTION_STOP -> handleStop()
            RecordingServiceActions.ACTION_CANCEL -> handleCancel()
            RecordingServiceActions.ACTION_PAUSE -> handlePause()
            RecordingServiceActions.ACTION_RESUME -> handleResume()
        }
        return START_STICKY
    }

    private fun handleStart() {
        locationDeferred = null
        startForeground(
            RecordingNotificationFactory.NOTIFICATION_ID,
            notificationFactory.buildNotification(this),
        )
        serviceScope.launch { playRecordingFeedback() }
        locationDeferred =
            serviceScope.async {
                if (preferencesDataStore.locationRecordingEnabled.first()) {
                    locationProvider.captureCoarseLocationWithLabel()
                } else {
                    null
                }
            }
        telemetryJob?.cancel()
        telemetryJob =
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
            audioRecorder
                .startRecording(config)
                .onFailure { error ->
                    android.util.Log.e(TAG, "Failed to start recording", error)
                    recordingSessionEvents.onRecordingError(error.message ?: "Failed to start recording")
                    cleanupAfterRecordingCommand()
                }
        }
    }

    private fun handleStop() {
        serviceScope.launch {
            playRecordingFeedback()
            audioRecorder
                .stopRecording()
                .onSuccess { recordedAudio ->
                    runCatching {
                        withContext(Dispatchers.IO) { persistRecording(recordedAudio) }
                    }.onSuccess { sessionId ->
                        recordingSessionEvents.onSessionCompleted(sessionId)
                        transcriptionScope.launch {
                            if (!preferencesDataStore.autoTranscribe.first()) {
                                return@launch
                            }
                            if (recordedAudio.durationMs < MIN_AUTO_TRANSCRIBE_DURATION_MS) {
                                recordingSessionEvents.onRecordingError(SHORT_AUTO_TRANSCRIBE_MESSAGE)
                                return@launch
                            }
                            val transcriptionResult =
                                sessionTranscriptionCoordinator
                                    .autoTranscribeIfEnabled(sessionId)
                            transcriptionResult.onFailure {
                                android.util.Log.e(TAG, "Auto-transcription failed for session $sessionId", it)
                                recordingSessionEvents.onRecordingError(
                                    it.message ?: "Auto-transcription failed",
                                )
                            }
                            if (transcriptionResult.isSuccess && !pendingSkipTransform) {
                                val customTypeId = pendingCustomTypeId
                                if (customTypeId != null) {
                                    val defaultProfileId =
                                        customRecordingTypeDao.getById(customTypeId)?.defaultProfileId
                                    if (defaultProfileId != null) {
                                        sessionTransformCoordinator
                                            .transformLatestRawTranscript(sessionId, defaultProfileId)
                                            .onFailure {
                                                android.util.Log.w(TAG, "Auto-transform failed for session $sessionId", it)
                                            }
                                    }
                                }
                            }
                        }
                    }.onFailure { error ->
                        android.util.Log.e(TAG, "Failed to save recording", error)
                        recordingSessionEvents.onRecordingError(error.message ?: "Failed to save recording")
                        runCatching { File(recordedAudio.filePath).takeIf { it.exists() }?.delete() }
                    }
                }.onFailure { error ->
                    android.util.Log.e(TAG, "Failed to stop recording", error)
                    recordingSessionEvents.onRecordingError(error.message ?: "Failed to stop recording")
                }
            cleanupAfterRecordingCommand()
        }
    }

    private fun handleCancel() {
        audioRecorder.cancelRecording()
        cleanupAfterRecordingCommand()
    }

    private fun handlePause() {
        serviceScope.launch { audioRecorder.pauseRecording() }
    }

    private fun handleResume() {
        serviceScope.launch { audioRecorder.resumeRecording() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        telemetryJob?.cancel()
        lastNotifiedSecond = -1L
        super.onDestroy()
    }

    private fun cleanupAfterRecordingCommand() {
        telemetryJob?.cancel()
        telemetryJob = null
        lastNotifiedSecond = -1L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun persistRecording(recordedAudio: RecordedAudio): String {
        val audioFile = File(recordedAudio.filePath)
        require(audioFile.exists()) { "Recording file was not found after saving" }
        val actualFileSize = audioFile.length()
        if (recordedAudio.fileSizeBytes != actualFileSize) {
            android.util.Log.w(
                TAG,
                "Recorded file size mismatch for ${audioFile.name}: recorder=${recordedAudio.fileSizeBytes}, disk=$actualFileSize",
            )
        }
        if (actualFileSize <= 0L) {
            runCatching { audioFile.delete() }
            error("Recording did not save correctly. Please try again.")
        }

        recordingSessionDao.getSessionByAudioFilePath(audioFile.absolutePath)?.let { existing ->
            return existing.id
        }

        val finishedAt = System.currentTimeMillis()
        val createdAt = finishedAt - recordedAudio.durationMs
        val title = "Recording ${TITLE_FORMAT.format(Date(createdAt))}"
        val sessionId = UUID.randomUUID().toString()

        val location = runCatching { locationDeferred?.await() }.getOrNull()
        recordingSessionDao.insertSession(
            RecordingSessionEntity(
                id = sessionId,
                title = title,
                tags = "",
                audioFilePath = audioFile.absolutePath,
                durationMs = recordedAudio.durationMs,
                fileSizeBytes = actualFileSize,
                audioFormat = recordedAudio.audioFormat.name,
                sampleRateHz = recordedAudio.sampleRateHz,
                encodingBitRate = recordedAudio.encodingBitRate,
                channelCount = recordedAudio.channelCount,
                waveformSamples = WaveformCodec.encode(recordedAudio.waveformSamples),
                status = SessionStatus.RECORDED.name,
                isArchived = false,
                estimatedTranscriptionCostUsd = null,
                locationLat = location?.first,
                locationLng = location?.second,
                locationLabel = location?.third,
                mode = pendingMode,
                customTypeId = pendingCustomTypeId,
                createdAt = createdAt,
                updatedAt = finishedAt,
            ),
        )
        return sessionId
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun updateRecordingNotification(
        elapsedMs: Long,
        amplitudeRatio: Float,
    ) {
        NotificationManagerCompat
            .from(this)
            .notify(
                RecordingNotificationFactory.NOTIFICATION_ID,
                notificationFactory.buildNotification(
                    context = this,
                    elapsedMs = elapsedMs,
                    amplitudeRatio = amplitudeRatio,
                ),
            )
    }

    @Suppress("DEPRECATION")
    private suspend fun playRecordingFeedback() {
        val shouldVibrate = preferencesDataStore.recordingVibrateOnStartStop.first()
        val shouldSound = preferencesDataStore.recordingSoundOnStartStop.first()

        if (shouldVibrate) {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        if (shouldSound) {
            runCatching {
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
        }
    }

    private companion object {
        const val TAG = "RecordingService"
        const val MIN_AUTO_TRANSCRIBE_DURATION_MS = 1_000L
        const val SHORT_AUTO_TRANSCRIBE_MESSAGE =
            "Recording was saved, but it was too short to auto-transcribe reliably."
        val TITLE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
}
