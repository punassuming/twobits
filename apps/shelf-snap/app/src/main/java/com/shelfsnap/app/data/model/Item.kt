package com.shelfsnap.app.data.model

/**
 * Domain model representing a donation / resale item in the inventory.
 *
 * [photoPaths] is an ordered list of absolute file paths for photos captured per item.
 * [estimatedValue] is always labeled as an estimate in the UI.
 * [isDraft] is true until the user confirms all AI-proposed fields.
 *
 * The v2 fields ([brand]…[tags]) capture the extra detail that improves price
 * research, while [marketResearch] and [listings] back the Market and List tabs.
 */
data class Item(
    val id: Long = 0L,
    val photoPaths: List<String> = emptyList(),
    val category: String = "",
    val description: String = "",
    val condition: Condition = Condition.GOOD,
    val estimatedValue: Double = 0.0,
    val confidencePercent: Int = 0,
    val isDraft: Boolean = true,
    // ── v2: extended item attributes ─────────────────────────────────────────
    val brand: String = "",
    val model: String = "",
    val size: String = "",
    val color: String = "",
    val quantity: Int = 1,
    val originalPrice: Double = 0.0,
    val tags: List<String> = emptyList(),
    // ── v2: market research + cross-listing ──────────────────────────────────
    val marketResearch: MarketResearch = MarketResearch(),
    val listings: List<PlatformListing> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // ── v3: primary photo index ───────────────────────────────────────────────
    val primaryPhotoIndex: Int = 0,
    // ── v4: item-level display title ──────────────────────────────────────────
    val title: String = "",
) {
    /** True when the item has at least one active (not sold) platform listing. */
    val hasActiveListing: Boolean
        get() = listings.any { it.status == ListingStatus.ACTIVE }

    /** True when the item has sold on at least one platform. */
    val hasSold: Boolean
        get() = listings.any { it.status == ListingStatus.SOLD }
}

/**
 * Brand+model, else category — the title fallback shared by [Item.displayTitle] and by
 * re-analysis, which needs the same fallback computed from a fresh AI result rather than the
 * (possibly stale) persisted [Item].
 */
fun displayTitleFallback(
    brand: String,
    model: String,
    category: String,
): String = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { category }

/**
 * Canonical display title: the persisted [Item.title] if set, else brand+model, else category.
 * Used wherever a human-facing item name is needed (inventory cards, listing summaries, market
 * research queries) so the fallback logic isn't duplicated at each call site, and so pre-v4 items
 * without a persisted title still show something sensible.
 */
val Item.displayTitle: String
    get() = title.ifBlank { displayTitleFallback(brand, model, category) }
