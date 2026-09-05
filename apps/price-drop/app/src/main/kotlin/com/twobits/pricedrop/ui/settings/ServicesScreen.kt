package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AiSectionCard
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val providerStates by viewModel.providerStates.collectAsState()
    val featureStates by viewModel.featureStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Services") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AiSectionCard(icon = Icons.Default.Public, title = "Search, shopping & page reading") {
                    Text(
                        text =
                            "Search, product-matching, and page-reading providers used for keyword search, " +
                                "URL-paste, and Amazon-specific enrichment. OpenAI is managed separately in " +
                                "AI configuration → Credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    PriceDropProvider.entries.filterNot { it.isAiModelProvider() }.forEach { provider ->
                        val state = providerStates[provider] ?: ProviderState(ProviderMode.PRO, "")
                        ProviderCredentialItem(
                            provider = provider,
                            state = state,
                            viewModel = viewModel,
                        )

                        val servedFeatures = AiFeature.entries.filter { provider in it.providers }
                        if (servedFeatures.isNotEmpty()) {
                            Column(modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)) {
                                servedFeatures.forEach { feature ->
                                    val enabled = featureStates[feature]?.enabledProviders?.contains(provider.key) ?: true
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Enabled for ${feature.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { viewModel.toggleFeatureProvider(feature, provider.key) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Only worth showing once there's an actual choice — with no validated
                    // Firecrawl key, Jina is the sole reader anyway (today's behavior, unchanged).
                    if (providerStates[PriceDropProvider.FIRECRAWL]?.isKeyValid == true) {
                        val pageReaderProvider by viewModel.pageReaderProvider.collectAsState()
                        PageReaderPicker(
                            selected = pageReaderProvider,
                            onSelect = viewModel::setPageReaderProvider,
                        )
                    }
                }
            }
        }
    }
}

/** Which backend reads a pasted product URL's page content — separate from the credential rows
 *  above, since reading a page and holding its key are different concerns. */
@Composable
private fun PageReaderPicker(
    selected: PriceDropProvider,
    onSelect: (PriceDropProvider) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("Page reader", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(PriceDropProvider.WEB_SEARCH, PriceDropProvider.FIRECRAWL).forEach { provider ->
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
