package com.twobits.pricedrop.data.provider.contracts

import com.google.gson.JsonParser
import com.twobits.pricedrop.domain.product.PromotionApplicability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingMapperTest {
    @Test
    fun `Serper listing normalizes into minor-unit offer`() {
        val item =
            JsonParser.parseString(
                """
                {
                  "title": "Sony WH-1000XM6 Black",
                  "price": "${'$'}448.00",
                  "source": "Example Store",
                  "link": "https://example.test/sony",
                  "productId": "serper-100",
                  "thumbnail": "https://example.test/sony.png",
                  "upc": "027242930124",
                  "coupon": "Save 10% at checkout"
                }
                """.trimIndent(),
            ).asJsonObject

        val candidate = checkNotNull(ShoppingMapper.map(item, "serper", "BYOK"))

        assertEquals(44_800L, candidate.offer?.totalPrice?.amountMinor)
        assertEquals("USD", candidate.offer?.totalPrice?.currency)
        assertEquals("027242930124", candidate.identity.upc)
        assertEquals(PromotionApplicability.POSSIBLY_APPLICABLE, candidate.offer?.promotions?.single()?.applicability)
        assertTrue(candidate.rawProviderData.isEmpty())
    }

    @Test
    fun `SearchAPI listing retains identifiers and numeric extracted price`() {
        val item =
            JsonParser.parseString(
                """
                {
                  "title": "Camera Body",
                  "extracted_price": 1299.95,
                  "currency": "USD",
                  "seller": "Camera Shop",
                  "product_link": "https://example.test/camera",
                  "mpn": "CAM-100"
                }
                """.trimIndent(),
            ).asJsonObject

        val candidate = checkNotNull(ShoppingMapper.map(item, "searchapi", "BYOK"))

        assertEquals(129_995L, candidate.offer?.itemPrice?.amountMinor)
        assertEquals("CAM-100", candidate.identity.manufacturerPartNumber)
        assertNull(candidate.identity.asin)
    }
}
