package com.twobits.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TrendingUp
import com.twobits.common.ReleaseNotes

fun ReleaseNotes.toWhatsNewRelease(isLatest: Boolean = false): WhatsNewRelease {
    val categories = if (groups.isNotEmpty()) {
        groups.map { group ->
            val icon = categoryIcon(group.title)
            WhatsNewCategory(
                id = group.title.lowercase().replace(" ", "_"),
                label = group.title,
                icon = icon,
                items = group.items.mapIndexed { idx, item ->
                    WhatsNewItem(
                        id = "${group.title}_$idx",
                        icon = icon,
                        title = item.title,
                        description = item.description.takeIf { it != item.title }.orEmpty(),
                    )
                },
            )
        }
    } else if (summaryItems.isNotEmpty()) {
        listOf(
            WhatsNewCategory(
                id = "updates",
                label = "Updates",
                icon = Icons.Filled.TrendingUp,
                items = summaryItems.mapIndexed { idx, bullet ->
                    WhatsNewItem(
                        id = "update_$idx",
                        icon = Icons.Filled.TrendingUp,
                        title = bullet,
                        description = "",
                    )
                },
            )
        )
    } else {
        emptyList()
    }
    return WhatsNewRelease(
        version = title,
        date = date.trim('(', ')'),
        isLatest = isLatest,
        categories = categories,
    )
}

private fun categoryIcon(label: String) = when {
    label.contains("feature", ignoreCase = true) -> Icons.Filled.AutoAwesome
    label.contains("fix", ignoreCase = true) -> Icons.Filled.BuildCircle
    label.contains("initial", ignoreCase = true) || label.contains("launch", ignoreCase = true) -> Icons.Filled.RocketLaunch
    else -> Icons.Filled.TrendingUp
}
