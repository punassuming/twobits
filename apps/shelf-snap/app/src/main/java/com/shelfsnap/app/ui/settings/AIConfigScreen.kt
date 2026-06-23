package com.shelfsnap.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.local.LocalModelState
import com.shelfsnap.app.data.model.LocalGemmaModel
import com.shelfsnap.app.data.model.LocalMoondreamModel
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.AiCredentialsDock
import com.twobits.design.components.AiNoKeyWarning
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionHeader
import com.twobits.design.components.AiSourceSegment
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList
import com.twobits.design.components.ProviderCredentialCard

private fun LocalModelState.toStatus(): LocalModelStatus =
    when (this) {
        is LocalModelState.NotAvailable -> LocalModelStatus.NotAvailable
        is LocalModelState.Importing -> LocalModelStatus.InProgress(progressPercent)
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

    val pendingMoondreamImport = remember { mutableStateOf<LocalMoondreamModel?>(null) }
    val moondreamPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.importMoondream(it, pendingMoondreamImport.value ?: return@let) }
            pendingMoondreamImport.value = null
        }

    val pendingGemmaImport = remember { mutableStateOf<LocalGemmaModel?>(null) }
    val gemmaPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.importGemma(it, pendingGemmaImport.value ?: return@let) }
            pendingGemmaImport.value = null
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AiCredentialsDock(
                proLabel = "Shelf Snap Pro",
                proPrice = "\$1.99/mo",
                hasPro = hasPro,
                apiKey = uiState.editApiKey,
                isValidating = uiState.isVerifyingKey,
                validationMessage =
                    when {
                        uiState.isKeyVerified == true -> "Connected to OpenAI"
                        uiState.isKeyVerified == false -> uiState.keyVerifyError ?: "Connection failed"
                        uiState.isKeyInvalid -> "Invalid API key format"
                        else -> null
                    },
                isKeyValid = uiState.isKeyVerified,
                onApiKeyChange = viewModel::onApiKeyChange,
                onSave = viewModel::save,
                onClear = viewModel::clearApiKey,
                onTest = viewModel::testApiKey,
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
                    else ->
                        LocalModelPanel(
                            sectionLabel = "Moondream — vision model",
                            sectionSubtitle = "Download from HuggingFace, then import the .gguf file.",
                            models = LocalMoondreamModel.entries.toList(),
                            status = { (uiState.moondreamStates[it] ?: LocalModelState.NotAvailable).toStatus() },
                            selected = uiState.selectedMoondream,
                            onSelect = { viewModel.selectMoondream(it) },
                            onPrimaryAction = {
                                pendingMoondreamImport.value = it
                                moondreamPicker.launch("*/*")
                            },
                            primaryActionLabel = "Import",
                            primaryActionIcon = Icons.Default.FolderOpen,
                            onDelete = { viewModel.deleteMoondream(it) },
                            name = { it.displayName },
                            sizeLabel = { it.sizeLabel },
                            description = { it.description },
                            progressLabel = "Importing",
                            huggingFaceUrl = { it.huggingFacePageUrl },
                        )
                }
            }

            AiSectionCard(icon = Icons.Default.AutoAwesome, title = "Pricing & descriptions") {
                AiSourceSegment(
                    selected = uiState.textSource,
                    hasPro = hasPro,
                    onChange = viewModel::onTextSourceChange,
                )
                when (uiState.textSource) {
                    "pro" ->
                        AiProManagedCard(
                            description = "Managed listing & pricing API active.",
                        )
                    "byok" -> {
                        if (uiState.editApiKey.isBlank()) {
                            AiNoKeyWarning()
                        } else {
                            Text(
                                "Pricing & description model",
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
                            sectionSubtitle = "Download from HuggingFace, then import the .gguf file.",
                            models = LocalGemmaModel.entries.toList(),
                            status = { (uiState.gemmaStates[it] ?: LocalModelState.NotAvailable).toStatus() },
                            selected = uiState.selectedGemma,
                            onSelect = { viewModel.selectGemma(it) },
                            onPrimaryAction = {
                                pendingGemmaImport.value = it
                                gemmaPicker.launch("*/*")
                            },
                            primaryActionLabel = "Import",
                            primaryActionIcon = Icons.Default.FolderOpen,
                            onDelete = { viewModel.deleteGemma(it) },
                            name = { it.displayName },
                            sizeLabel = { it.sizeLabel },
                            description = { it.description },
                            progressLabel = "Importing",
                            huggingFaceUrl = { it.huggingFacePageUrl },
                        )
                }
            }

            WebSearchSection(uiState = uiState, viewModel = viewModel, hasPro = hasPro)

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

@Composable
private fun WebSearchSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    hasPro: Boolean,
) {
    AiSectionHeader(icon = Icons.Default.Search, title = "Web search for pricing")
    if (hasPro) {
        AiProManagedCard(
            description = "Web search managed via api.twobits.app — Jina AI (primary) with Brave Search as supplement. No keys required.",
        )
        return
    }
    Text(
        text =
            "Enable one or both. Jina AI both searches and opens listing pages to read real prices; " +
                "Brave Search adds a second independent index for broader coverage. With both on, " +
                "results are merged and the top pages are read via Jina.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    WebSearchProviderBlock(
        title = stringResource(R.string.jina_api_key_label),
        enabled = uiState.jinaSearchEnabled,
        onEnabledChange = viewModel::onJinaSearchEnabledChange,
        apiKey = uiState.editJinaApiKey,
        isValidating = uiState.isJinaTesting,
        validationMessage = uiState.jinaTestMessage,
        isKeyValid = uiState.jinaTestResult,
        onApiKeyChange = viewModel::onJinaApiKeyChange,
        onSave = viewModel::saveJinaKey,
        onClear = viewModel::clearJinaKey,
        onTest = viewModel::testJinaKey,
        keyHint = stringResource(R.string.jina_api_key_hint),
        setupContent =
            if (uiState.savedJinaApiKey.isBlank()) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.jina_setup_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        listOf(
                            stringResource(R.string.jina_setup_step_1),
                            stringResource(R.string.jina_setup_step_2),
                            stringResource(R.string.jina_setup_step_3),
                        ).forEachIndexed { i, step ->
                            Text(
                                text = "${i + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                null
            },
    )
    WebSearchProviderBlock(
        title = stringResource(R.string.brave_api_key_label),
        enabled = uiState.braveSearchEnabled,
        onEnabledChange = viewModel::onBraveSearchEnabledChange,
        apiKey = uiState.editBraveApiKey,
        isValidating = uiState.isBraveTesting,
        validationMessage = uiState.braveTestMessage,
        isKeyValid = uiState.braveTestResult,
        onApiKeyChange = viewModel::onBraveApiKeyChange,
        onSave = viewModel::saveBraveKey,
        onClear = viewModel::clearBraveKey,
        onTest = viewModel::testBraveKey,
        keyHint = stringResource(R.string.brave_api_key_hint),
    )
}

@Composable
private fun WebSearchProviderBlock(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    apiKey: String,
    isValidating: Boolean,
    validationMessage: String?,
    isKeyValid: Boolean?,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
    keyHint: String,
    setupContent: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            setupContent?.invoke()
            ProviderCredentialCard(
                title = title,
                selectedMode = "key",
                keyMode = "key",
                apiKey = apiKey,
                isValidating = isValidating,
                validationMessage = validationMessage,
                isKeyValid = isKeyValid,
                onApiKeyChange = onApiKeyChange,
                onSave = onSave,
                onClear = onClear,
                onTest = onTest,
                keyHint = keyHint,
            )
        }
    }
}

@Composable
private fun AiSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AiSectionHeader(title = title, icon = icon)
        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
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
