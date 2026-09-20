package dev.scrybe.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The export exists so another app can open these files, which makes the filename part of the
 * feature rather than a detail. A slash in a title, two recordings from the same minute, or a title
 * long enough to exceed the filesystem's limit all fail *at the destination*, where the export's
 * own return value cannot see them.
 */
class ExportFileNamesTest {
    // 2026-03-14 09:26 UTC. Fixed so the assertions do not depend on where this runs.
    private val instant = 1_773_480_360_000L

    private fun name(
        title: String,
        extension: String = "m4a",
        sessionId: String = "3f2b7c1a-9d4e-4a11-bd8c-2f0e91a7c4d5",
        used: MutableSet<String> = mutableSetOf(),
    ) = ExportFileNames.forRecording(instant, title, extension, sessionId, used)

    init {
        // SimpleDateFormat uses the default zone; pin it so the timestamp is deterministic.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun `a name leads with a sortable timestamp and ends with the true extension`() {
        val result = name("Team standup")
        assertTrue(result, result.startsWith("2026-03-14_"))
        assertTrue(result, result.endsWith(".m4a"))
        assertTrue(result, result.contains("Team standup"))
    }

    @Test
    fun `path separators in a title cannot escape the folder`() {
        val result = name("../../etc/passwd")
        assertFalse(result, result.contains('/'))
        assertFalse(result, result.contains('\\'))
        assertFalse(result, result.contains(".."))
    }

    @Test
    fun `characters filesystems reject are replaced rather than passed through`() {
        val result = name("""q1:report*"<>|?""")
        listOf(':', '*', '"', '<', '>', '|', '?').forEach {
            assertFalse("$it survived in $result", result.dropLast(4).contains(it))
        }
    }

    @Test
    fun `an empty or whitespace title still produces a usable name`() {
        assertTrue(name("").contains("Recording"))
        assertTrue(name("   ").contains("Recording"))
        assertTrue(name("///").contains("_"))
    }

    @Test
    fun `a very long title is truncated so the filename fits a filesystem limit`() {
        val result = name("x".repeat(500))
        assertTrue("filename was ${result.length} chars", result.length < 255)
    }

    @Test
    fun `two recordings in the same minute with the same title get different names`() {
        val used = mutableSetOf<String>()
        val first = ExportFileNames.forRecording(instant, "Notes", "m4a", "aaaaaaaa-1111", used)
        val second = ExportFileNames.forRecording(instant, "Notes", "m4a", "bbbbbbbb-2222", used)
        assertNotEquals(first, second)
        assertTrue(second, second.contains("bbbbbbbb"))
    }

    @Test
    fun `the true extension is used even when it differs from what the session was called`() {
        // A recording made on the old "WAV" setting is MPEG-4/AAC; exporting it as .wav is what
        // makes another app reject it.
        assertTrue(name("Lossless take", extension = "m4a").endsWith(".m4a"))
        assertTrue(name("Web take", extension = "webm").endsWith(".webm"))
    }

    @Test
    fun `a sanitised title keeps spaces dashes and underscores`() {
        assertEquals("A-B_C D", ExportFileNames.sanitize("A-B_C D"))
    }

    @Test
    fun `a title that is only punctuation does not collapse to an empty name`() {
        assertTrue(ExportFileNames.sanitize("!!!").isNotBlank())
    }
}
