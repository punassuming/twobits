package com.twobits.pricedrop.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.twobits.pricedrop.data.remote.dto.CouponDto
import com.twobits.pricedrop.data.remote.dto.CouponsResponseDto

/** Normalizes LinkMyDeals' feed into the stable response shared by BYOK and Pro. */
internal object LinkMyDealsCouponMapper {
    private const val MAX_COUPONS = 10

    fun map(
        payload: JsonObject,
        query: String,
        domain: String?,
    ): CouponsResponseDto {
        val accepted = payload["result"]?.asString?.lowercase() in setOf("1", "true")
        require(accepted) { payload.string("message") ?: "LinkMyDeals rejected the request" }

        val queryTokens = tokens(query)
        val normalizedDomain = normalizeDomain(domain.orEmpty())
        val offers = payload["offers"]?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val ranked =
            offers
                .mapNotNull { element ->
                    val offer = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val code = offer.string("Coupon Code", "coupon_code")?.trim().orEmpty()
                    val kind = offer.string("Type", "type").orEmpty()
                    if (code.isBlank() || !kind.contains("coupon", ignoreCase = true)) return@mapNotNull null

                    val store = offer.string("Store", "store").orEmpty()
                    val homepage = offer.string("Merchant Homepage", "merchant_homepage").orEmpty()
                    val text =
                        listOf(
                            store,
                            homepage,
                            offer.string("Title", "title").orEmpty(),
                            offer.string("Offer Text", "offer_text").orEmpty(),
                            offer.string("Description", "description").orEmpty(),
                            offer.string("Categories", "categories").orEmpty(),
                        ).joinToString(" ").lowercase()
                    val score = matchScore(text, homepage, normalizedDomain, queryTokens)
                    if (score <= 0) return@mapNotNull null

                    RankedCoupon(
                        score = score,
                        stableId = offer.string("LMD ID", "lmd_id").orEmpty(),
                        coupon =
                            CouponDto(
                                code = code,
                                description =
                                    offer.string("Description", "description")
                                        ?: offer.string("Title", "title")
                                        ?: offer.string("Offer Text", "offer_text"),
                                discount = offer.string("Offer Value", "offer_value"),
                                type = offer.string("Offer", "offer") ?: kind,
                                expires = offer.string("End Date", "end_date"),
                                store = store.ifBlank { null },
                            ),
                    )
                }.sortedWith(compareByDescending<RankedCoupon> { it.score }.thenBy { it.stableId }.thenBy { it.coupon.code })

        return CouponsResponseDto(ranked.take(MAX_COUPONS).map { it.coupon })
    }

    private fun matchScore(
        text: String,
        homepage: String,
        domain: String,
        queryTokens: List<String>,
    ): Int {
        var score = queryTokens.count { it in text } * 3
        if (domain.isNotBlank()) {
            val homepageDomain = normalizeDomain(homepage)
            if (homepageDomain == domain || homepageDomain.endsWith(".$domain")) score += 100
            val merchantToken = domain.substringBefore('.')
            if (merchantToken.length >= 2 && merchantToken in text) score += 25
        }
        return score
    }

    private fun tokens(value: String): List<String> =
        value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .distinct()

    private fun normalizeDomain(value: String): String =
        value
            .trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')

    private fun JsonObject.string(vararg names: String): String? {
        val requested = names.map { it.lowercase() }.toSet()
        val entry = entrySet().firstOrNull { it.key.lowercase() in requested } ?: return null
        return entry.value.takeUnless { it.isJsonNull }?.asString
    }

    private data class RankedCoupon(
        val score: Int,
        val stableId: String,
        val coupon: CouponDto,
    )
}
