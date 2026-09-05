package com.twobits.pricedrop.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.design.components.AI_LOCAL_COLOR
import com.twobits.design.components.AiNoKeyWarning
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionCard
import com.twobits.design.components.AppSectionLabel
import com.twobits.design.components.CallBudgetCard
import com.twobits.design.components.CallBudgetEntry
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelPicker
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList
import com.twobits.design.components.ModelStorageSection
import com.twobits.pricedrop.data.pro.PriceDropPlan
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.AiModelOption
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode

// PriceDrop accent colors (mirror the design tokens; Pro amber + BYOK coral).
private val PD_PRO_COLOR = Color(0xFFFFD580)
private val PD_BYOK_COLOR = Color(0xFFFF8066)

private fun LocalModelState.toStatus(): LocalModelStatus =
    when (this) {
        is LocalModelState.Absent -> LocalModelStatus.NotAvailable
        is LocalModelState.Acquiring -> LocalModelStatus.InProgress(progressPercent)
        is LocalModelState.Ready -> LocalModelStatus.Ready
        is LocalModelState.Error -> LocalModelStatus.Error(message)
    }

private fun AiFeature.icon(): ImageVector =
    when (this) {
        AiFeature.SEARCH -> Icons.Filled.ManageSearch
        AiFeature.PRICE_CHECK -> Icons.Filled.PriceCheck
        AiFeature.COUPON -> Icons.Filled.ConfirmationNumber
        AiFeature.DROPS -> Icons.Filled.TrendingDown
        AiFeature.ASK -> Icons.Filled.AutoAwesome
    }

private fun AiFeature.budgetColor(scheme: androidx.compose.material3.ColorScheme): Color =
    when (this) {
        AiFeature.SEARCH -> scheme.primary
        AiFeature.PRICE_CHECK -> scheme.secondary
        AiFeature.COUPON -> scheme.tertiary
        AiFeature.DROPS -> PD_PRO_COLOR
        AiFeature.ASK -> Color(0xFFC6A0F6)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val providerStates by viewModel.providerStates.collectAsState()
    val featureStates by viewModel.featureStates.collectAsState()
    val activity = LocalContext.current as? Activity
    val hasPro = uiState.hasPro

    var selectedFeature by remember { mutableStateOf<AiFeature?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var importTarget by remember { mutableStateOf<LocalLlmModel?>(null) }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val model = importTarget
            importTarget = null
            if (uri != null && model != null) viewModel.importLlmModel(model, uri)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedFeature?.label ?: "AI configuration") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedFeature != null) {
                                selectedFeature = null
                            } else {
                                onNavigateBack()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val feature = selectedFeature
        if (feature == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Configuration") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Models") })
                }
                if (selectedTab == 1) {
                    val llmStates by viewModel.llmStates.collectAsState()
                    val selectedLlm by viewModel.selectedLlm.collectAsState()
                    val orphanedFileDetails by viewModel.orphanedFileDetails.collectAsState()
                    val installedFileDetails by viewModel.installedFileDetails.collectAsState()
                    LaunchedEffect(Unit) { viewModel.refreshModelStorage() }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    ) {
                        item {
                            LocalModelPanel(
                                sectionLabel = "On-device models",
                                models = LocalLlmModel.entries.toList(),
                                status = { (llmStates[it] ?: LocalModelState.Absent).toStatus() },
                                selected = selectedLlm,
                                onSelect = { viewModel.selectLlmModel(it) },
                                onPrimaryAction = { viewModel.downloadLlmModel(it) },
                                primaryActionLabel = "Download",
                                primaryActionIcon = Icons.Default.CloudDownload,
                                onDelete = { viewModel.deleteLlmModel(it) },
                                onImport = { model ->
                                    importTarget = model
                                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                description = { it.description },
                                progressLabel = "Downloading",
                                huggingFaceUrl = { it.huggingFacePageUrl },
                            )
                        }
                        item {
                            ModelStorageSection(
                                storageDirPath = viewModel.storageDirPath,
                                installed = installedFileDetails,
                                orphaned = orphanedFileDetails,
                                onClearOrphaned = { viewModel.clearOrphanedStorage() },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                } else {
                    FeatureListContent(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        hasPro = hasPro,
                        providerStates = providerStates,
                        featureStates = featureStates,
                        viewModel = viewModel,
                        onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                        onSelectFeature = { selectedFeature = it },
                    )
                }
            }
        } else {
            FeatureDetailContent(
                modifier = Modifier.padding(padding),
                feature = feature,
                hasPro = hasPro,
                featureState = featureStates[feature],
                providerStates = providerStates,
                viewModel = viewModel,
                onManageModels = {
                    selectedFeature = null
                    selectedTab = 1
                },
            )
        }
    }
}

@Composable
private fun FeatureListContent(
    modifier: Modifier,
    hasPro: Boolean,
    providerStates: Map<PriceDropProvider, ProviderState>,
    featureStates: Map<AiFeature, FeatureState>,
    viewModel: SettingsViewModel,
    onUpgrade: () -> Unit,
    onSelectFeature: (AiFeature) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AiSectionCard(icon = Icons.Filled.VpnKey, title = "Credentials") {
                if (hasPro) {
                    AiProManagedCard(
                        description = "PriceDrop Pro is active. Shopping, coupons, and AI route through the managed proxy — no key setup needed.",
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Upgrade to PriceDrop Pro for managed connectors — no keys needed.",
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
                PriceDropProvider.entries.filter { it.isAiModelProvider() }.forEach { provider ->
                    val state = providerStates[provider] ?: ProviderState(ProviderMode.PRO, "")
                    ProviderCredentialItem(
                        provider = provider,
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }
        }

        item {
            val scheme = MaterialTheme.colorScheme
            CallBudgetCard(
                entries = AiFeature.entries.map { f -> CallBudgetEntry(f.label, f.callWeight, f.budgetColor(scheme)) },
                footnote = "Estimate per user-triggered event. Background checks (price polling) cost 1–2 calls per item.",
            )
        }

        item {
            AppSectionLabel("Features", modifier = Modifier.padding(start = 4.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column {
                    AiFeature.entries.forEach { feature ->
                        val fState = featureStates[feature]
                        val source = fState?.source ?: ProviderMode.BYOK
                        val modelName =
                            feature.models
                                .firstOrNull { it.id == fState?.modelId }
                                ?.name
                        FeatureRow(
                            feature = feature,
                            source = source,
                            modelName = modelName,
                            onClick = { onSelectFeature(feature) },
                        )
                    }
                }
            }
        }

        item {
            ArchitectureNoteCard()
        }
    }
}

@Composable
private fun FeatureRow(
    feature: AiFeature,
    source: ProviderMode,
    modelName: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                feature.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = (if (modelName != null) "$modelName · " else "") + feature.callEstimate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SourceBadge(source)
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SourceBadge(source: ProviderMode) {
    val (label, color) =
        when (source) {
            ProviderMode.PRO -> "Pro" to PD_PRO_COLOR
            ProviderMode.BYOK -> "BYOK" to PD_BYOK_COLOR
            ProviderMode.LOCAL -> "Local" to AI_LOCAL_COLOR
            ProviderMode.OFF -> "Off" to Color.Gray
        }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun ArchitectureNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Architecture: Cloudflare Worker + OpenAI Responses API. The app posts queries to a TwoBits Worker, which fans out to providers and calls OpenAI server-side. The app never calls OpenAI directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Managed-Pro description shown for [feature]. Only [AiFeature.ASK] maps unambiguously onto a
 * single worker op ("pricedrop.chat") — the other features multiplex several PriceDropProviders
 * (search/price/coupon calls) that don't correspond 1:1 to a [PriceDropPlan] usage counter, so
 * they keep the generic cap note rather than guess at a wrong number.
 */
private fun managedProDescription(feature: AiFeature): String {
    val generic = "This function is routed through PriceDrop's managed proxy. No key setup required. Usage counts toward your monthly cap."
    if (feature != AiFeature.ASK) return generic
    val counter = PriceDropPlan.plan.feature("pricedrop.chat")?.usageCounter ?: return generic
    return "This function is routed through PriceDrop's managed proxy. No key setup required. " +
        "Up to ${counter.monthlyLimit} ${counter.unitLabel} included with Pro each month."
}

@Composable
private fun FeatureDetailContent(
    modifier: Modifier,
    feature: AiFeature,
    hasPro: Boolean,
    featureState: FeatureState?,
    providerStates: Map<PriceDropProvider, ProviderState>,
    viewModel: SettingsViewModel,
    onManageModels: () -> Unit,
) {
    val source = featureState?.source ?: ProviderMode.BYOK
    val enabledProviders = featureState?.enabledProviders ?: feature.providers.map { it.key }.toSet()
    val selectedModelId =
        featureState?.modelId?.takeIf { id -> feature.models.any { it.id == id } }
            ?: feature.models.firstOrNull()?.id

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (feature.providers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                ) {
                    Text(
                        text = "This feature doesn't call an external provider — nothing to configure here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            item {
                AppSectionLabel("Source")
            }
            item {
                SourceSegment(
                    selected = source,
                    hasPro = hasPro,
                    hasLocal = feature == AiFeature.ASK,
                    onChange = { viewModel.setFeatureSource(feature, it) },
                )
            }

            when (source) {
                ProviderMode.PRO ->
                    item {
                        AiProManagedCard(description = managedProDescription(feature))
                    }
                ProviderMode.LOCAL ->
                    item {
                        val llmStates by viewModel.llmStates.collectAsState()
                        val selectedLlm by viewModel.selectedLlm.collectAsState()
                        LocalModelPicker(
                            models = LocalLlmModel.entries.toList(),
                            status = { (llmStates[it] ?: LocalModelState.Absent).toStatus() },
                            selected = selectedLlm,
                            onSelect = { viewModel.selectLlmModel(it) },
                            name = { it.displayName },
                            sizeLabel = { it.sizeLabel },
                            onManageModels = onManageModels,
                        )
                    }
                ProviderMode.OFF ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                        ) {
                            Text(
                                text = "This feature is disabled. Enable it with BYOK keys or a Pro subscription.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                ProviderMode.BYOK -> {
                    item {
                        AppSectionLabel("Providers")
                    }
                    val hasConfiguredKey =
                        feature.providers.any { p ->
                            p.key in enabledProviders && providerStates[p]?.isKeyValid == true
                        }
                    if (feature.providers.isNotEmpty() && !hasConfiguredKey) {
                        item {
                            AiNoKeyWarning(text = noKeyMessage(feature))
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                        ) {
                            Column {
                                feature.providers.forEach { provider ->
                                    val hasKey = providerStates[provider]?.isKeyValid == true
                                    ProviderToggleRow(
                                        provider = provider,
                                        enabled = provider.key in enabledProviders,
                                        hasKey = hasKey,
                                        onToggle = { viewModel.toggleFeatureProvider(feature, provider.key) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Manage keys in ${providerKeyLocation(feature)}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (feature.models.isNotEmpty() && selectedModelId != null) {
                        item {
                            AppSectionLabel("Model")
                        }
                        item {
                            ModelRadioList(
                                models = feature.models,
                                selected = feature.models.first { it.id == selectedModelId },
                                onSelect = { viewModel.setFeatureModel(feature, it.id) },
                                name = AiModelOption::name,
                                subtitle = AiModelOption::sub,
                                costLabel = { it.cost },
                            )
                        }
                    }
                }
            }
        }

        item {
            CallEstimateCard(feature)
        }
    }
}

@Composable
private fun SourceSegment(
    selected: ProviderMode,
    hasPro: Boolean,
    hasLocal: Boolean = false,
    onChange: (ProviderMode) -> Unit,
) {
    val options =
        buildList {
            add(Triple(ProviderMode.OFF, "Off", Color.Gray))
            add(Triple(ProviderMode.BYOK, "BYOK", PD_BYOK_COLOR))
            add(Triple(ProviderMode.PRO, "Pro", PD_PRO_COLOR))
            if (hasLocal) add(Triple(ProviderMode.LOCAL, "Local", AI_LOCAL_COLOR))
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (mode, label, color) ->
            val isSelected = selected == mode
            val locked = mode == ProviderMode.PRO && !hasPro
            Surface(
                onClick = { if (!locked) onChange(mode) },
                shape = RoundedCornerShape(10.dp),
                color =
                    if (isSelected) {
                        color.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (locked) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .padding(end = 3.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderToggleRow(
    provider: PriceDropProvider,
    enabled: Boolean,
    hasKey: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                provider.icon(),
                contentDescription = null,
                tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (hasKey) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "Key set",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                } else {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "No key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = provider.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun CallEstimateCard(feature: AiFeature) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Functions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Estimated calls",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = feature.callEstimate,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = feature.callNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
