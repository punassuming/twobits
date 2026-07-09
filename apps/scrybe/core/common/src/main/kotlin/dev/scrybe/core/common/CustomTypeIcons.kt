package dev.scrybe.core.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Selectable icon set for user-created recording types, persisted by name
 * ([dev.scrybe.core.database.CustomRecordingTypeEntity.iconName]). [LABEL] is both the first
 * choice and the fallback for null/unknown names, so pre-icon types keep today's look.
 */
enum class CustomTypeIcon(
    val vector: ImageVector,
) {
    LABEL(Icons.Filled.Label),
    BOLT(Icons.Filled.Bolt),
    SCHOOL(Icons.Filled.School),
    WORK(Icons.Filled.Work),
    PSYCHOLOGY(Icons.Filled.Psychology),
    FLAG(Icons.Filled.Flag),
    HEADPHONES(Icons.Filled.Headphones),
    BRUSH(Icons.Filled.Brush),
    CAMPAIGN(Icons.Filled.Campaign),
    SCIENCE(Icons.Filled.Science),
    FAVORITE(Icons.Filled.FavoriteBorder),
    FITNESS(Icons.Filled.FitnessCenter),
    ;

    companion object {
        fun fromName(name: String?): CustomTypeIcon = entries.firstOrNull { it.name == name } ?: LABEL
    }
}

fun customTypeIcon(iconName: String?): ImageVector = CustomTypeIcon.fromName(iconName).vector
