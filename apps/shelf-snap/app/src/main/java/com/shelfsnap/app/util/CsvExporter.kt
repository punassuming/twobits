package com.shelfsnap.app.util

import com.shelfsnap.app.data.model.Item
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports the confirmed donation inventory to a CSV file.
 *
 * Columns: Category, Description, Condition, Estimated Value (USD), Photos
 * Footer row: Total estimated donation value.
 */
@Singleton
class CsvExporter @Inject constructor() {

    fun export(items: List<Item>, outputDir: File): Result<File> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir, "shelf_snap_donation_$timestamp.csv")

        val sb = StringBuilder()
        sb.appendLine("Category,Description,Condition,\"Estimated Value (USD) [estimate]\",Photos")

        for (item in items) {
            val photoList = item.photoPaths.joinToString("; ")
            sb.appendLine(
                "${item.category.csvEscape()}," +
                    "${item.description.csvEscape()}," +
                    "${item.condition.name.csvEscape()}," +
                    "${"%.2f".format(item.estimatedValue)}," +
                    "${photoList.csvEscape()}"
            )
        }

        val total = items.sumOf { it.estimatedValue }
        sb.appendLine()
        sb.appendLine(",,\"Total Estimated Donation Value (estimate)\",${"%.2f".format(total)},")

        file.writeText(sb.toString())
        file
    }

    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
