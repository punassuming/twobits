package com.shelfsnap.app.data.remote.search

import com.shelfsnap.app.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarketplaceKeyTest {
    @Test
    fun `every marketplace key resolves to a real Platform`() {
        val urls =
            listOf(
                "https://www.ebay.com/itm/123",
                "https://www.mercari.com/us/item/m456/",
                "https://offerup.com/item/detail/789",
                "https://www.facebook.com/marketplace/item/321",
                "https://sfbay.craigslist.org/sby/ele/d/thing/999.html",
            )

        urls.forEach { url ->
            val key = marketplaceKeyFromUrl(url)
            assertNotNull("Expected a marketplace key for $url", key)
            // A key that Platform can't resolve is silently dropped when comps are built, so an
            // unresolvable key is the same as no evidence at all.
            assertNotNull("Platform.fromKey could not resolve '$key' (from $url)", Platform.fromKey(key!!))
        }
    }

    @Test
    fun `facebook urls map to the fbmarket platform key`() {
        // Regression: this returned "facebook", which Platform.fromKey rejects, so every
        // Facebook Marketplace comparable was discarded before it reached the UI.
        assertEquals("fbmarket", marketplaceKeyFromUrl("https://www.facebook.com/marketplace/item/321"))
    }

    @Test
    fun `non marketplace urls are not treated as postings`() {
        listOf(
            "https://example.com/blog/how-to-price-used-electronics",
            "https://www.intel.com/content/www/us/en/products/nuc.html",
            "https://reddit.com/r/homelab/comments/abc",
        ).forEach { assertNull("$it should not count as a posting", marketplaceKeyFromUrl(it)) }
    }

    @Test
    fun `only SearchAPI reports structured listing support`() {
        assertEquals(
            listOf(SearchProvider.SEARCHAPI),
            SearchProvider.entries.filter { it.suppliesStructuredListings },
        )
    }
}
