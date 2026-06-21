package com.twobits.pricedrop.ui.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.twobits.common.ReleaseNotesParser
import com.twobits.design.components.WhatsNewScreenLayout
import com.twobits.design.components.toWhatsNewRelease

@Composable
fun WhatsNewScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val releases =
        remember {
            val text =
                try {
                    context.assets
                        .open("CHANGELOG.md")
                        .bufferedReader()
                        .use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
            ReleaseNotesParser
                .parseReleaseHistory(text)
                .mapIndexed { i, notes -> notes.toWhatsNewRelease(isLatest = i == 0) }
                .filter { it.categories.isNotEmpty() }
        }
    WhatsNewScreenLayout(title = "What's New", releases = releases, onBack = onNavigateBack)
}
