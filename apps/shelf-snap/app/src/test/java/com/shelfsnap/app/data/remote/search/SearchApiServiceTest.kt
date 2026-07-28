package com.shelfsnap.app.data.remote.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiServiceTest {
    @Test
    fun `ebay queries use completed sold listings engine`() {
        val url = buildSearchApiUrl("grandfather clock site:ebay.com/itm sold", limit = 8)

        assertEquals("ebay_search", url.queryParameter("engine"))
        assertEquals("sold_listings", url.queryParameter("filters"))
        assertEquals("grandfather clock", url.queryParameter("q"))
        assertFalse(url.queryParameterNames.contains("api_key"))
    }

    @Test
    fun `ebay for-sale queries route through the engine without the sold filter`() {
        val url = buildSearchApiUrl("grandfather clock site:ebay.com/itm for sale", limit = 8)

        assertEquals("ebay_search", url.queryParameter("engine"))
        assertEquals(null, url.queryParameter("filters"))
        assertEquals("grandfather clock", url.queryParameter("q"))
    }

    @Test
    fun `ebay response preserves structured sold price evidence`() {
        val response =
            """
            {
              "organic_results": [
                {
                  "title": "Traditional grandfather clock",
                  "link": "https://www.ebay.com/itm/123",
                  "condition": "Used",
                  "price": "${'$'}249.95",
                  "extracted_price": 249.95
                }
              ]
            }
            """.trimIndent()

        val result = parseSearchApiResponse(response, limit = 8, soldOnly = true).single()

        assertEquals("ebay", result.platformKey)
        assertEquals(249.95, result.price ?: 0.0, 0.001)
        assertEquals(true, result.sold)
        assertTrue(result.snippet.contains("Completed/sold listing"))
        assertTrue(result.snippet.contains("Condition: Used"))
    }

    @Test
    fun `google response keeps marketplace price without claiming a sale`() {
        val response =
            """
            {
              "organic_results": [
                {
                  "title": "Grandfather clock listing",
                  "link": "https://www.mercari.com/us/item/m123/",
                  "price": "${'$'}175.00"
                }
              ]
            }
            """.trimIndent()

        val result = parseSearchApiResponse(response, limit = 8, soldOnly = false).single()

        assertEquals("mercari", result.platformKey)
        assertEquals(175.0, result.price ?: 0.0, 0.001)
        assertEquals(null, result.sold)
    }
}
