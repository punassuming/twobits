package com.shelfsnap.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shelfsnap.app.data.model.Item
import java.io.File

/**
 * Maps an item's free-text [category] to a representative Material icon, mirroring the
 * design's `ItemThumb` icon set. Used as the fallback when an item has no photo so the
 * inventory still reads visually (a coat looks like a coat, a mixer like an appliance).
 */
fun categoryIcon(category: String): ImageVector {
    val c = category.lowercase()

    fun match(vararg keys: String) = keys.any { c.contains(it) }
    return when {
        match("coat", "jacket", "parka", "cloth", "apparel", "shirt", "dress", "outerwear", "pant") -> Icons.Default.Checkroom
        match("mixer", "blender", "appliance", "kitchen", "cookware") -> Icons.Default.Blender
        match("game", "puzzle", "toy", "board") -> Icons.Default.Casino
        match("shoe", "boot", "sneaker", "footwear", "heel", "sandal") -> Icons.Default.Hiking
        match("shelf", "bookcase", "furniture", "table", "chair", "desk", "cabinet", "dresser") -> Icons.Default.Weekend
        match("book", "novel", "textbook", "magazine") -> Icons.Default.MenuBook
        match("phone", "laptop", "electronic", "tv", "camera", "device", "tablet", "speaker", "headphone") -> Icons.Default.Devices
        else -> Icons.Default.Inventory2
    }
}

/**
 * The canonical item thumbnail used across Inventory, Summary, Item Detail and the
 * Camera strip. Shows the item's first [Item.photoPaths] photo when present, otherwise a
 * category-specific icon on a rounded surface tile. Optionally overlays a photo-count
 * badge when [showCount] and the item has more than one photo.
 */
@Composable
fun ItemThumb(
    item: Item,
    size: Dp,
    modifier: Modifier = Modifier,
    showCount: Boolean = false,
) {
    val corner = size * 0.22f
    val shape = RoundedCornerShape(corner)
    Box(modifier = modifier.size(size)) {
        val photo = item.photoPaths.firstOrNull()
        if (photo != null) {
            AsyncImage(
                model = File(photo),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(item.category),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.46f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showCount && item.photoPaths.size > 1) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "${item.photoPaths.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
