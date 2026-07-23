package com.shelfsnap.app

import com.shelfsnap.app.data.remote.VisionAnalysisService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Verifies that raw HTTP codes and exceptions are mapped to the friendly,
 * user-facing messages shown in the camera screen.
 */
class VisionAnalysisErrorTest {
    @Test
    fun `401 and 403 map to invalid key message`() {
        assertEquals(VisionAnalysisService.ERROR_INVALID_KEY, VisionAnalysisService.friendlyHttpError(401))
        assertEquals(VisionAnalysisService.ERROR_INVALID_KEY, VisionAnalysisService.friendlyHttpError(403))
    }

    @Test
    fun `429 maps to rate-limited message`() {
        assertEquals(VisionAnalysisService.ERROR_RATE_LIMITED, VisionAnalysisService.friendlyHttpError(429))
    }

    @Test
    fun `5xx maps to unavailable message`() {
        assertEquals(VisionAnalysisService.ERROR_UNAVAILABLE, VisionAnalysisService.friendlyHttpError(500))
        assertEquals(VisionAnalysisService.ERROR_UNAVAILABLE, VisionAnalysisService.friendlyHttpError(503))
    }

    @Test
    fun `timeout maps to timeout message`() {
        assertEquals(
            VisionAnalysisService.ERROR_TIMEOUT,
            VisionAnalysisService.friendlyNetworkError(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun `generic IO error maps to network message`() {
        assertEquals(
            VisionAnalysisService.ERROR_NETWORK,
            VisionAnalysisService.friendlyNetworkError(IOException("boom")),
        )
    }

    @Test
    fun `unexpected exception maps to unknown message`() {
        assertEquals(
            VisionAnalysisService.ERROR_UNKNOWN,
            VisionAnalysisService.friendlyNetworkError(IllegalStateException("?")),
        )
    }

    @Test
    fun `category aliases normalize to the standard taxonomy`() {
        assertEquals(
            "Clothing & Accessories",
            VisionAnalysisService.normalizeCategory(" apparel "),
        )
        assertEquals(
            "Home & Kitchen",
            VisionAnalysisService.normalizeCategory("Kitchenware"),
        )
        assertEquals(
            "Sports & Outdoors",
            VisionAnalysisService.normalizeCategory("sporting goods"),
        )
    }

    @Test
    fun `unknown or missing categories default to other`() {
        assertEquals("Other", VisionAnalysisService.normalizeCategory("Appliances"))
        assertEquals("Other", VisionAnalysisService.normalizeCategory(null))
    }
}
