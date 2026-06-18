package com.twobits.pricedrop.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<Pair<String, List<String>>>>(emptyList()) }

    LaunchedEffect(Unit) {
        sections = try {
            context.assets.open("CHANGELOG.md").bufferedReader().readText().let(::parseChangelog)
        } catch (_: Exception) {
            listOf("PriceDrop 1.0" to listOf("Initial release — watch products, track prices, get drop alerts."))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's new") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(sections) { (version, bullets) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(version, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    bullets.forEach { bullet ->
                        Text("• $bullet", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

private fun parseChangelog(text: String): List<Pair<String, List<String>>> {
    val result = mutableListOf<Pair<String, List<String>>>()
    var currentVersion = ""
    val currentBullets = mutableListOf<String>()

    text.lines().forEach { line ->
        when {
            line.startsWith("## ") -> {
                if (currentVersion.isNotBlank() && currentBullets.isNotEmpty()) {
                    result += currentVersion to currentBullets.toList()
                }
                currentVersion = line.removePrefix("## ").trim()
                currentBullets.clear()
            }
            line.startsWith("* ") || line.startsWith("- ") -> {
                currentBullets += line.drop(2).trim()
            }
        }
    }
    if (currentVersion.isNotBlank() && currentBullets.isNotEmpty()) {
        result += currentVersion to currentBullets.toList()
    }
    return result
}
