package com.twobits.debuglog

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shelf Snap wrote `debug_log.json` with **Gson** before this module existed, so an upgrade has to
 * read a file this code never produced. Nothing about that is verifiable on a developer machine
 * after the fact — if it is wrong, the symptom is a user's entire log silently decoding as empty on
 * first launch after the update, which is the one outcome the log exists to prevent.
 *
 * The documents below are what Gson actually emits for these classes: fields by name, enums by
 * name, and **nulls omitted entirely** (Gson's default). That last part is the real risk, and the
 * reason [Json.ignoreUnknownKeys] alone would not be enough — every nullable field also needs a
 * default in the data class, or a missing key is a decode failure rather than a null.
 */
class GsonWireCompatibilityTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun decode(document: String): List<DebugLogEntry> = json.decodeFromString(ListSerializer(DebugLogEntry.serializer()), document)

    /** Gson omits every null, so a minimal call entry carries only the fields that had values. */
    @Test
    fun `a Gson-written call entry with nulls omitted still decodes`() {
        val entries =
            decode(
                """
                [{"timestampMs":1750000000000,"type":"AI_CALL","op":"vision-analyze",
                  "endpoint":"on-device","model":"gemma-4-E2B-it.litertlm",
                  "requestSummary":"photo=x.jpg","success":true,"responseSnippet":"812 chars",
                  "durationMs":4210}]
                """.trimIndent(),
            )
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("vision-analyze", entry.op)
        assertEquals(true, entry.success)
        // Absent from the document entirely — must arrive as the data class default, not a failure.
        assertNull(entry.stackTrace)
        assertNull(entry.httpStatus)
        assertEquals(false, entry.startMarker)
    }

    @Test
    fun `a Gson-written crash entry still decodes`() {
        val entry =
            decode(
                """
                [{"timestampMs":1750000000001,"type":"CRASH","threadName":"process",
                  "exceptionType":"ProcessExit",
                  "message":"Previous run ended: LOW_MEMORY (killed by the low-memory killer)"}]
                """.trimIndent(),
            ).single()
        assertEquals(DebugLogEntryType.CRASH, entry.type)
        assertEquals(PROCESS_EXIT_TYPE, entry.exceptionType)
        assertTrue(entry.message!!.contains("LOW_MEMORY"))
    }

    /** A file from a newer build carrying a field this code does not know must not fail the decode. */
    @Test
    fun `an unknown field is ignored rather than fatal`() {
        val entries =
            decode(
                """
                [{"timestampMs":1750000000002,"type":"SERVICE_CALL","op":"web-search",
                  "someFieldFromALaterVersion":"whatever"}]
                """.trimIndent(),
            )
        assertEquals("web-search", entries.single().op)
    }

    /** What this module writes must be readable by this module — the round trip, stated. */
    @Test
    fun `entries written here decode back unchanged`() {
        val original =
            listOf(
                DebugLogEntry(
                    timestampMs = 1_750_000_000_003,
                    type = DebugLogEntryType.AI_CALL,
                    op = "market-research-start",
                    startMarker = true,
                    endpoint = "on-device",
                    model = "qwen3_0.6b_q4_block32_ekv1280.litertlm",
                    requestSummary = "mem=1432/5891 MB",
                    success = true,
                ),
            )
        val encoded = json.encodeToString(ListSerializer(DebugLogEntry.serializer()), original)
        assertEquals(original, decode(encoded))
    }
}
