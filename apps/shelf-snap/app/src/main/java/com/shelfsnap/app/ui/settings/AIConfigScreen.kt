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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.twobits.design.components.LocalModelPicker
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
    var selectedTab by remember { mutableStateOf(0) }

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
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Configuration") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Models") })
            }
            if (selectedTab == 1) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    LocalModelPanel(
                        sectionLabel = "On-device models",
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
                return@Scaffold
            }
            Column(
                modifier =
                    Modifier
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
                            LocalModelPicker(
                                models = LocalLlmModel.entries.filter { it.visionCapable },
                                status = { (uiState.llmStates[it] ?: LocalModelState.Absent).toStatus() },
                                selected = uiState.selectedLlm,
                                onSelect = { viewModel.selectLlmModel(it) },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                onManageModels = { selectedTab = 1 },
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
                            LocalModelPicker(
                                models = LocalLlmModel.entries.toList(),
                                status = { (uiState.llmStates[it] ?: LocalModelState.Absent).toStatus() },
                                selected = uiState.selectedLlm,
                                onSelect = { viewModel.selectLlmModel(it) },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                onManageModels = { selectedTab = 1 },
                            )
                    }
                }

                AiSectionCard(icon = Icons.Default.Insights, title = "Market research") {
                    AiSourceSegment(
                        selected = uiState.textSource,
                        hasPro = hasPro,
                        onChange = viewModel::onTextSourceChange,
                    )
                    when (uiState.textSource) {
                        "pro" ->
                            AiProManagedCard(
                                description = "Managed pricing & web search API active — no keys required.",
                            )
                        "local" -> {
                            LocalModelPicker(
                                models = LocalLlmModel.entries.toList(),
                                status = { (uiState.llmStates[it] ?: LocalModelState.Absent).toStatus() },
                                selected = uiState.selectedLlm,
                                onSelect = { viewModel.selectLlmModel(it) },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                onManageModels = { selectedTab = 1 },
                            )
                            HorizontalDivider()
                            Text(
                                "Web search still runs through your configured providers (Settings → " +
                                    "Services) — the local model only writes up the price estimate " +
                                    "from those results; it can't search the web itself.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                    "Settings → Services.",
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
            }
        }
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
                    "research synthesis all fail without it. The web search services (Settings → " +
                    "Services) only affect how well-grounded market research is; they can't " +
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
