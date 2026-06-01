package dev.scrybe.core.billing

/**
 * Replace REVENUECAT_PUBLIC_KEY with the public SDK key from your
 * RevenueCat project (https://app.revenuecat.com → Project settings → API keys).
 * This key is intentionally public — it identifies your app to RevenueCat.
 */
internal const val REVENUECAT_PUBLIC_KEY = "YOUR_REVENUECAT_PUBLIC_KEY"

/** RevenueCat entitlement ID configured in the dashboard. */
internal const val ENTITLEMENT_PRO = "pro"

/** Play Store subscription product IDs — must match Google Play Console. */
const val PRODUCT_PRO_MONTHLY = "scrybe_pro_monthly"
const val PRODUCT_PRO_ANNUAL = "scrybe_pro_annual"
