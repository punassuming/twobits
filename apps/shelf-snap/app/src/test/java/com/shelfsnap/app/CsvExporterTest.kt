package com.shelfsnap.app

import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.util.CsvExporter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CsvExporterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exporter: CsvExporter

    @Before
    fun setUp() {
        exporter = CsvExporter()
    }

    @Test
    fun `export creates CSV file`() {
        val items =
            listOf(
                Item(
                    id = 1L,
                    category = "Books",
                    description = "Kotlin in Action",
                    condition = Condition.GOOD,
                    estimatedValue = 12.50,
                    isDraft = false,
                ),
            )
        val result = exporter.export(items, tempFolder.root)
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("shelf_snap_donation_"))
        assertTrue(file.name.endsWith(".csv"))
    }

    @Test
    fun `export CSV contains header row`() {
        val result = exporter.export(emptyList(), tempFolder.root)
        val content = result.getOrThrow().readText()
        assertTrue(content.contains("Category"))
        assertTrue(content.contains("Description"))
        assertTrue(content.contains("Condition"))
        assertTrue(content.contains("Estimated Value"))
    }

    @Test
    fun `export CSV contains item data`() {
        val items =
            listOf(
                Item(
                    id = 1L,
                    category = "Clothing",
                    description = "Winter jacket",
                    condition = Condition.EXCELLENT,
                    estimatedValue = 25.00,
                    isDraft = false,
                ),
            )
        val content = exporter.export(items, tempFolder.root).getOrThrow().readText()
        assertTrue(content.contains("Clothing"))
        assertTrue(content.contains("Winter jacket"))
        assertTrue(content.contains("EXCELLENT"))
        assertTrue(content.contains("25.00"))
    }

    @Test
    fun `export CSV contains total row`() {
        val items =
            listOf(
                Item(id = 1L, estimatedValue = 10.00, isDraft = false),
                Item(id = 2L, estimatedValue = 20.00, isDraft = false),
            )
        val content = exporter.export(items, tempFolder.root).getOrThrow().readText()
        assertTrue(content.contains("30.00"))
        assertTrue(content.contains("Total Estimated Donation Value"))
    }

    @Test
    fun `total is sum of all item estimated values`() {
        val items =
            listOf(
                Item(id = 1L, estimatedValue = 5.50, isDraft = false),
                Item(id = 2L, estimatedValue = 14.75, isDraft = false),
                Item(id = 3L, estimatedValue = 0.00, isDraft = false),
            )
        val content = exporter.export(items, tempFolder.root).getOrThrow().readText()
        // 5.50 + 14.75 + 0.00 = 20.25
        assertTrue(content.contains("20.25"))
    }

    @Test
    fun `export escapes commas in description`() {
        val items =
            listOf(
                Item(
                    id = 1L,
                    category = "Other",
                    description = "Bowl, plate, cup set",
                    condition = Condition.GOOD,
                    estimatedValue = 8.00,
                    isDraft = false,
                ),
            )
        val content = exporter.export(items, tempFolder.root).getOrThrow().readText()
        // Commas in description should be wrapped in quotes
        assertTrue(content.contains("\"Bowl, plate, cup set\""))
    }

    @Test
    fun `export returns failure for unwritable directory`() {
        val noDir = File(tempFolder.root, "nonexistent/sub/dir")
        val result = exporter.export(emptyList(), noDir)
        assertTrue(result.isFailure)
    }

    @Test
    fun `export CSV labels values as estimates`() {
        val result = exporter.export(emptyList(), tempFolder.root)
        val content = result.getOrThrow().readText()
        assertTrue(content.contains("estimate"))
    }
}
