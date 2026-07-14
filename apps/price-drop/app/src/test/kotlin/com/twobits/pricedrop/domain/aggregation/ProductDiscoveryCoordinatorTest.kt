package com.twobits.pricedrop.domain.aggregation

import com.twobits.pricedrop.data.provider.contracts.OfferProvider
import com.twobits.pricedrop.data.provider.contracts.ProductDetailsProvider
import com.twobits.pricedrop.data.provider.contracts.ProductSearchProvider
import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.data.provider.contracts.PromotionProvider
import com.twobits.pricedrop.data.provider.contracts.ProviderCapability
import com.twobits.pricedrop.data.provider.contracts.ProviderDescriptor
import com.twobits.pricedrop.data.provider.contracts.ProviderResult
import com.twobits.pricedrop.data.provider.registry.ProviderRegistry
import com.twobits.pricedrop.domain.matching.ProductResolver
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProviderRef
import com.twobits.pricedrop.domain.product.ProviderStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDiscoveryCoordinatorTest {
    @Test
    fun `all providers execute and one failure preserves successful results`() =
        runTest {
            val calls = mutableListOf<String>()
            val providers =
                listOf(
                    fakeProvider("serper", calls, listOf(candidate("serper"))),
                    fakeProvider("searchapi", calls, listOf(candidate("searchapi"))),
                    fakeProvider("broken", calls, failure = true),
                )
            val coordinator =
                ProductDiscoveryCoordinator(
                    registry =
                        object : ProviderRegistry {
                            override suspend fun searchProviders(): List<ProductSearchProvider> = providers

                            override suspend fun detailProviders(): List<ProductDetailsProvider> = emptyList()

                            override suspend fun offerProviders(): List<OfferProvider> = emptyList()

                            override suspend fun promotionProviders(): List<PromotionProvider> = emptyList()
                        },
                    resolver = ProductResolver(),
                    offerAggregator = OfferAggregator(),
                )

            val result = coordinator.discover(ProductSearchRequest("Sony WH-1000XM6"))

            assertEquals(setOf("serper", "searchapi", "broken"), calls.toSet())
            assertTrue(result.products.isNotEmpty())
            assertEquals(ProviderStatus.ERROR, result.providerDiagnostics.single { it.provider == "broken" }.status)
        }

    private fun fakeProvider(
        id: String,
        calls: MutableList<String>,
        candidates: List<ProductCandidate> = emptyList(),
        failure: Boolean = false,
    ) =
        object : ProductSearchProvider {
            override val descriptor = ProviderDescriptor(id, id, setOf(ProviderCapability.SEARCH))

            override suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>> {
                calls += id
                return if (failure) ProviderResult.Failure("fixture failure") else ProviderResult.Success(candidates)
            }
        }

    private fun candidate(provider: String) =
        ProductCandidate(
            provider = ProviderRef(provider, "BYOK"),
            title = "Sony WH-1000XM6 headphones",
            sourceUrl = "https://example.test/$provider",
        )
}
