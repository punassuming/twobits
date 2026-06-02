package com.shelfsnap.app

import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.shelfsnap.app.data.remote.search.SearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAndListingTest {

    @Test
    fun `platform keys round-trip via fromKey`() {
        Platform.entries.forEach { p ->
            assertEquals(p, Platform.fromKey(p.key))
        }
    }

    @Test
    fun `unknown platform key resolves to null`() {
        assertNull(Platform.fromKey("etsy"))
        assertNull(Platform.fromKey(""))
    }

    @Test
    fun `hasActiveListing reflects an active listing`() {
        val item = Item(
            listings = listOf(PlatformListing("ebay", ListingStatus.ACTIVE, 25.0))
        )
        assertTrue(item.hasActiveListing)
        assertFalse(item.hasSold)
    }

    @Test
    fun `hasSold reflects a sold listing and is not active`() {
        val item = Item(
            listings = listOf(PlatformListing("mercari", ListingStatus.SOLD, 30.0))
        )
        assertTrue(item.hasSold)
        assertFalse(item.hasActiveListing)
    }

    @Test
    fun `item with no listings has neither flag`() {
        val item = Item()
        assertFalse(item.hasActiveListing)
        assertFalse(item.hasSold)
    }

    @Test
    fun `search provider keys round-trip and default to NONE`() {
        SearchProvider.entries.forEach { p ->
            assertEquals(p, SearchProvider.fromKey(p.key))
        }
        assertEquals(SearchProvider.NONE, SearchProvider.fromKey("unknown"))
        assertEquals(SearchProvider.NONE, SearchProvider.fromKey(""))
    }
}
