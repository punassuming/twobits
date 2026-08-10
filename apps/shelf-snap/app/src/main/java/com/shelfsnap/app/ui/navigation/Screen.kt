package com.shelfsnap.app.ui.navigation

sealed class Screen(
    val route: String,
) {
    data object Onboarding : Screen("onboarding")

    data object Inventory : Screen("inventory")

    /** Camera supports an optional [itemId] to append photos to an existing item. */
    data object Camera : Screen("camera?itemId={itemId}") {
        fun createRoute(itemId: Long = -1L) = "camera?itemId=$itemId"
    }

    data object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: Long) = "item_detail/$itemId"
    }

    data object MarketResearch : Screen("market_research/{itemId}") {
        fun createRoute(itemId: Long) = "market_research/$itemId"
    }

    data object ListingSummary : Screen("listing_summary/{itemId}") {
        fun createRoute(itemId: Long) = "listing_summary/$itemId"
    }

    data object Pro : Screen("pro")

    data object Summary : Screen("summary")

    data object Settings : Screen("settings")

    data object WhatsNew : Screen("whats_new")

    data object AiConfig : Screen("ai_config")

    data object Services : Screen("services")

    data object DebugLog : Screen("debug_log")
}
