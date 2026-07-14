package com.twobits.pricedrop.domain.matching

import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.domain.product.MatchClassification
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProviderRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductResolverTest {
    private val resolver = ProductResolver()

    @Test
    fun `exact identifier is explainable`() {
        val result =
            resolver.assess(
                ProductSearchRequest("Sony WH-1000XM6", ProductIdentity(upc = "027242930124")),
                candidate("Sony WH-1000XM6 Black", ProductIdentity(upc = "027242930124")),
            )

        assertEquals(MatchClassification.EXACT, result.classification)
        assertTrue(result.evidence.any { it.signal == "upc" })
    }

    @Test
    fun `conflicting capacity rejects a variant`() {
        val result =
            resolver.assess(
                ProductSearchRequest("Phone Model 256GB black"),
                candidate("Phone Model 128GB black"),
            )

        assertEquals(MatchClassification.UNRELATED, result.classification)
        assertTrue(result.conflicts.any { it.field == "capacity" })
    }

    @Test
    fun `accessory false positive has insufficient query coverage`() {
        val result =
            resolver.assess(
                ProductSearchRequest("Sony WH-1000XM6 headphones"),
                candidate("Replacement ear pads for Sony headphones"),
            )

        assertEquals(MatchClassification.UNRELATED, result.classification)
    }

    private fun candidate(
        title: String,
        identity: ProductIdentity = ProductIdentity(),
    ) =
        ProductCandidate(
            provider = ProviderRef("fixture", "BYOK"),
            title = title,
            identity = identity,
            sourceUrl = "https://example.test/item",
        )
}
