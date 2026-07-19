package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The standard "nothing here" state: a centered icon, a title, and an optional subtitle — used
 * for both a genuinely empty list and a filtered/searched view with no matches. Was previously
 * hand-rolled with differing icon sizes and typography in each app; call this directly instead.
 * Optional primary/secondary actions render as a filled button and a text button below the
 * subtitle, for empty states whose fix is one obvious next step.
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
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
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
                    textAlign = TextAlign.Center,
                )
            }
            primaryActionLabel?.let {
                Button(onClick = onPrimaryAction, modifier = Modifier.padding(top = 8.dp)) {
                    Text(it)
                }
            }
            secondaryActionLabel?.let {
                TextButton(onClick = onSecondaryAction) {
                    Text(it)
                }
            }
        }
    }
}
