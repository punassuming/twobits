package com.twobits.pricedrop.domain

import com.twobits.pricedrop.data.model.DiscountType
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectivePriceTest {
    @Test
    fun `compute sums base shipping and fees`() {
        assertEquals(119.99, EffectivePrice.compute(base = 99.99, shipping = 15.0, fees = 5.0), 0.001)
    }

    @Test
    fun `compute subtracts coupon discount`() {
        assertEquals(89.99, EffectivePrice.compute(base = 99.99, couponDiscount = 10.0), 0.001)
    }

    @Test
    fun `compute never goes negative`() {
        assertEquals(0.0, EffectivePrice.compute(base = 10.0, couponDiscount = 50.0), 0.001)
    }

    @Test
    fun `percent coupon is a fraction of base`() {
        assertEquals(20.0, EffectivePrice.couponDiscount(100.0, DiscountType.PERCENT, 20.0), 0.001)
    }

    @Test
    fun `percent coupon is capped at base`() {
        assertEquals(100.0, EffectivePrice.couponDiscount(100.0, DiscountType.PERCENT, 150.0), 0.001)
    }

    @Test
    fun `fixed coupon is capped at base`() {
        assertEquals(50.0, EffectivePrice.couponDiscount(50.0, DiscountType.FIXED, 80.0), 0.001)
    }

    @Test
    fun `unknown coupon yields no discount`() {
        assertEquals(0.0, EffectivePrice.couponDiscount(100.0, DiscountType.UNKNOWN, 20.0), 0.001)
    }
}
