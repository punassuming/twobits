package com.shelfsnap.app.data.model

/**
 * Online selling platforms Shelf Snap can research prices on and cross-list to.
 *
 * [key] is the stable identifier persisted in the database and used as a map key
 * (kept lowercase to match the original design tokens). [displayName] is shown in
 * the UI. [queryHint] is appended to web searches to bias results toward that
 * platform's marketplace.
 */
enum class Platform(
    val key: String,
    val displayName: String,
    val queryHint: String
) {
    EBAY("ebay", "eBay", "ebay sold listing"),
    MERCARI("mercari", "Mercari", "mercari"),
    OFFERUP("offerup", "OfferUp", "offerup"),
    FB_MARKETPLACE("fbmarket", "FB Marketplace", "facebook marketplace"),
    CRAIGSLIST("craigslist", "Craigslist", "craigslist");

    companion object {
        /** Resolves a persisted [key] back to a [Platform], or null if unknown. */
        fun fromKey(key: String): Platform? = entries.firstOrNull { it.key == key }
    }
}
