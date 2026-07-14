package com.twobits.pricedrop.domain.matching

import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.domain.product.MatchAssessment
import com.twobits.pricedrop.domain.product.MatchClassification
import com.twobits.pricedrop.domain.product.MatchConflict
import com.twobits.pricedrop.domain.product.MatchEvidence
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class ProductResolver
    @Inject
    constructor() {
        fun assess(
            request: ProductSearchRequest,
            candidate: ProductCandidate,
        ): MatchAssessment {
            val evidence = mutableListOf<MatchEvidence>()
            val conflicts = mutableListOf<MatchConflict>()
            compareIdentifier("gtin", request.identifiers.gtin, candidate.identity.gtin, 1.0, evidence, conflicts)
            compareIdentifier("upc", request.identifiers.upc, candidate.identity.upc, 1.0, evidence, conflicts)
            compareIdentifier("ean", request.identifiers.ean, candidate.identity.ean, 1.0, evidence, conflicts)
            compareIdentifier("asin", request.identifiers.asin, candidate.identity.asin, 1.0, evidence, conflicts)
            compareIdentifier(
                "manufacturerPartNumber",
                request.identifiers.manufacturerPartNumber,
                candidate.identity.manufacturerPartNumber,
                0.9,
                evidence,
                conflicts,
            )
            val variantConflicts = variantConflicts(request.query, candidate.title)
            conflicts += variantConflicts
            val titleScore = titleCoverage(request.query, candidate.title)
            if (titleScore > 0.0) {
                evidence += MatchEvidence("title_tokens", 0.65 * titleScore, "${(titleScore * 100).toInt()}% query token coverage")
            }
            if (same(request.identifiers.brand, candidate.identity.brand) && same(request.identifiers.model, candidate.identity.model)) {
                evidence += MatchEvidence("brand_model", 0.8, "Exact normalized brand and model")
            }
            val strongIdentifier = evidence.any { it.signal != "title_tokens" && it.score >= 0.9 }
            val rawScore = if (strongIdentifier) evidence.maxOf { it.score } else evidence.sumOf { it.score }
            val score = if (conflicts.isEmpty()) min(1.0, rawScore) else 0.0
            val classification =
                when {
                    conflicts.isNotEmpty() -> MatchClassification.UNRELATED
                    strongIdentifier && score >= 0.95 -> MatchClassification.EXACT
                    score >= 0.85 -> MatchClassification.HIGH_CONFIDENCE
                    score >= 0.45 -> MatchClassification.POSSIBLE_VARIANT
                    else -> MatchClassification.UNRELATED
                }
            return MatchAssessment(score, classification, evidence, conflicts)
        }

        fun targetFor(request: ProductSearchRequest): ProductIdentity = request.identifiers

        private fun compareIdentifier(
            field: String,
            expected: String?,
            actual: String?,
            weight: Double,
            evidence: MutableList<MatchEvidence>,
            conflicts: MutableList<MatchConflict>,
        ) {
            if (expected.isNullOrBlank() || actual.isNullOrBlank()) return
            if (normalize(expected) == normalize(actual)) {
                evidence += MatchEvidence(field, weight, "Exact $field match")
            } else {
                conflicts += MatchConflict(field, expected, actual)
            }
        }

        private fun same(
            left: String?,
            right: String?,
        ): Boolean = !left.isNullOrBlank() && !right.isNullOrBlank() && normalize(left) == normalize(right)

        private fun titleCoverage(
            query: String,
            title: String,
        ): Double {
            val queryTokens = tokens(query)
            if (queryTokens.isEmpty()) return 0.0
            val titleTokens = tokens(title)
            return queryTokens.count { it in titleTokens }.toDouble() / queryTokens.size
        }

        private fun variantConflicts(
            query: String,
            title: String,
        ): List<MatchConflict> {
            val conflicts = mutableListOf<MatchConflict>()
            compareVariant("capacity", CAPACITY.find(query)?.value, CAPACITY.find(title)?.value, conflicts)
            compareVariant("color", color(query), color(title), conflicts)
            return conflicts
        }

        private fun compareVariant(
            field: String,
            expected: String?,
            actual: String?,
            conflicts: MutableList<MatchConflict>,
        ) {
            if (!expected.isNullOrBlank() && !actual.isNullOrBlank() && normalize(expected) != normalize(actual)) {
                conflicts += MatchConflict(field, expected, actual)
            }
        }

        private fun color(value: String): String? = COLORS.firstOrNull { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) }

        private fun tokens(value: String): Set<String> =
            normalize(value).split(' ').filter { it.length > 1 && it !in STOP_WORDS }.toSet()

        private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        private companion object {
            val CAPACITY = Regex("\\b\\d+\\s?(?:gb|tb)\\b", RegexOption.IGNORE_CASE)
            val COLORS = setOf("black", "white", "blue", "red", "green", "silver", "gold", "gray", "grey", "pink")
            val STOP_WORDS = setOf("the", "and", "for", "with", "new")
        }
    }
