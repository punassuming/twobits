package dev.scrybe.feature.history

import dev.scrybe.core.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkTranscriptionEligibilityTest {
    @Test
    fun `RECORDED status is eligible for transcription`() {
        assertTrue(isEligibleForTranscription(SessionStatus.RECORDED))
    }

    @Test
    fun `FAILED status is eligible for transcription retry`() {
        assertTrue(isEligibleForTranscription(SessionStatus.FAILED))
    }

    @Test
    fun `TRANSCRIBING status is not eligible - avoids duplicate work`() {
        assertFalse(isEligibleForTranscription(SessionStatus.TRANSCRIBING))
    }

    @Test
    fun `TRANSCRIBED status is not eligible - already done`() {
        assertFalse(isEligibleForTranscription(SessionStatus.TRANSCRIBED))
    }

    @Test
    fun `EDITED status is not eligible - transcript already exists`() {
        assertFalse(isEligibleForTranscription(SessionStatus.EDITED))
    }

    @Test
    fun `ARCHIVED status is not eligible`() {
        assertFalse(isEligibleForTranscription(SessionStatus.ARCHIVED))
    }

    @Test
    fun `IDLE and RECORDING statuses are not eligible`() {
        assertFalse(isEligibleForTranscription(SessionStatus.IDLE))
        assertFalse(isEligibleForTranscription(SessionStatus.RECORDING))
    }

    @Test
    fun `PARTIAL_TRANSCRIPTION status is eligible for resume`() {
        assertTrue(isEligibleForTranscription(SessionStatus.PARTIAL_TRANSCRIPTION))
    }

    @Test
    fun `eligible statuses are exactly RECORDED, FAILED, and PARTIAL_TRANSCRIPTION`() {
        val allStatuses = SessionStatus.entries
        val expectedEligible =
            setOf(
                SessionStatus.RECORDED,
                SessionStatus.FAILED,
                SessionStatus.PARTIAL_TRANSCRIPTION,
            )
        val actualEligible = allStatuses.filter { isEligibleForTranscription(it) }.toSet()
        assertEquals(expectedEligible, actualEligible)
    }

    @Test
    fun `bulk eligibility filter keeps only RECORDED, FAILED, and PARTIAL_TRANSCRIPTION from a mixed list`() {
        val statuses =
            listOf(
                SessionStatus.RECORDED,
                SessionStatus.TRANSCRIBING,
                SessionStatus.TRANSCRIBED,
                SessionStatus.FAILED,
                SessionStatus.EDITED,
                SessionStatus.PARTIAL_TRANSCRIPTION,
            )
        val eligible = statuses.filter { isEligibleForTranscription(it) }
        assertEquals(
            listOf(SessionStatus.RECORDED, SessionStatus.FAILED, SessionStatus.PARTIAL_TRANSCRIPTION),
            eligible,
        )
    }

    @Test
    fun `bulk feedback counts match expected queued and skipped values`() {
        val statuses =
            listOf(
                SessionStatus.RECORDED,
                SessionStatus.FAILED,
                SessionStatus.PARTIAL_TRANSCRIPTION,
                SessionStatus.TRANSCRIBING,
                SessionStatus.TRANSCRIBED,
                SessionStatus.EDITED,
            )
        var queued = 0
        var skipped = 0
        statuses.forEach { status ->
            if (isEligibleForTranscription(status)) queued++ else skipped++
        }
        assertEquals(3, queued)
        assertEquals(3, skipped)
    }

    @Test
    fun `all items already transcribed produces zero queued and correct skip count`() {
        val statuses =
            listOf(
                SessionStatus.TRANSCRIBED,
                SessionStatus.EDITED,
                SessionStatus.TRANSCRIBING,
            )
        val queued = statuses.count { isEligibleForTranscription(it) }
        val skipped = statuses.size - queued
        assertEquals(0, queued)
        assertEquals(3, skipped)
    }

    @Test
    fun `all items eligible produces full queued count and zero skipped`() {
        val statuses =
            listOf(
                SessionStatus.RECORDED,
                SessionStatus.RECORDED,
                SessionStatus.FAILED,
            )
        val queued = statuses.count { isEligibleForTranscription(it) }
        val skipped = statuses.size - queued
        assertEquals(3, queued)
        assertEquals(0, skipped)
    }
}
