package com.shelfsnap.app

import com.shelfsnap.app.data.remote.PriceResearchService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Verifies the price-research service maps raw HTTP codes and exceptions to the
 * friendly, user-facing messages (mirrors VisionAnalysisErrorTest).
 */
class PriceResearchErrorTest {
    @Test
    fun `401 and 403 map to invalid key message`() {
        assertEquals(PriceResearchService.ERROR_INVALID_KEY, PriceResearchService.friendlyHttpError(401))
        assertEquals(PriceResearchService.ERROR_INVALID_KEY, PriceResearchService.friendlyHttpError(403))
    }

    @Test
    fun `429 maps to rate-limited message`() {
        assertEquals(PriceResearchService.ERROR_RATE_LIMITED, PriceResearchService.friendlyHttpError(429))
    }

    @Test
    fun `5xx maps to unavailable message`() {
        assertEquals(PriceResearchService.ERROR_UNAVAILABLE, PriceResearchService.friendlyHttpError(500))
        assertEquals(PriceResearchService.ERROR_UNAVAILABLE, PriceResearchService.friendlyHttpError(503))
    }

    @Test
    fun `timeout maps to timeout message`() {
        assertEquals(
            PriceResearchService.ERROR_TIMEOUT,
            PriceResearchService.friendlyNetworkError(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun `generic IO error maps to network message`() {
        assertEquals(
            PriceResearchService.ERROR_NETWORK,
            PriceResearchService.friendlyNetworkError(IOException("boom")),
        )
    }

    @Test
    fun `unexpected exception maps to unknown message`() {
        assertEquals(
            PriceResearchService.ERROR_UNKNOWN,
            PriceResearchService.friendlyNetworkError(IllegalStateException("?")),
        )
    }
}
