package com.shelfsnap.app.data.model

/**
 * Online selling platforms Shelf Snap can research prices on and cross-list to.
 *
 * [key] is the stable identifier persisted in the database and used as a map key
 * (kept lowercase to match the original design tokens). [displayName] is shown in
 * the UI. [queryHint] is appended to web searches to bias results toward that
 * platform's marketplace. [sellUrl] opens the platform's listing-creation page —
 * Android App Links route this to the native app when installed, web browser otherwise.
 * [listingTips] are short platform-specific tips shown in the List tab.
 */
enum class Platform(
    val key: String,
    val displayName: String,
    val queryHint: String,
    val sellUrl: String,
    val listingTips: String,
    val titleCharLimit: Int,
) {
    EBAY(
        "ebay",
        "eBay",
        "ebay sold listing",
        "https://www.ebay.com/sl/sell",
        "Title capped at 80 characters · Fill Item Specifics for better search placement · 12 photos max — use all slots · Accept offers to move inventory faster",
        80,
    ),
    MERCARI(
        "mercari",
        "Mercari",
        "mercari",
        "https://www.mercari.com/sell/",
        "Casual, friendly tone converts better · Keep photos bright and square · Bundle discount attracts buyers · Free shipping increases views",
        40,
    ),
    OFFERUP(
        "offerup",
        "OfferUp",
        "offerup",
        "https://offerup.com/sell/",
        "Short punchy title works best · Local pickup preferred — note your city · Price to sell: buyers expect to negotiate down 10–20%",
        50,
    ),
    FB_MARKETPLACE(
        "fbmarket",
        "FB Marketplace",
        "facebook marketplace",
        "https://www.facebook.com/marketplace/create/item/",
        "Put price at top of description — browsers skim · Conversational tone, no hashtags · Reply quickly: first 30 min drive most sales",
        200,
    ),
    CRAIGSLIST(
        "craigslist",
        "Craigslist",
        "craigslist",
        "https://post.craigslist.org/",
        "Include dimensions for furniture · Cash or Venmo only · Repost every 48 hours to stay at the top of search results",
        200,
    ),
    ;

    companion object {
        /** Resolves a persisted [key] back to a [Platform], or null if unknown. */
        fun fromKey(key: String): Platform? = entries.firstOrNull { it.key == key }
    }
}

/** Builds a platform-specific listing text block ready to paste into the sell form. */
fun Platform.formatListingText(item: Item): String {
    val fullTitle =
        listOf(item.brand, item.model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { item.category }
    val price = item.marketResearch.suggestedPrices[key] ?: item.estimatedValue
    val condLabel =
        item.condition.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    return when (this) {
        Platform.EBAY ->
            buildString {
                appendLine(fullTitle.take(80))
                appendLine()
                appendLine("Item Specifics:")
                if (item.brand.isNotBlank()) appendLine("Brand: ${item.brand}")
                if (item.model.isNotBlank()) appendLine("Model: ${item.model}")
                appendLine("Condition: $condLabel")
                if (item.size.isNotBlank()) appendLine("Size: ${item.size}")
                if (item.color.isNotBlank()) appendLine("Color: ${item.color}")
                if (item.description.isNotBlank()) {
                    appendLine()
                    appendLine(item.description)
                }
                if (item.tags.isNotEmpty()) appendLine(item.tags.joinToString(", "))
                append("Price: \$${" %.2f".format(price).trim()}")
            }
        Platform.MERCARI ->
            buildString {
                appendLine(fullTitle)
                if (item.description.isNotBlank()) appendLine(item.description)
                if (item.size.isNotBlank()) appendLine("Size: ${item.size}")
                if (item.color.isNotBlank()) appendLine("Color: ${item.color}")
                if (item.tags.isNotEmpty()) appendLine(item.tags.joinToString(" ") { "#$it" })
                append("\$${" %.2f".format(price).trim()}")
            }
        Platform.FB_MARKETPLACE ->
            buildString {
                appendLine("\$${" %.2f".format(price).trim()} — $fullTitle")
                appendLine("Condition: $condLabel")
                if (item.description.isNotBlank()) appendLine(item.description)
                if (item.size.isNotBlank()) appendLine("Size: ${item.size}")
                if (item.color.isNotBlank()) appendLine("Color: ${item.color}")
                append("Comment or message to claim!")
            }
        Platform.OFFERUP ->
            buildString {
                appendLine(fullTitle.take(50))
                appendLine()
                appendLine("• Condition: $condLabel")
                if (item.size.isNotBlank()) appendLine("• Size: ${item.size}")
                if (item.color.isNotBlank()) appendLine("• Color: ${item.color}")
                if (item.description.isNotBlank()) {
                    appendLine()
                    appendLine(item.description)
                }
                if (item.tags.isNotEmpty()) appendLine(item.tags.joinToString(" ") { "#$it" })
                append("Asking \$${" %.2f".format(price).trim()} — local pickup preferred")
            }
        Platform.CRAIGSLIST ->
            buildString {
                appendLine(fullTitle)
                appendLine()
                if (item.description.isNotBlank()) appendLine(item.description)
                appendLine("Condition: $condLabel")
                if (item.size.isNotBlank()) appendLine("Size / Dimensions: ${item.size}")
                if (item.color.isNotBlank()) appendLine("Color: ${item.color}")
                if (item.tags.isNotEmpty()) appendLine("Keywords: ${item.tags.joinToString(", ")}")
                appendLine()
                appendLine("Asking \$${" %.2f".format(price).trim()} — cash or Venmo.")
                append("Email for info / to arrange pickup.")
            }
    }
}
