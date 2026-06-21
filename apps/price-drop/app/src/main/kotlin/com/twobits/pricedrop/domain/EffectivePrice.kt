package com.twobits.pricedrop.domain

import com.twobits.pricedrop.data.model.DiscountType

/**
 * Effective-price math, per the PriceDrop brief:
 *
 *   effective = item price + shipping + required fees − usable coupon
 *
 * Taxes are intentionally excluded (shown separately / marked unavailable). The result is
 * never negative. Kept pure so it is trivially unit-testable.
 */
object EffectivePrice {
    /** Resolve the dollar discount a coupon yields against [base]. */
    fun couponDiscount(
        base: Double,
        discountType: DiscountType,
        discountValue: Double,
    ): Double =
        when (discountType) {
            DiscountType.PERCENT -> (base * (discountValue / 100.0)).coerceIn(0.0, base)
            DiscountType.FIXED -> discountValue.coerceIn(0.0, base)
            DiscountType.UNKNOWN -> 0.0
        }

    /** Compose the effective price from its components. */
    fun compute(
        base: Double,
        shipping: Double = 0.0,
        fees: Double = 0.0,
        couponDiscount: Double = 0.0,
    ): Double = (base + shipping + fees - couponDiscount).coerceAtLeast(0.0)
}
