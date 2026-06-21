package com.shelfsnap.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.ui.theme.PlatformCraigslist
import com.shelfsnap.app.ui.theme.PlatformEbay
import com.shelfsnap.app.ui.theme.PlatformFbMarketplace
import com.shelfsnap.app.ui.theme.PlatformMercari
import com.shelfsnap.app.ui.theme.PlatformOfferUp

/** Brand color for a selling platform. */
fun Platform.brandColor(): Color =
    when (this) {
        Platform.EBAY -> PlatformEbay
        Platform.MERCARI -> PlatformMercari
        Platform.OFFERUP -> PlatformOfferUp
        Platform.FB_MARKETPLACE -> PlatformFbMarketplace
        Platform.CRAIGSLIST -> PlatformCraigslist
    }

/** Representative icon for a selling platform. */
fun Platform.icon(): ImageVector =
    when (this) {
        Platform.EBAY -> Icons.Default.Storefront
        Platform.MERCARI -> Icons.Default.ShoppingBag
        Platform.OFFERUP -> Icons.Default.LocalOffer
        Platform.FB_MARKETPLACE -> Icons.Default.Groups
        Platform.CRAIGSLIST -> Icons.Default.ListAlt
    }

/** A small pill showing a platform's icon + name in its brand color. */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    val color = platform.brandColor()
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                platform.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}
