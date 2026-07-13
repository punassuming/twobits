package com.twobits.pricedrop.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMyDealsCouponMapperTest {
    @Test
    fun `maps and ranks matching coupon codes`() {
        val payload =
            requireNotNull(javaClass.classLoader?.getResourceAsStream("linkmydeals-offers.json"))
                .bufferedReader()
                .use { JsonParser.parseReader(it).asJsonObject }

        val result = LinkMyDealsCouponMapper.map(payload, "wireless headphones", "www.amazon.com")

        assertEquals(listOf("SAVE10", "AUDIO5"), result.coupons.map { it.code })
        assertEquals("10%", result.coupons.first().discount)
        assertEquals("Amazon", result.coupons.first().store)
    }

    @Test
    fun `rejects unsuccessful provider response`() {
        val payload = JsonParser.parseString("{\"result\":false,\"message\":\"bad key\"}").asJsonObject

        val failure = runCatching { LinkMyDealsCouponMapper.map(payload, "headphones", null) }

        assertTrue(failure.exceptionOrNull()?.message?.contains("bad key") == true)
    }
}
