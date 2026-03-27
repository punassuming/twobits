package dev.scrybe.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSwipeSafetyTest {

    @Test
    fun `record swipe threshold requires a deliberate majority gesture`() {
        assertTrue(RECORD_ROW_SWIPE_THRESHOLD_FRACTION > 0.5f)
    }

    @Test
    fun `record swipe edge zone keeps swipe initiation near row boundaries`() {
        assertTrue(recordRowEdgeSwipeZoneFraction() < 0.25f)
    }

    @Test
    fun `transform swipe confirmation copy is explicit`() {
        assertEquals(
            "Run Default Transform",
            recordSwipeConfirmationTitle(RecordSwipeAction.TRANSFORM),
        )
        assertEquals(
            "Run the default transform for Daily Standup?",
            recordSwipeConfirmationMessage(RecordSwipeAction.TRANSFORM, "Daily Standup"),
        )
    }

    @Test
    fun `archive swipe confirmation explains restore path`() {
        assertEquals(
            "Archive Daily Standup? You can restore it later from archived records.",
            recordSwipeConfirmationMessage(RecordSwipeAction.ARCHIVE, "Daily Standup"),
        )
    }
}
