package dev.scrybe.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.twobits.common.ReleaseNotesParser
import com.twobits.design.components.WhatsNewScreenLayout
import com.twobits.design.components.toWhatsNewRelease

@Composable
fun ScrybeWhatsNewScreen(
    onBack: () -> Unit,
    onNavigate: (target: String) -> Unit,
) {
    val context = LocalContext.current
    val releases = remember {
        val text = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        ReleaseNotesParser.parseReleaseHistory(text)
            .mapIndexed { i, notes -> notes.toWhatsNewRelease(isLatest = i == 0) }
            .filter { it.categories.isNotEmpty() }
    }
    WhatsNewScreenLayout(
        title = "Version history",
        releases = releases,
        onBack = onBack,
        onNavigate = onNavigate,
    )
}
