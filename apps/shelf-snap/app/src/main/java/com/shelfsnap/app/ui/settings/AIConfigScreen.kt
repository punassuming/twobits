package com.shelfsnap.app.ui.settings

import android.app.Activity
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.twobits.billing.SubscriptionTier
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.design.components.AiNoKeyWarning
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionCard
import com.twobits.design.components.AiSourceSegment
import com.twobits.design.components.CollapsibleProviderRow
import com.twobits.design.components.CredentialRequirement
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList

private fun LocalModelState.toStatus(): LocalModelStatus =
    when (this) {
        is LocalModelState.Absent -> LocalModelStatus.NotAvailable
        is LocalModelState.Acquiring -> LocalModelStatus.InProgress(progressPercent)
        is LocalModelState.Ready -> LocalModelStatus.Ready
        is LocalModelState.Error -> LocalModelStatus.Error(message)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    val hasPro = uiState.subscriptionTier is SubscriptionTier.Pro
    val snackbarHostState = remember { SnackbarHostState() }
    val searchSavedMessage = stringResource(R.string.search_settings_saved)

    LaunchedEffect(uiState.isSearchSaved) {
        if (uiState.isSearchSaved) {
            snackbarHostState.showSnackbar(searchSavedMessage)
            viewModel.onSearchSavedShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI configuration") },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CredentialsSection(
                uiState = uiState,
                viewModel = viewModel,
                hasPro = hasPro,
                onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
            )

            AiSectionCard(icon = Icons.Default.ImageSearch, title = "Vision — item identification") {
                AiSourceSegment(
                    selected = uiState.visionSource,
                    hasPro = hasPro,
                    onChange = viewModel::onVisionSourceChange,
                )
                when (uiState.visionSource) {
                    "pro" ->
                        AiProManagedCard(
                            description = "Managed vision API active — items analysed automatically.",
                        )
                    "byok" -> {
                        if (uiState.editApiKey.isBlank()) {
                            AiNoKeyWarning()
                        } else {
                            Text(
                                "Vision model",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ModelRadioList(
                                models = VisionModel.entries.toList(),
                                selected = uiState.visionModel,
                                onSelect = viewModel::onVisionModelChange,
                                name = { it.displayName },
                                subtitle = { it.supportingText },
                                costLabel = { it.costLabel },
                            )
                        }
                    }
                    else -> {
                        LocalModelPanel(
                            sectionLabel = "Gemma — on-device vision",
                            models = LocalLlmModel.entries.toList(),
                            status = { (uiState.llmStates[it] ?: LocalModelState.Absent).toStatus() },
                            selected = uiState.selectedLlm,
                            onSelect = { viewModel.selectLlmModel(it) },
                            onPrimaryAction = { viewModel.downloadLlmModel(it) },
                            primaryActionLabel = "Download",
                            primaryActionIcon = Icons.Default.CloudDownload,
                            onDelete = { viewModel.deleteLlmModel(it) },
                            name = { it.displayName },
                            sizeLabel = { it.sizeLabel },
                            description = { it.description },
                            progressLabel = "Downloading",
                            huggingFaceUrl = { it.huggingFacePageUrl },
                        )
                        Text(
                            "Experimental — on-device vision reuses the same Gemma model as local " +
                                "listing generation; accuracy is unverified on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AiSectionCard(icon = Icons.Default.AutoAwesome, title = "Listing generation") {
                AiSourceSegment(
                    selected = uiState.listingSource,
                    hasPro = hasPro,
                    onChange = viewModel::onListingSourceChange,
                )
                when (uiState.listingSource) {
                    "pro" ->
                        AiProManagedCard(
                            description = "Managed listing API active — refined listing copy generated automatically.",
                        )
                    "byok" -> {
                        if (uiState.editApiKey.isBlank()) {
                            AiNoKeyWarning()
                        } else {
                            Text(
                                "Listing model",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ModelRadioList(
                                models = ReasoningModel.entries.toList(),
                                selected = uiState.reasoningModel,
                                onSelect = viewModel::onReasoningModelChange,
                                name = { it.displayName },
                                subtitle = { it.supportingText },
                                costLabel = { it.costLabel },
                            )
                        }
                    }
                    else ->
                        LocalModelPanel(
                            sectionLabel = "Gemma — on-device LLM",
                            models = LocalLlmModel.entries.toList(),
                            status = { (uiState.llmStates[it] ?: LocalModelState.Absent).toStatus() },
                            selected = uiState.selectedLlm,
                            onSelect = { viewModel.selectLlmModel(it) },
                            onPrimaryAction = { viewModel.downloadLlmModel(it) },
                            primaryActionLabel = "Download",
                            primaryActionIcon = Icons.Default.CloudDownload,
                            onDelete = { viewModel.deleteLlmModel(it) },
                            name = { it.displayName },
                            sizeLabel = { it.sizeLabel },
                            description = { it.description },
                            progressLabel = "Downloading",
                            huggingFaceUrl = { it.huggingFacePageUrl },
                        )
                }
            }

            AiSectionCard(icon = Icons.Default.Insights, title = "Market research") {
                AiSourceSegment(
                    selected = uiState.textSource,
                    hasPro = hasPro,
                    hasLocal = false,
                    onChange = viewModel::onTextSourceChange,
                )
                when (uiState.textSource) {
                    "pro" ->
                        AiProManagedCard(
                            description = "Managed pricing & web search API active — no keys required.",
                        )
                    "local" ->
                        Text(
                            "Local web search isn't available — choose Pro or BYOK for market research.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else -> {
                        if (uiState.editApiKey.isBlank()) {
                            AiNoKeyWarning()
                        } else {
                            Text(
                                "Market research model",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ModelRadioList(
                                models = ReasoningModel.entries.toList(),
                                selected = uiState.reasoningModel,
                                onSelect = viewModel::onReasoningModelChange,
                                name = { it.displayName },
                                subtitle = { it.supportingText },
                                costLabel = { it.costLabel },
                            )
                        }
                        HorizontalDivider()
                        Text(
                            "Web search providers, their keys, and which are enabled now live in " +
                                "the Services section below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AiSectionCard(icon = Icons.Default.Insights, title = "Analysis") {
                AiToggleRow(
                    title = "AI condition detection",
                    subtitle = "Automatically assess item condition from photos",
                    checked = uiState.aiConditionDetection,
                    onCheckedChange = viewModel::onAiConditionDetectionChange,
                )
                HorizontalDivider()
                AiToggleRow(
                    title = "Auto price estimate",
                    subtitle = "Generate a resale price estimate when analyzing items",
                    checked = uiState.autoPriceEstimate,
                    onCheckedChange = viewModel::onAutoPriceEstimateChange,
                )
                HorizontalDivider()
                AiToggleRow(
                    title = "Multi-photo analysis",
                    subtitle = "Use all captured photos for richer item identification",
                    checked = uiState.multiPhotoAnalysis,
                    onCheckedChange = viewModel::onMultiPhotoAnalysisChange,
                )
            }

            ServicesSection(uiState = uiState, viewModel = viewModel)
        }
    }
}

/**
 * Web-search services market research optionally uses — kept separate from AI Configuration's
 * own provider cards above since these aren't AI providers themselves (no vision/listing/
 * research model runs on them directly), just supporting infrastructure one AI feature can
 * enable. Previously split across "Credentials" (the API keys) and "Market research" (the
 * enable/disable toggles); both live here together now.
 */
@Composable
private fun ServicesSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    AiSectionCard(icon = Icons.Default.Public, title = "Services") {
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

/** All BYOK keys for Shelf Snap, grouped at the top like PriceDrop's credentials section. */
@Composable
private fun CredentialsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    hasPro: Boolean,
    onUpgrade: () -> Unit,
) {
    AiSectionCard(icon = Icons.Filled.VpnKey, title = "Credentials") {
        if (hasPro) {
            Text(
                text = "Shelf Snap Pro active — managed keys are used automatically. The keys below are optional (BYOK).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Upgrade to Shelf Snap Pro for managed keys — no setup needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onUpgrade) { Text("Upgrade") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.VpnKey,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = "BYOK · YOUR KEYS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CollapsibleProviderRow(
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
            title = "OpenAI",
            summary = "Required for vision, listing, and market research",
            description =
                "Required for every AI feature — vision item ID, listing generation, and market " +
                    "research synthesis all fail without it. The web search services (Services " +
                    "section below) only affect how well-grounded market research is; they can't " +
                    "substitute for this key.",
            maskedKey = maskKey(uiState.savedApiKey),
            isKeyValid = uiState.isKeyVerified,
            isValidating = uiState.isVerifyingKey,
            validationMessage =
                when {
                    uiState.isVerifyingKey -> "Checking connection…"
                    uiState.isKeyVerified == true -> "Connected to OpenAI"
                    uiState.isKeyVerified == false -> uiState.keyVerifyError ?: "Connection failed"
                    uiState.isKeyInvalid -> "Invalid API key format"
                    else -> null
                },
            apiKey = uiState.editApiKey,
            onApiKeyChange = viewModel::onApiKeyChange,
            onSave = viewModel::save,
            onTest = viewModel::testApiKey,
            onClear = viewModel::clearApiKey,
            signupUrl = "https://platform.openai.com/api-keys",
            requirement = CredentialRequirement.REQUIRED,
        )
    }
}

private fun maskKey(key: String): String? =
    when {
        key.length > 8 -> "${key.take(4)}${"•".repeat(7)}${key.takeLast(4)}"
        key.isNotBlank() -> "••••"
        else -> null
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

@Composable
private fun AiToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
