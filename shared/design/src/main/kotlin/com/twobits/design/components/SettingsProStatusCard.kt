package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The Settings-screen Pro status widget shared by all three apps — a Free-state upsell CTA or a
 * Pro-state "Active" badge with restore/manage actions. Distinct from [ProTierCard] (the
 * plan-comparison card used on the dedicated Pro screens): this is a status card with a different
 * interaction shape, not a comparison card. All copy/pricing stays caller-supplied so each app's
 * real price and tone are preserved exactly.
 */
@Composable
fun SettingsProStatusCard(
    appName: String,
    isPro: Boolean,
    upgradeLabel: String,
    upgradeDescription: String,
    activeDescription: String,
    isPurchasing: Boolean,
    purchaseError: String?,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    detailsLabelFree: String = "See plans →",
    detailsLabelPro: String = "Details →",
) {
    AppSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("$appName Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (isPro) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Text(
            text = if (isPro) activeDescription else upgradeDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isPro) {
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPurchasing,
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isPurchasing) "Processing…" else upgradeLabel)
            }
        }
        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Restore purchases")
        }
        TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
            Text(if (isPro) detailsLabelPro else detailsLabelFree)
        }
        if (purchaseError != null) {
            Text(purchaseError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError) { Text("Dismiss") }
        }
    }
}
