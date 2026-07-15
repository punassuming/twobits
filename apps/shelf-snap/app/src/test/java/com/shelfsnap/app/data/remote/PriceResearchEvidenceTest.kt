package com.shelfsnap.app.data.remote

import com.shelfsnap.app.data.model.MarketComp
import com.shelfsnap.app.data.remote.search.WebSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceResearchEvidenceTest {
    @Test
    fun `structured sold evidence becomes a comparable without model assistance`() {
        val evidence =
            SearchEvidence(
                results =
                    listOf(
                        WebSearchResult(
                            title = "Sold grandfather clock",
                            url = "https://www.ebay.com/itm/123",
                            snippet = "${'$'}249.95 · Completed/sold listing",
                            platformKey = "ebay",
                            price = 249.95,
                            sold = true,
                        ),
                    ),
            )

        val comparable = mergeVerifiedComparableListings(emptyList(), evidence).single()

        assertEquals("ebay", comparable.platformKey)
        assertEquals(249.95, comparable.price, 0.001)
        assertTrue(comparable.sold)
    }

    @Test
    fun `model comparables without an exact evidence url are rejected`() {
        val evidence =
            SearchEvidence(
                results =
                    listOf(
                        WebSearchResult(
                            title = "Evidence",
                            url = "https://www.ebay.com/itm/real",
                            snippet = "Evidence only",
                        ),
                    ),
            )
        val invented =
            MarketComp(
                platformKey = "ebay",
                title = "Invented listing",
                price = 300.0,
                sold = true,
                date = "Today",
                sourceUrl = "https://www.ebay.com/itm/invented",
            )

        assertTrue(mergeVerifiedComparableListings(listOf(invented), evidence).isEmpty())
    }
}
