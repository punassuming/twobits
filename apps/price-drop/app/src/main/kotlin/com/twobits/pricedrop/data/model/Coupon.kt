package com.twobits.pricedrop.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Coupon-verification states shown in the Product Detail coupon section. */
enum class CouponState(
    val value: String,
) {
    UNVERIFIED("unverified"),
    TESTED_VALID("tested_valid"),
    EXPIRED("expired"),
    RESTRICTED("restricted"),
    ;

    companion object {
        fun fromValue(v: String): CouponState = entries.firstOrNull { it.value == v } ?: UNVERIFIED
    }
}

/** Coupon discount kinds. */
enum class DiscountType(
    val value: String,
) {
    PERCENT("percent"),
    FIXED("fixed"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromValue(v: String?): DiscountType = entries.firstOrNull { it.value == v } ?: UNKNOWN
    }
}

@Entity(tableName = "coupons")
data class Coupon(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val code: String,
    val description: String = "",
    val discountType: String = DiscountType.UNKNOWN.value,
    val discountValue: Double = 0.0,
    val state: String = CouponState.UNVERIFIED.value,
    val source: String = "",
    val store: String = "",
    val expiresAt: String = "",
    @ColumnInfo(defaultValue = "'UNKNOWN'") val applicability: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "0.0") val confidence: Double = 0.0,
    val lastCheckedAt: Long = System.currentTimeMillis(),
)
