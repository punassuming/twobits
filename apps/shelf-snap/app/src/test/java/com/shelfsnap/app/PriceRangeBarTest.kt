package com.shelfsnap.app

import com.shelfsnap.app.ui.itemdetail.priceMarkerFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceRangeBarTest {

    @Test
    fun `marker sits at the midpoint for a centered value`() {
        assertEquals(0.5f, priceMarkerFraction(low = 10.0, high = 30.0, value = 20.0), 0.001f)
    }

    @Test
    fun `marker is clamped to a visible margin at the extremes`() {
        // Below/at the low end clamps to the lower margin, not 0.
        assertEquals(0.03f, priceMarkerFraction(low = 10.0, high = 30.0, value = 5.0), 0.001f)
        // Above/at the high end clamps to the upper margin, not 1.
        assertEquals(0.97f, priceMarkerFraction(low = 10.0, high = 30.0, value = 40.0), 0.001f)
    }

    @Test
    fun `degenerate range returns the midpoint`() {
        assertEquals(0.5f, priceMarkerFraction(low = 25.0, high = 25.0, value = 25.0), 0.001f)
        assertEquals(0.5f, priceMarkerFraction(low = 30.0, high = 10.0, value = 20.0), 0.001f)
    }
}
