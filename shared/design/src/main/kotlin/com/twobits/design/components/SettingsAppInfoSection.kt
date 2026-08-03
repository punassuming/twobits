package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/**
 * The Settings-screen "About" section shared by all three apps: version line, a What's New row,
 * a Crash log row, and a Privacy policy row. Built on [AppSectionLabel]/[AppSectionCard]/
 * [SettingsRow] (which previously had zero call sites anywhere) instead of each app hand-rolling
 * its own.
 *
 * The crash log row lives here — in main Settings, next to version/privacy — rather than under
 * AI configuration: a crash isn't an AI feature's problem to surface, and burying crash retrieval
 * inside an AI-specific screen means anyone hunting for it after a crash won't think to look there.
 */
@Composable
fun SettingsAppInfoSection(
    versionLabel: String,
    onWhatsNew: () -> Unit,
    onCrashLog: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    title: String = "About",
    whatsNewSubtitle: String = "Recent changes & release notes",
    crashLogSubtitle: String = "Details of any app crashes, captured automatically",
    privacyUrl: String = "https://punassuming.github.io/twobits/privacy.html",
    privacySubtitle: String = "punassuming.github.io/twobits/privacy",
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppSectionLabel(title, Icons.Filled.Info, Modifier.padding(start = 4.dp))
        AppSectionCard(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            SettingsRow(
                title = "What's new",
                subtitle = whatsNewSubtitle,
                trailing = {
                    Icon(
                        Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onWhatsNew,
            )
            HorizontalDivider()
            SettingsRow(
                title = "Crash log",
                subtitle = crashLogSubtitle,
                trailing = {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onCrashLog,
            )
            HorizontalDivider()
            SettingsRow(
                title = "Privacy policy",
                subtitle = privacySubtitle,
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { uriHandler.openUri(privacyUrl) },
            )
        }
    }
}
