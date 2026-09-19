package dev.scrybe.core.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stale start marker is shown to the user as "the app closed unexpectedly while running X",
 * so a false positive accuses the app of a crash that never happened. This pins the rule that
 * decides it; a previous version matched on an "-start" suffix in the op name and reported an
 * ordinary per-launch bookkeeping entry, named "app-start", as a crash.
 */
class StaleStartMarkerTest {
    private fun call(
        op: String,
        startMarker: Boolean = false,
    ) = DebugLogEntry(
        timestampMs = 1_000,
        type = DebugLogEntryType.AI_CALL,
        op = op,
        startMarker = startMarker,
    )

    @Test
    fun `an unmatched start marker is reported`() {
        val entry = call("transcribe-start", startMarker = true)
        assertEquals(entry, selectStaleStartMarker(listOf(call("app-launch"), entry)))
    }

    @Test
    fun `a completed call reports nothing`() {
        val entries = listOf(call("transcribe-start", startMarker = true), call("transcribe"))
        assertNull(selectStaleStartMarker(entries))
    }

    /** The regression: an op ending in "-start" that is not a risky-call marker must be ignored. */
    @Test
    fun `a launch entry named like a start marker is not reported`() {
        assertNull(selectStaleStartMarker(listOf(call("app-start"))))
        assertNull(selectStaleStartMarker(listOf(call("app-launch"))))
    }

    /**
     * The process-exit entry is appended on the *next* launch, after the marker it explains, and
     * must not hide it — that pairing is the whole diagnostic for a native crash.
     */
    @Test
    fun `a process-exit entry does not hide the marker it describes`() {
        val marker = call("market-research-start", startMarker = true)
        val exit =
            DebugLogEntry(
                timestampMs = 2_000,
                type = DebugLogEntryType.CRASH,
                exceptionType = PROCESS_EXIT_TYPE,
                message = "Previous run ended: CRASH_NATIVE",
            )
        assertEquals(marker, selectStaleStartMarker(listOf(marker, exit)))
    }

    @Test
    fun `an empty log reports nothing`() {
        assertNull(selectStaleStartMarker(emptyList()))
    }

    @Test
    fun `a retry of the crashed pair matches despite the start suffix`() {
        assertTrue(isRememberedCrashPair("market-research-start", "qwen3.litertlm", "market-research", "qwen3.litertlm"))
        assertTrue(isRememberedCrashPair("market-research", "qwen3.litertlm", "market-research", "qwen3.litertlm"))
    }

    @Test
    fun `a different model or op is not the remembered pair`() {
        assertFalse(isRememberedCrashPair("market-research-start", "gemma.litertlm", "market-research", "qwen3.litertlm"))
        assertFalse(isRememberedCrashPair("transcribe-start", "qwen3.litertlm", "market-research", "qwen3.litertlm"))
    }

    @Test
    fun `nothing remembered matches nothing`() {
        assertFalse(isRememberedCrashPair("market-research-start", "qwen3.litertlm", null, null))
        assertFalse(isRememberedCrashPair(null, null, "market-research", "qwen3.litertlm"))
    }
}
