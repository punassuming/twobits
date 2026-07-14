package com.twobits.pricedrop.data.provider.pro

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDiscoveryContractTest {
    @Test
    fun `shared v2 fixture deserializes without provider payload leakage`() {
        val json = checkNotNull(javaClass.classLoader?.getResource("discover-response.json")).readText()
        val response = Gson().fromJson(json, ProductDiscoveryGatewayResponse::class.java)

        assertEquals(2, response.schemaVersion)
        assertEquals(44_800L, response.products.single().offers.single().totalPrice.amountMinor)
        assertEquals("USD", response.products.single().offers.single().totalPrice.currency)
        assertEquals(setOf("serper", "searchapi"), response.providerDiagnostics.map { it.provider }.toSet())
        assertTrue(json.contains("canonicalProductId"))
        assertFalse(json.contains("rawProviderData"))
    }
}
