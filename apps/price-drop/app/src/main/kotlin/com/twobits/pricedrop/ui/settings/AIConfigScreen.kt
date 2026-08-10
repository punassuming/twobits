package com.twobits.pricedrop.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionCard
import com.twobits.design.components.AppSectionLabel
import com.twobits.design.components.CollapsibleProviderRow
import com.twobits.design.components.CredentialRequirement
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList
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

private fun PriceDropProvider.icon(): ImageVector =
    when (this) {
        PriceDropProvider.OPENAI -> Icons.Filled.AutoAwesome
        PriceDropProvider.WEB_SEARCH -> Icons.Filled.Search
        PriceDropProvider.SHOPPING -> Icons.Filled.ShoppingCart
        PriceDropProvider.SERPER -> Icons.Filled.ShoppingCart
        PriceDropProvider.RAINFOREST -> Icons.Filled.Park
    }

/**
 * Overall importance of this provider's key across the whole app (not one feature) — see each
 * provider's [PriceDropProvider.description] for exactly what breaks vs degrades without it.
 */
private fun PriceDropProvider.requirement(): CredentialRequirement =
    when (this) {
        PriceDropProvider.OPENAI -> CredentialRequirement.REQUIRED
        PriceDropProvider.RAINFOREST -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.WEB_SEARCH -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.SHOPPING -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.SERPER -> CredentialRequirement.RECOMMENDED
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
            FeatureListContent(
                modifier = Modifier.padding(padding),
                hasPro = hasPro,
                providerStates = providerStates,
                featureStates = featureStates,
                viewModel = viewModel,
                onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                onSelectFeature = { selectedFeature = it },
            )
        } else {
            FeatureDetailContent(
                modifier = Modifier.padding(padding),
                feature = feature,
                hasPro = hasPro,
                featureState = featureStates[feature],
                providerStates = providerStates,
                viewModel = viewModel,
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
                PriceDropProvider.entries.forEach { provider ->
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
            CallBudgetCard()
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
                            providerStates = providerStates,
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
    providerStates: Map<PriceDropProvider, ProviderState>,
    onClick: () -> Unit,
) {
    // Best-effort signal only: "at least one of this feature's providers has a saved key",
    // not a precise per-sub-path check (e.g. SEARCH lists OPENAI, which alone only unlocks
    // URL-paste, not keyword search) — see FeatureRow's doc for the full caveat.
    val needsKey =
        source == ProviderMode.BYOK &&
            feature.providers.isNotEmpty() &&
            feature.providers.none { providerStates[it]?.key?.isNotBlank() == true }
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
        if (needsKey) {
            NeedsKeyChip()
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

/** Flags a BYOK feature whose providers have no saved key — it will error, not just degrade. */
@Composable
private fun NeedsKeyChip() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
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
                        .background(MaterialTheme.colorScheme.error),
            )
            Text(
                text = "No key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
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
private fun CallBudgetCard() {
    val scheme = MaterialTheme.colorScheme
    val total = AiFeature.entries.sumOf { it.callWeight }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Functions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Call budget estimate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "~$total calls/event",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AiFeature.entries.forEach { feature ->
                    Box(
                        modifier =
                            Modifier
                                .weight(feature.callWeight.toFloat())
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(feature.budgetColor(scheme)),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AiFeature.entries.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(feature.budgetColor(scheme)),
                        )
                        Text(
                            text = "${feature.label} ${feature.callWeight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Estimate per user-triggered event. Background checks (price polling) cost 1–2 calls per item.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
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
                        LocalModelPanel(
                            sectionLabel = "On-device LLM",
                            models = LocalLlmModel.entries.toList(),
                            status = { (llmStates[it] ?: LocalModelState.Absent).toStatus() },
                            selected = selectedLlm,
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
                            text = "Manage keys for each provider in the Credentials section.",
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

@Composable
private fun ProviderCredentialItem(
    provider: PriceDropProvider,
    state: ProviderState,
    viewModel: SettingsViewModel,
) {
    var draft by rememberSaveable(provider) { mutableStateOf(state.key) }
    LaunchedEffect(state.key) { if (draft != state.key) draft = state.key }

    val maskedKey =
        when {
            state.key.length > 8 -> "${state.key.take(4)}${"•".repeat(7)}${state.key.takeLast(4)}"
            state.key.isNotBlank() -> "••••"
            else -> null
        }

    CollapsibleProviderRow(
        icon = {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (state.isKeyValid == true) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    provider.icon(),
                    contentDescription = null,
                    tint = if (state.isKeyValid == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        title = provider.displayName,
        description = provider.description,
        summary = provider.summary,
        maskedKey = maskedKey,
        isKeyValid = state.isKeyValid,
        isValidating = state.isValidating,
        validationMessage = state.validationMessage,
        apiKey = draft,
        onApiKeyChange = { draft = it },
        onSave = { viewModel.setProviderKey(provider, draft) },
        onTest = { viewModel.testProviderKey(provider, draft) },
        onClear = {
            draft = ""
            viewModel.clearProviderKey(provider)
        },
        setupHint = provider.setupHint,
        signupUrl = provider.signupUrl,
        costEstimate = provider.costEstimate,
        requirement = provider.requirement(),
    )
}
