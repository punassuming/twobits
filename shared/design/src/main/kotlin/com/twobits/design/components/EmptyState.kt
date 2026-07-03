package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The standard "nothing here" state: a centered icon, a title, and an optional subtitle — used
 * for both a genuinely empty list and a filtered/searched view with no matches. Was previously
 * hand-rolled with differing icon sizes and typography in each app; call this directly instead.
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    // Defaults to filling the screen; pass a different modifier (e.g. Modifier.fillMaxWidth() +
    // vertical padding) when this sits inside a LazyColumn item instead of being the whole screen.
    modifier: Modifier = Modifier.fillMaxSize(),
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconTint,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
