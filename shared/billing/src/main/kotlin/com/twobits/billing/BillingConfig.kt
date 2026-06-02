package com.twobits.billing

data class BillingConfig(
    val revenueCatPublicKey: String,
    val proEntitlementId: String = "pro",
)
