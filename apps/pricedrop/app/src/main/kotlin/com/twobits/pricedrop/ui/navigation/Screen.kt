package com.twobits.pricedrop.ui.navigation

sealed class Screen(val route: String) {
    data object Watch : Screen("watch")
    data object Drops : Screen("drops")
    data object Search : Screen("search")
    data object BarcodeScan : Screen("barcode_scan")
    data object Ask : Screen("ask")
    data object Settings : Screen("settings")
    data object Pro : Screen("pro")
    data object AiConfig : Screen("ai_config")
    data object WhatsNew : Screen("whats_new")
    data object Onboarding : Screen("onboarding")

    data object ProductDetail : Screen("product/{productId}") {
        fun createRoute(productId: Long) = "product/$productId"
    }
}
