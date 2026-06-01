package com.shelfsnap.app.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {
    /**
     * Creates a new empty JPEG file in the app's external Pictures directory.
     * Returns null if external storage is unavailable.
     */
    fun createImageFile(context: Context): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        if (!dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(dir, "IMG_$timestamp.jpg")
    }
}
