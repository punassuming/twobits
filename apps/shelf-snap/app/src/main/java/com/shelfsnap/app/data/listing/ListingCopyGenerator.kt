package com.shelfsnap.app.data.listing

import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform

data class ListingCopy(
    val title: String,
    val description: String,
    val condition: String,
    val shipping: String,
)

object ListingCopyGenerator {
    fun generate(
        item: Item,
        platform: Platform,
    ): ListingCopy {
        val rawTitle =
            listOfNotNull(
                item.brand.takeIf { it.isNotBlank() },
                item.model.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { item.category }
        val title = rawTitle.take(platform.titleCharLimit)
        val description = item.description.ifBlank { item.category }
        val condition = item.condition.displayLabel()
        val shipping = "I'll ship it — prepaid label"
        return ListingCopy(title, description, condition, shipping)
    }

    private fun Condition.displayLabel(): String =
        when (this) {
            Condition.EXCELLENT -> "Excellent — like new"
            Condition.GOOD -> "Good — light wear"
            Condition.FAIR -> "Fair — noticeable wear"
            Condition.POOR -> "Poor — for parts or repair"
        }
}
