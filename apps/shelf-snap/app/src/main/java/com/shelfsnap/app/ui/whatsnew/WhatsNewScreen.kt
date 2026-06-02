package com.shelfsnap.app.ui.whatsnew

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shelfsnap.app.BuildConfig
import com.shelfsnap.app.R

/**
 * Renders the bundled CHANGELOG.md (Settings → What's new). The changelog is copied
 * into assets at build time, so this always reflects the shipped version.
 */
@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = remember {
        val text = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        parseChangelog(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whats_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.whats_new_empty))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(sections) { section -> ChangelogSectionCard(section) }
        }
    }
}

@Composable
private fun ChangelogSectionCard(section: ChangelogSection) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            section.body.forEach { line ->
                when {
                    line.startsWith("### ") -> Text(
                        text = line.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    line.startsWith("- ") || line.startsWith("* ") -> Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = line.drop(2).trim(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A single `## [version]` block from the changelog. */
data class ChangelogSection(val title: String, val body: List<String>)

/**
 * Parses Keep-a-Changelog markdown into sections keyed by their `## ` headings.
 * Content before the first `## ` heading (the file preamble) is ignored.
 */
fun parseChangelog(markdown: String): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    var title: String? = null
    val body = mutableListOf<String>()

    fun flush() {
        title?.let { sections.add(ChangelogSection(it, body.toList())) }
        body.clear()
    }

    markdown.lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.startsWith("## ") -> {
                flush()
                // "## [1.0.0] - 2026-06-01" -> "1.0.0 - 2026-06-01"; "## [Unreleased]" -> "Unreleased"
                title = line.removePrefix("## ").replace("[", "").replace("]", "").trim()
            }

            title != null && line.isNotBlank() -> body.add(line)
        }
    }
    flush()
    return sections
}
