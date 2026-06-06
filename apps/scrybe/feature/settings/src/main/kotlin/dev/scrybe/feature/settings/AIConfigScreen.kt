package dev.scrybe.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.localai.LocalModelState
import dev.scrybe.core.model.LocalGemmaModel
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTransformModel

private val PRO_COLOR get() = Color(0xFF88D7A8)
private val BYOK_COLOR get() = Color(0xFF7DD4DC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val whisperStates by viewModel.whisperStates.collectAsState()
    val selectedWhisperModel by viewModel.selectedWhisperModel.collectAsState()
    val gemmaStates by viewModel.gemmaStates.collectAsState()
    val selectedGemmaModel by viewModel.selectedGemmaModel.collectAsState()

    var pendingImportGemmaModel by remember { mutableStateOf<LocalGemmaModel?>(null) }
    val importGemmaFilePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.importGemmaModel(it, pendingImportGemmaModel ?: return@let) }
            pendingImportGemmaModel = null
        }

    val selectedTransformModel = OpenAiTransformModel.fromApiName(uiState.transformModel)
    val selectedProfileModel = OpenAiProfileSuggestionModel.fromApiName(uiState.profileSuggestionModel)
    var showTransformModelPicker by remember { mutableStateOf(false) }
    var showProfileModelPicker by remember { mutableStateOf(false) }

    Scaffold(
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
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = ScrybeLayoutDefaults.screenHorizontalPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                        .widthIn(max = ScrybeLayoutDefaults.contentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
            ) {
                AiCredentialsDock(
                    tier = uiState.subscriptionTier,
                    apiKey = uiState.apiKey,
                    validationStatus = uiState.apiKeyValidationStatus,
                    validationMessage = uiState.apiKeyValidationMessage,
                    onApiKeyChange = viewModel::updateApiKey,
                    onSave = viewModel::saveApiKey,
                )

                AiSectionCard(icon = Icons.Default.Mic, title = "Transcription") {
                    AiSourceToggle(
                        provider = uiState.transcriptionProvider,
                        onProviderChange = viewModel::setTranscriptionProvider,
                    )
                    if (uiState.transcriptionProvider == "LOCAL") {
                        Text(
                            "On-device models — runs fully offline, no API key required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LocalWhisperModel.entries.forEach { model ->
                            WhisperModelRow(
                                model = model,
                                state = whisperStates[model] ?: LocalModelState.NotDownloaded,
                                isSelected = model == selectedWhisperModel,
                                onSelect = { viewModel.selectWhisperModel(model) },
                                onDownload = { viewModel.downloadWhisperModel(model) },
                                onDelete = { viewModel.deleteWhisperModel(model) },
                            )
                        }
                    } else {
                        CloudInfo(
                            tier = uiState.subscriptionTier,
                            apiKey = uiState.apiKey,
                            text = "Cloud transcription via OpenAI Whisper. Pro includes managed access — no personal key needed.",
                        )
                    }
                }

                AiSectionCard(icon = Icons.Default.AutoAwesome, title = "Transforms & Profiles") {
                    AiSourceToggle(
                        provider = uiState.aiFeaturesProvider,
                        onProviderChange = viewModel::setAiFeaturesProvider,
                    )
                    if (uiState.aiFeaturesProvider == "LOCAL") {
                        Text(
                            "On-device AI — runs fully offline, no API key required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LocalGemmaModel.entries.forEach { model ->
                            GemmaModelRow(
                                model = model,
                                state = gemmaStates[model] ?: LocalModelState.NotDownloaded,
                                isSelected = model == selectedGemmaModel,
                                onSelect = { viewModel.selectGemmaModel(model) },
                                onImport = {
                                    pendingImportGemmaModel = model
                                    importGemmaFilePicker.launch("*/*")
                                },
                                onGetModel = {},
                                onDelete = { viewModel.deleteGemmaModel(model) },
                            )
                        }
                    } else {
                        SettingOptionRow(
                            title = "Transform model",
                            value = "${selectedTransformModel.title} — ${selectedTransformModel.costSummary}",
                            onClick = { showTransformModelPicker = true },
                        )
                        SettingOptionRow(
                            title = "Profile draft model",
                            value = selectedProfileModel.title,
                            onClick = { showProfileModelPicker = true },
                        )
                    }
                }

                AiSectionCard(icon = Icons.Default.Insights, title = "Analysis") {
                    AiToggleRow(
                        title = "Speaker identification",
                        subtitle = "Color-code multiple voices in transcript",
                        checked = uiState.enableSpeakerIdentification,
                        onCheckedChange = viewModel::setEnableSpeakerIdentification,
                    )
                    HorizontalDivider()
                    AiToggleRow(
                        title = "Insight extraction",
                        subtitle = "Pull tasks and action items from transcripts with AI",
                        checked = uiState.enableInsightAnalysis,
                        onCheckedChange = viewModel::setEnableInsightAnalysis,
                    )
                }
            }
        }
    }

    if (showTransformModelPicker) {
        OptionPickerDialog(
            title = "Transform Model",
            options = OpenAiTransformModel.entries.toList(),
            selected = selectedTransformModel,
            label = { "${it.title} — ${it.costSummary}" },
            onDismiss = { showTransformModelPicker = false },
            onSelect = {
                viewModel.setTransformModel(it.apiName)
                showTransformModelPicker = false
            },
        )
    }
    if (showProfileModelPicker) {
        OptionPickerDialog(
            title = "Profile Draft Model",
            options = OpenAiProfileSuggestionModel.entries.toList(),
            selected = selectedProfileModel,
            label = { it.title },
            onDismiss = { showProfileModelPicker = false },
            onSelect = {
                viewModel.setProfileSuggestionModel(it.apiName)
                showProfileModelPicker = false
            },
        )
    }
}

@Composable
private fun AiCredentialsDock(
    tier: SubscriptionTier,
    apiKey: String,
    validationStatus: ApiKeyValidationStatus,
    validationMessage: String?,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var proExpanded by rememberSaveable { mutableStateOf(false) }
    var byokExpanded by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column {
            AiCredentialRow(
                icon = Icons.Default.WorkspacePremium,
                iconTint = PRO_COLOR,
                title = "Pro subscription",
                status = if (tier is SubscriptionTier.Pro) "Active" else "Not subscribed",
                subtitle = if (tier is SubscriptionTier.Pro) "Managed API · no key needed" else "\$1.99/mo · tap to expand",
                expanded = proExpanded,
                onToggle = { proExpanded = !proExpanded },
            )
            if (proExpanded) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 14.dp),
                ) {
                    Text(
                        text =
                            when (tier) {
                                SubscriptionTier.Free ->
                                    "Upgrade to Pro for managed OpenAI access — no personal API key needed for transcription or transforms."
                                SubscriptionTier.Pro ->
                                    "Your Pro subscription is active. Transcription and AI transforms are managed automatically."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            AiCredentialRow(
                icon = Icons.Default.Key,
                iconTint = BYOK_COLOR,
                title = "OpenAI API key",
                status = if (apiKey.isNotBlank()) "Connected" else "Not configured",
                subtitle = if (apiKey.length > 8) "sk-…${apiKey.takeLast(4)}" else "Bring your own key",
                expanded = byokExpanded,
                onToggle = { byokExpanded = !byokExpanded },
            )
            if (byokExpanded) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("OpenAI API key") },
                        placeholder = { Text("sk-…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show")
                            }
                        },
                    )
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = validationStatus != ApiKeyValidationStatus.Validating,
                    ) {
                        if (validationStatus == ApiKeyValidationStatus.Validating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Checking…")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                    validationMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when (validationStatus) {
                                    ApiKeyValidationStatus.Valid -> MaterialTheme.colorScheme.primary
                                    ApiKeyValidationStatus.Invalid -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCredentialRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    status: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.18f)) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun AiSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSourceToggle(
    provider: String,
    onProviderChange: (String) -> Unit,
) {
    val options = listOf("OPENAI" to "Cloud", "LOCAL" to "On-device")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = provider == value,
                onClick = { onProviderChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun CloudInfo(
    tier: SubscriptionTier,
    apiKey: String,
    text: String,
) {
    val hasAccess = tier is SubscriptionTier.Pro || apiKey.isNotBlank()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (hasAccess) {
                        PRO_COLOR.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    },
                )
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (hasAccess) Icons.Default.Check else Icons.Default.Key,
            contentDescription = null,
            tint = if (hasAccess) PRO_COLOR else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (hasAccess) text else "No API access configured. Add an API key or upgrade to Pro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WhisperModelRow(
    model: LocalWhisperModel,
    state: LocalModelState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReady = state is LocalModelState.Ready
    val containerColor =
        if (isSelected && isReady) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${model.description} · ${model.sizeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isReady) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isSelected) TextButton(onClick = onSelect) { Text("Use") }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            when (state) {
                is LocalModelState.NotDownloaded ->
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Download")
                    }
                is LocalModelState.Downloading ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${state.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                is LocalModelState.Ready ->
                    if (isSelected) Text("Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                is LocalModelState.Error ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                    }
            }
        }
    }
}

@Composable
private fun GemmaModelRow(
    model: LocalGemmaModel,
    state: LocalModelState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onImport: () -> Unit,
    onGetModel: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReady = state is LocalModelState.Ready
    val containerColor =
        if (isSelected && isReady) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${model.description} · ${model.sizeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isReady) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isSelected) TextButton(onClick = onSelect) { Text("Use") }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            when (state) {
                is LocalModelState.NotDownloaded ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Import .task file")
                        }
                        TextButton(onClick = onGetModel, modifier = Modifier.fillMaxWidth()) {
                            Text("Get model on HuggingFace ↗")
                        }
                    }
                is LocalModelState.Downloading ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Importing… ${state.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                is LocalModelState.Ready ->
                    if (isSelected) Text("Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                is LocalModelState.Error ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                    }
            }
        }
    }
}
