package dev.scrybe.service.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @SuppressLint("MissingPermission")
        fun findOverlappingEvent(
            startMs: Long,
            endMs: Long,
        ): String? {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return null

            return runCatching {
                val projection = arrayOf(CalendarContract.Events.TITLE)
                val selection =
                    "(${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DTEND} >= ?)"
                val args = arrayOf(endMs.toString(), startMs.toString())
                context.contentResolver
                    .query(CalendarContract.Events.CONTENT_URI, projection, selection, args, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()
        }
    }
