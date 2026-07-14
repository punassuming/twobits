package com.twobits.pricedrop.data.provider.contracts

import com.google.gson.JsonObject
import com.twobits.pricedrop.data.remote.PriceParser
import com.twobits.pricedrop.domain.product.Availability
import com.twobits.pricedrop.domain.product.ItemCondition
import com.twobits.pricedrop.domain.product.Merchant
import com.twobits.pricedrop.domain.product.Money
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer
import com.twobits.pricedrop.domain.product.Promotion
import com.twobits.pricedrop.domain.product.PromotionApplicability
import com.twobits.pricedrop.domain.product.ProviderRef
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

internal object ShoppingMapper {
    fun map(
        item: JsonObject,
        providerId: String,
        executionMode: String,
    ): ProductCandidate? {
        val title = item.string("title") ?: return null
        val url = item.string("product_link", "link") ?: return null
        val source = item.string("seller", "source") ?: "Unknown merchant"
        val currency = item.string("currency")?.uppercase() ?: "USD"
        val price = item.number("extracted_price") ?: PriceParser.parse(item.string("price"))
        val provider = ProviderRef(providerId, executionMode)
        val offer =
            price?.let {
                val money = Money(it.toMinor(), currency)
                ProductOffer(
                    merchant = Merchant(normalizeMerchant(source), source),
                    listingId = item.string("product_id", "id"),
                    seller = source,
                    itemPrice = money,
                    totalPrice = money,
                    availability = availability(item.string("delivery", "availability")),
                    condition = condition(item.string("condition")),
                    productUrl = url,
                    observedAt = Instant.now(),
                    promotions = promotions(item, provider),
                    provider = provider,
                )
            }
        return ProductCandidate(
            provider = provider,
            providerProductId = item.string("product_id", "id"),
            title = title,
            identity =
                ProductIdentity(
                    brand = item.string("brand"),
                    model = item.string("model"),
                    manufacturerPartNumber = item.string("mpn", "manufacturer_part_number"),
                    gtin = item.string("gtin"),
                    upc = item.string("upc"),
                    ean = item.string("ean"),
                    asin = item.string("asin"),
                ),
            imageUrls = listOfNotNull(item.string("imageUrl", "image", "thumbnail")),
            offer = offer,
            sourceUrl = url,
        )
    }

    private fun JsonObject.string(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }

    private fun JsonObject.number(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun Double.toMinor(): Long =
        BigDecimal.valueOf(this)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private fun normalizeMerchant(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun availability(value: String?): Availability =
        when {
            value == null -> Availability.UNKNOWN
            value.contains("out", ignoreCase = true) -> Availability.OUT_OF_STOCK
            value.contains("preorder", ignoreCase = true) -> Availability.PREORDER
            else -> Availability.IN_STOCK
        }

    private fun condition(value: String?): ItemCondition =
        when {
            value == null -> ItemCondition.UNKNOWN
            value.contains("refurb", ignoreCase = true) || value.contains("renew", ignoreCase = true) -> ItemCondition.REFURBISHED
            value.contains("used", ignoreCase = true) -> ItemCondition.USED
            value.contains("new", ignoreCase = true) -> ItemCondition.NEW
            else -> ItemCondition.UNKNOWN
        }

    private fun promotions(
        item: JsonObject,
        provider: ProviderRef,
    ): List<Promotion> =
        sequenceOf("coupon", "promotion", "discount")
            .mapNotNull { field -> item.string(field) }
            .distinct()
            .map { description ->
                Promotion(
                    description = description,
                    applicability = PromotionApplicability.POSSIBLY_APPLICABLE,
                    confidence = 0.5,
                    source = provider,
                )
            }.toList()
}
