package com.twobits.debuglog

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two pre-merge log files — `crash_log.json` and `ai_call_debug.json` — are read exactly once
 * per install, by `migrateLegacyLogsIfPresent`, and every failure there is swallowed by a
 * `runCatching { }.onFailure { Log.w(...) }`. That is the right behaviour for a best-effort
 * migration and it is also why nothing else can catch a mistake in these two schemas: the symptom
 * is not an exception anyone sees, it is a user's pre-merge history quietly not arriving.
 *
 * Both files were written by Gson in every version that produced them, including in Shelf Snap,
 * which kept a Gson fork of this store until it moved onto this module. So these are documents this
 * code never wrote, decoded by a different library than wrote them — the one combination worth
 * asserting rather than assuming.
 *
 * The first test below is the one that failed before [LegacyCrashLogEntry.message] was given a
 * default: Gson omits nulls, kotlinx.serialization treats a missing key with no default as fatal,
 * and a throwable with no message is entirely ordinary.
 */
class LegacyWireCompatibilityTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun `a legacy crash entry whose throwable had no message still decodes`() {
        val entries =
            json.decodeFromString(
                ListSerializer(LegacyCrashLogEntry.serializer()),
                // Gson omitted "message" because it was null. Nothing else is missing.
                """
                [{"timestampMs":1740000000000,"threadName":"main",
                  "exceptionType":"java.lang.IllegalStateException",
                  "stackTrace":"java.lang.IllegalStateException\n\tat com.shelfsnap.Foo.bar(Foo.kt:12)"}]
                """.trimIndent(),
            )
        val entry = entries.single()
        assertNull(entry.message)
        assertEquals("main", entry.threadName)
        assertEquals("java.lang.IllegalStateException", entry.exceptionType)
    }

    @Test
    fun `a legacy crash entry with a message decodes too`() {
        val entry =
            json
                .decodeFromString(
                    ListSerializer(LegacyCrashLogEntry.serializer()),
                    """
                    [{"timestampMs":1740000000001,"threadName":"DefaultDispatcher-worker-3",
                      "exceptionType":"java.io.IOException","message":"unexpected end of stream",
                      "stackTrace":"java.io.IOException\n\tat okio.Okio.read(Okio.kt:1)"}]
                    """.trimIndent(),
                ).single()
        assertEquals("unexpected end of stream", entry.message)
    }

    /** Every optional field omitted, which is what Gson wrote for a minimal successful call. */
    @Test
    fun `a legacy AI-call entry with every nullable field omitted still decodes`() {
        val entry =
            json
                .decodeFromString(
                    ListSerializer(LegacyAiCallDebugEntry.serializer()),
                    """
                    [{"timestampMs":1740000000002,"op":"listing-generate","endpoint":"on-device",
                      "requestSummary":"photos=3","success":true}]
                    """.trimIndent(),
                ).single()
        assertEquals("listing-generate", entry.op)
        assertEquals(true, entry.success)
        assertNull(entry.model)
        assertNull(entry.httpStatus)
        assertNull(entry.responseSnippet)
        assertNull(entry.durationMs)
    }

    @Test
    fun `a legacy AI-call entry with every field present decodes`() {
        val entry =
            json
                .decodeFromString(
                    ListSerializer(LegacyAiCallDebugEntry.serializer()),
                    """
                    [{"timestampMs":1740000000003,"op":"market-research","endpoint":"api.openai.com",
                      "model":"gpt-4o-mini","requestSummary":"query=vintage lamp","success":false,
                      "httpStatus":429,"responseSnippet":"rate limited","durationMs":1180}]
                    """.trimIndent(),
                ).single()
        assertEquals(429, entry.httpStatus)
        assertEquals(false, entry.success)
        assertEquals(1180L, entry.durationMs)
    }

    /** An empty legacy file is the common case and must decode to nothing, not fail. */
    @Test
    fun `an empty legacy document decodes to no entries`() {
        assertEquals(
            emptyList<LegacyCrashLogEntry>(),
            json.decodeFromString(ListSerializer(LegacyCrashLogEntry.serializer()), "[]"),
        )
    }
}
