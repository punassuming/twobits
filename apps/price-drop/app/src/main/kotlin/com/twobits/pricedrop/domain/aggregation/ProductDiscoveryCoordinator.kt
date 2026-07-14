package com.twobits.pricedrop.domain.aggregation

import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.data.provider.contracts.ProviderResult
import com.twobits.pricedrop.data.provider.registry.ProviderRegistry
import com.twobits.pricedrop.domain.matching.ProductResolver
import com.twobits.pricedrop.domain.product.MatchClassification
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductDiscoveryResult
import com.twobits.pricedrop.domain.product.ProviderDiagnostic
import com.twobits.pricedrop.domain.product.ProviderStatus
import com.twobits.pricedrop.domain.product.ResolvedProduct
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductDiscoveryCoordinator
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val resolver: ProductResolver,
        private val offerAggregator: OfferAggregator,
    ) {
        suspend fun discover(request: ProductSearchRequest): ProductDiscoveryResult =
            coroutineScope {
                val providers = registry.searchProviders()
                val semaphore = Semaphore(MAX_CONCURRENT_PROVIDERS)
                val outcomes =
                    providers.map { provider ->
                        async {
                            val started = System.currentTimeMillis()
                            val result =
                                runCatching {
                                    withTimeout(PROVIDER_TIMEOUT_MS) {
                                        semaphore.withPermit { provider.search(request) }
                                    }
                                }
                            val latency = System.currentTimeMillis() - started
                            when {
                                result.isFailure ->
                                    ProviderOutcome(
                                        emptyList(),
                                        listOf(
                                            ProviderDiagnostic(
                                                provider.descriptor.id,
                                                ProviderStatus.TIMEOUT,
                                                latency,
                                                0,
                                                result.exceptionOrNull()?.message,
                                            ),
                                        ),
                                    )
                                result.getOrNull() is ProviderResult.Success -> {
                                    val candidates = (result.getOrNull() as ProviderResult.Success<List<ProductCandidate>>).value
                                    ProviderOutcome(
                                        candidates,
                                        successDiagnostics(
                                            result.getOrNull() as ProviderResult.Success<List<ProductCandidate>>,
                                            provider.descriptor.id,
                                            latency,
                                        ),
                                    )
                                }
                                else -> {
                                    val failure = result.getOrNull() as ProviderResult.Failure
                                    ProviderOutcome(
                                        emptyList(),
                                        listOf(
                                            ProviderDiagnostic(
                                                provider.descriptor.id,
                                                ProviderStatus.ERROR,
                                                latency,
                                                0,
                                                failure.message,
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                val resolved = resolveAndCluster(request, outcomes.flatMap { it.candidates })
                ProductDiscoveryResult(
                    query = request.query,
                    target = resolver.targetFor(request),
                    products = resolved,
                    providerDiagnostics = outcomes.flatMap { it.diagnostics },
                )
            }

        private fun resolveAndCluster(
            request: ProductSearchRequest,
            candidates: List<ProductCandidate>,
        ): List<ResolvedProduct> =
            candidates
                .map { it to resolver.assess(request, it) }
                .filter { (_, assessment) -> assessment.classification != MatchClassification.UNRELATED }
                .groupBy { (candidate, _) -> canonicalKey(candidate) }
                .map { (key, cluster) ->
                    val best = cluster.maxBy { (_, assessment) -> assessment.score }
                    ResolvedProduct(
                        canonicalProductId = "pd_${sha256(key).take(24)}",
                        candidate = best.first,
                        assessment = best.second,
                        offers = offerAggregator.deduplicate(cluster.mapNotNull { it.first.offer }),
                    )
                }.sortedWith(
                    compareByDescending<ResolvedProduct> { it.assessment.score }
                        .thenBy { it.offers.firstOrNull()?.totalPrice?.amountMinor ?: Long.MAX_VALUE },
                )

        private fun canonicalKey(candidate: ProductCandidate): String =
            listOfNotNull(
                candidate.identity.gtin,
                candidate.identity.upc,
                candidate.identity.ean,
                candidate.identity.asin,
                candidate.identity.manufacturerPartNumber,
            ).firstOrNull()?.lowercase()
                ?: candidate.title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        private fun sha256(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private fun successDiagnostics(
            success: ProviderResult.Success<List<ProductCandidate>>,
            providerId: String,
            latencyMs: Long,
        ): List<ProviderDiagnostic> =
            success.diagnostics.ifEmpty {
                listOf(
                    ProviderDiagnostic(
                        provider = providerId,
                        status = ProviderStatus.SUCCESS,
                        latencyMs = latencyMs,
                        resultCount = success.value.size,
                    ),
                )
            }

        private data class ProviderOutcome(
            val candidates: List<ProductCandidate>,
            val diagnostics: List<ProviderDiagnostic>,
        )

        private companion object {
            const val MAX_CONCURRENT_PROVIDERS = 3
            const val PROVIDER_TIMEOUT_MS = 8_000L
        }
    }
