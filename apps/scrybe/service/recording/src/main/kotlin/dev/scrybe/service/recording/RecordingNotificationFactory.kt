package dev.scrybe.service.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import javax.inject.Inject

class RecordingNotificationFactory
    @Inject
    constructor() {
        companion object {
            const val CHANNEL_ID = "recording_channel"
            const val NOTIFICATION_ID = 1001
        }

        fun createChannel(context: Context) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Recording",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Recording in progress"
                }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        fun buildNotification(
            context: Context,
            elapsedMs: Long = 0L,
            amplitudeRatio: Float = 0f,
        ): Notification {
            val stopIntent =
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = RecordingServiceActions.ACTION_STOP
                }
            val stopPendingIntent =
                PendingIntent.getService(
                    context,
                    0,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val contentPendingIntent =
                contentIntent?.let {
                    PendingIntent.getActivity(
                        context,
                        1,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Recording in progress")
                .setContentText("Elapsed ${formatElapsed(elapsedMs)} · ${formatLevel(amplitudeRatio)}")
                .setSmallIcon(R.drawable.ic_recording_notification)
                .addAction(R.drawable.ic_recording_notification, "Stop", stopPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)
                .build()
        }

        private fun formatElapsed(elapsedMs: Long): String {
            val totalSeconds = elapsedMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

        private fun formatLevel(amplitudeRatio: Float): String =
            when {
                amplitudeRatio >= 0.68f -> "Input high"
                amplitudeRatio >= 0.26f -> "Listening"
                else -> "Quiet room"
            }
    }
