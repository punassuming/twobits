package dev.scrybe.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.AiCredentialsDock
import com.twobits.design.components.AiNoKeyWarning
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionHeader
import com.twobits.design.components.AiSourceSegment
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.localai.LocalModelState
import dev.scrybe.core.model.LocalGemmaModel
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTranscriptionModel
import dev.scrybe.core.model.OpenAiTransformModel

private fun LocalModelState.toStatus(): LocalModelStatus =
    when (this) {
        is LocalModelState.NotDownloaded -> LocalModelStatus.NotAvailable
        is LocalModelState.Downloading -> LocalModelStatus.InProgress(progressPercent)
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

    val activity = LocalContext.current as? android.app.Activity
    val hasPro = uiState.subscriptionTier is SubscriptionTier.Pro
    val selectedTransformModel = OpenAiTransformModel.fromApiName(uiState.transformModel)
    val selectedProfileModel = OpenAiProfileSuggestionModel.fromApiName(uiState.profileSuggestionModel)
    val selectedTranscriptionModel = OpenAiTranscriptionModel.fromApiName(uiState.transcriptionModel)
    var showProfileModelPicker by remember { mutableStateOf(false) }
    var showTransformModelPicker by remember { mutableStateOf(false) }

    var transcriptionSegment by rememberSaveable {
        mutableStateOf(
            when {
                uiState.transcriptionProvider == "LOCAL" -> "local"
                hasPro -> "pro"
                else -> "byok"
            },
        )
    }
    var featuresSegment by rememberSaveable {
        mutableStateOf(
            when {
                uiState.aiFeaturesProvider == "LOCAL" -> "local"
                hasPro -> "pro"
                else -> "byok"
            },
        )
    }

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
                    proLabel = "Scrybe Pro",
                    proPrice = "\$1.99/mo",
                    hasPro = hasPro,
                    apiKey = uiState.apiKey,
                    isValidating = uiState.apiKeyValidationStatus == ApiKeyValidationStatus.Validating,
                    validationMessage = uiState.apiKeyValidationMessage,
                    isKeyValid =
                        when (uiState.apiKeyValidationStatus) {
                            ApiKeyValidationStatus.Valid -> true
                            ApiKeyValidationStatus.Invalid -> false
                            else -> null
                        },
                    onApiKeyChange = viewModel::updateApiKey,
                    onSave = viewModel::saveApiKey,
                    onClear = viewModel::clearApiKey,
                    onTest = viewModel::testApiConnection,
                    onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                )

                AiSectionCard(icon = Icons.Default.Mic, title = "Transcription") {
                    AiSourceSegment(
                        selected = transcriptionSegment,
                        hasPro = hasPro,
                        onChange = { seg ->
                            transcriptionSegment = seg
                            viewModel.setTranscriptionProvider(if (seg == "local") "LOCAL" else "OPENAI")
                        },
                    )
                    when (transcriptionSegment) {
                        "pro" ->
                            AiProManagedCard(
                                description = "Transcription via managed OpenAI Whisper. Pro subscription active — no personal key needed.",
                            )
                        "byok" -> {
                            if (uiState.apiKey.isBlank()) {
                                AiNoKeyWarning()
                            } else {
                                Text(
                                    "Transcription model",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                ModelRadioList(
                                    models = OpenAiTranscriptionModel.entries.toList(),
                                    selected = selectedTranscriptionModel,
                                    onSelect = { viewModel.setTranscriptionModel(it) },
                                    name = { it.title },
                                    subtitle = { it.subtitle },
                                    costLabel = { it.costLabel },
                                )
                            }
                        }
                        else ->
                            LocalModelPanel(
                                sectionLabel = "Whisper — speech-to-text",
                                models = LocalWhisperModel.entries.toList(),
                                status = { (whisperStates[it] ?: LocalModelState.NotDownloaded).toStatus() },
                                selected = selectedWhisperModel,
                                onSelect = { viewModel.selectWhisperModel(it) },
                                onPrimaryAction = { viewModel.downloadWhisperModel(it) },
                                primaryActionLabel = "Download",
                                primaryActionIcon = Icons.Default.CloudDownload,
                                onDelete = { viewModel.deleteWhisperModel(it) },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                description = { it.description },
                                progressLabel = "Downloading",
                            )
                    }
                }

                AiSectionCard(icon = Icons.Default.AutoAwesome, title = "Transforms & Profiles") {
                    AiSourceSegment(
                        selected = featuresSegment,
                        hasPro = hasPro,
                        onChange = { seg ->
                            featuresSegment = seg
                            viewModel.setAiFeaturesProvider(if (seg == "local") "LOCAL" else "OPENAI")
                        },
                    )
                    when (featuresSegment) {
                        "pro" ->
                            AiProManagedCard(
                                description = "AI transforms managed by Pro subscription. No personal key needed.",
                            )
                        "byok" -> {
                            if (uiState.apiKey.isBlank()) AiNoKeyWarning()
                            SettingOptionRow(
                                title = "Transform model",
                                value = selectedTransformModel?.title ?: "Select",
                                onClick = { showTransformModelPicker = true },
                            )
                            SettingOptionRow(
                                title = "Profile draft model",
                                value = selectedProfileModel.title,
                                onClick = { showProfileModelPicker = true },
                            )
                        }
                        else ->
                            LocalModelPanel(
                                sectionLabel = "Gemma — on-device LLM",
                                sectionSubtitle = "Download from HuggingFace, then import the .gguf file.",
                                models = LocalGemmaModel.entries.toList(),
                                status = { (gemmaStates[it] ?: LocalModelState.NotDownloaded).toStatus() },
                                selected = selectedGemmaModel,
                                onSelect = { viewModel.selectGemmaModel(it) },
                                onPrimaryAction = {
                                    pendingImportGemmaModel = it
                                    importGemmaFilePicker.launch("*/*")
                                },
                                primaryActionLabel = "Import",
                                primaryActionIcon = Icons.Default.FolderOpen,
                                onDelete = { viewModel.deleteGemmaModel(it) },
                                name = { it.displayName },
                                sizeLabel = { it.sizeLabel },
                                description = { it.description },
                                progressLabel = "Importing",
                                huggingFaceUrl = { it.huggingFacePageUrl },
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
                    HorizontalDivider()
                    AiToggleRow(
                        title = "Diarization debug",
                        subtitle = "Show raw speaker segments and model output on session screens",
                        checked = uiState.debugDiarization,
                        onCheckedChange = viewModel::setDebugDiarization,
                    )
                }
            }
        }
    }

    if (showTransformModelPicker) {
        ModelPickerDialog(
            title = "Transform Model",
            models = OpenAiTransformModel.entries.toList(),
            selected = selectedTransformModel,
            name = { it.title },
            subtitle = { it.supportingText },
            costLabel = { it.costSummary },
            onDismiss = { showTransformModelPicker = false },
            onSelect = { viewModel.setTransformModel(it.apiName) },
        )
    }

    if (showProfileModelPicker) {
        ModelPickerDialog(
            title = "Profile Draft Model",
            models = OpenAiProfileSuggestionModel.entries.toList(),
            selected = selectedProfileModel,
            name = { it.title },
            subtitle = { it.supportingText },
            costLabel = { "" },
            onDismiss = { showProfileModelPicker = false },
            onSelect = { viewModel.setProfileSuggestionModel(it.apiName) },
        )
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
private fun <T> ModelPickerDialog(
    title: String,
    models: List<T>,
    selected: T?,
    name: (T) -> String,
    subtitle: (T) -> String,
    costLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                models.forEach { model ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(model)
                                    onDismiss()
                                }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name(model), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                subtitle(model),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val cost = costLabel(model)
                            if (cost.isNotBlank()) {
                                Text(
                                    cost,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
