package com.shelfsnap.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.remote.search.ReaderProvider
import com.twobits.design.components.AiSectionCard
import com.twobits.design.components.CollapsibleProviderRow
import com.twobits.design.components.CredentialRequirement

/**
 * Web-search services market research optionally uses — its own Settings menu item (reached
 * from the row right below "AI configuration"), not a card nested inside AI Configuration:
 * these aren't AI providers themselves, just supporting infrastructure one AI feature can
 * enable, so they don't belong buried in a screen scoped to configuring AI providers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            AiSectionCard(icon = Icons.Default.Public, title = "Web search") {
                Text(
                    "SearchAPI.io and Serper.dev both return real marketplace listings and honor site: " +
                        "filters (Serper is the cheaper of the two); Jina AI opens those pages to read prices " +
                        "and is also a search fallback; Brave adds a second index. Only used by market research.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CollapsibleProviderRow(
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    title = "SearchAPI.io",
                    summary = "Recommended — best marketplace search evidence",
                    description =
                        "Recommended for market research — the only engine here that honors site: filters, " +
                            "so eBay/Mercari/OfferUp \"sold\" queries return real marketplace listings instead " +
                            "of generic web results. Without it (or Serper), research still runs on the other " +
                            "providers or the model's own knowledge, just with weaker marketplace evidence.",
                    maskedKey = maskKey(uiState.savedSearchapiApiKey),
                    isKeyValid = uiState.searchapiTestResult,
                    isValidating = uiState.isSearchapiTesting,
                    validationMessage = uiState.searchapiTestMessage,
                    apiKey = uiState.editSearchapiApiKey,
                    onApiKeyChange = viewModel::onSearchapiApiKeyChange,
                    onSave = viewModel::saveSearchapiKey,
                    onTest = viewModel::testSearchapiKey,
                    onClear = viewModel::clearSearchapiKey,
                    signupUrl = "https://www.searchapi.io",
                    requirement = CredentialRequirement.RECOMMENDED,
                )

                CollapsibleProviderRow(
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    title = "Serper.dev",
                    summary = "Recommended — cheaper alternative to SearchAPI.io",
                    description =
                        "Recommended — a materially cheaper alternative to SearchAPI.io that also honors " +
                            "site: filters for real marketplace listings. Has no dedicated eBay engine, so " +
                            "eBay-targeted results are plain-Google quality rather than structured sold listings.",
                    maskedKey = maskKey(uiState.savedSerperApiKey),
                    isKeyValid = uiState.serperTestResult,
                    isValidating = uiState.isSerperTesting,
                    validationMessage = uiState.serperTestMessage,
                    apiKey = uiState.editSerperApiKey,
                    onApiKeyChange = viewModel::onSerperApiKeyChange,
                    onSave = viewModel::saveSerperKey,
                    onTest = viewModel::testSerperKey,
                    onClear = viewModel::clearSerperKey,
                    signupUrl = "https://serper.dev",
                    requirement = CredentialRequirement.RECOMMENDED,
                )

                CollapsibleProviderRow(
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    title = "Jina AI",
                    summary = "Recommended — page reading + search fallback",
                    description =
                        "Recommended — this key does two things: it's a search fallback when SearchAPI.io/" +
                            "Serper aren't configured, AND it's the only way any search result's full page gets " +
                            "read (price, condition, sold status), even when a different engine found the result. " +
                            "Adding this key improves research quality no matter which search provider is primary.",
                    maskedKey = maskKey(uiState.savedJinaApiKey),
                    isKeyValid = uiState.jinaTestResult,
                    isValidating = uiState.isJinaTesting,
                    validationMessage = uiState.jinaTestMessage,
                    apiKey = uiState.editJinaApiKey,
                    onApiKeyChange = viewModel::onJinaApiKeyChange,
                    onSave = viewModel::saveJinaKey,
                    onTest = viewModel::testJinaKey,
                    onClear = viewModel::clearJinaKey,
                    signupUrl = "https://jina.ai",
                    requirement = CredentialRequirement.RECOMMENDED,
                )

                CollapsibleProviderRow(
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    title = "Firecrawl",
                    summary = "Optional — alternative page reader",
                    description =
                        "Optional alternative to Jina AI for opening a listing's full page. Renders every " +
                            "page through a real headless browser, which may fare better on JS-heavy or " +
                            "anti-bot listings than Jina's default engine, but is slower and costs more per read.",
                    maskedKey = maskKey(uiState.savedFirecrawlApiKey),
                    isKeyValid = uiState.firecrawlTestResult,
                    isValidating = uiState.isFirecrawlTesting,
                    validationMessage = uiState.firecrawlTestMessage,
                    apiKey = uiState.editFirecrawlApiKey,
                    onApiKeyChange = viewModel::onFirecrawlApiKeyChange,
                    onSave = viewModel::saveFirecrawlKey,
                    onTest = viewModel::testFirecrawlKey,
                    onClear = viewModel::clearFirecrawlKey,
                    signupUrl = "https://firecrawl.dev",
                    requirement = CredentialRequirement.OPTIONAL,
                )

                CollapsibleProviderRow(
                    icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    title = "Brave Search",
                    description = "Optional — a second search index with no unique capability. Safe to skip.",
                    maskedKey = maskKey(uiState.savedBraveApiKey),
                    isKeyValid = uiState.braveTestResult,
                    isValidating = uiState.isBraveTesting,
                    validationMessage = uiState.braveTestMessage,
                    apiKey = uiState.editBraveApiKey,
                    onApiKeyChange = viewModel::onBraveApiKeyChange,
                    onSave = viewModel::saveBraveKey,
                    onTest = viewModel::testBraveKey,
                    onClear = viewModel::clearBraveKey,
                    signupUrl = "https://brave.com/search/api/",
                    requirement = CredentialRequirement.OPTIONAL,
                )

                if (uiState.savedJinaApiKey.isNotBlank() || uiState.savedFirecrawlApiKey.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ReaderProviderPicker(
                        selected = uiState.readerProvider,
                        onSelect = viewModel::onReaderProviderChange,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Enabled for market research",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                WebSearchToggleRow(
                    title = "SearchAPI.io",
                    enabled = uiState.searchapiSearchEnabled,
                    hasKey = uiState.savedSearchapiApiKey.isNotBlank(),
                    onEnabledChange = viewModel::onSearchapiSearchEnabledChange,
                )
                WebSearchToggleRow(
                    title = "Serper.dev",
                    enabled = uiState.serperSearchEnabled,
                    hasKey = uiState.savedSerperApiKey.isNotBlank(),
                    onEnabledChange = viewModel::onSerperSearchEnabledChange,
                )
                WebSearchToggleRow(
                    title = stringResource(R.string.jina_api_key_label),
                    enabled = uiState.jinaSearchEnabled,
                    hasKey = uiState.savedJinaApiKey.isNotBlank(),
                    onEnabledChange = viewModel::onJinaSearchEnabledChange,
                )
                WebSearchToggleRow(
                    title = stringResource(R.string.brave_api_key_label),
                    enabled = uiState.braveSearchEnabled,
                    hasKey = uiState.savedBraveApiKey.isNotBlank(),
                    onEnabledChange = viewModel::onBraveSearchEnabledChange,
                )
            }
        }
    }
}

/** Which backend opens listing pages — separate from the search toggles above, since reading a page and finding it are different jobs. */
@Composable
private fun ReaderProviderPicker(
    selected: ReaderProvider,
    onSelect: (ReaderProvider) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Page reader", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReaderProvider.entries.forEach { provider ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp),
                ) {
                    RadioButton(selected = selected == provider, onClick = { onSelect(provider) })
                    Text(provider.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun WebSearchToggleRow(
    title: String,
    enabled: Boolean,
    hasKey: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled && !hasKey) {
            Text(
                text = "On, but no key saved above — this provider won't be used until you add one.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun maskKey(key: String): String? =
    when {
        key.length > 8 -> "${key.take(4)}${"•".repeat(7)}${key.takeLast(4)}"
        key.isNotBlank() -> "••••"
        else -> null
    }
