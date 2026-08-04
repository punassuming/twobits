package dev.scrybe.feature.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.core.pro.ExecutionMode
import com.twobits.design.components.AiCredentialsDock
import com.twobits.design.components.AiNoKeyWarning
import com.twobits.design.components.AiProManagedCard
import com.twobits.design.components.AiSectionCard
import com.twobits.design.components.AiSourceSegment
import com.twobits.design.components.LocalModelPanel
import com.twobits.design.components.LocalModelStatus
import com.twobits.design.components.ModelRadioList
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiTranscriptionModel
import dev.scrybe.core.model.OpenAiTransformModel

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
    onNavigateToAiCallLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val whisperStates by viewModel.whisperStates.collectAsState()
    val storedSpokenLanguages by viewModel.spokenLanguages.collectAsState()
    var spokenLanguagesText by remember { mutableStateOf<String?>(null) }
    val selectedWhisperModel by viewModel.selectedWhisperModel.collectAsState()
    val llmStates by viewModel.llmStates.collectAsState()
    val selectedLlmModel by viewModel.selectedLlmModel.collectAsState()

    val activity = LocalContext.current as? android.app.Activity
    val hasPro = uiState.subscriptionTier is SubscriptionTier.Pro
    val selectedTransformModel = OpenAiTransformModel.fromApiName(uiState.transformModel)
    val selectedTranscriptionModel = OpenAiTranscriptionModel.fromApiName(uiState.transcriptionModel)
    var showTransformModelPicker by remember { mutableStateOf(false) }

    // Derived from uiState on every recomposition rather than captured once in rememberSaveable:
    // the old snapshot was taken before DataStore emitted the real values and never re-synced, so
    // the control could permanently display BYOK while the stored preference was actually LOCAL —
    // hiding exactly the misconfiguration that silently routes diarization/insights on-device.
    val transcriptionMode =
        executionModeFromSegment(
            when {
                uiState.transcriptionProvider == "LOCAL" -> "local"
                hasPro -> "pro"
                else -> "byok"
            },
        )
    val featuresMode =
        executionModeFromSegment(
            when {
                uiState.aiFeaturesProvider == "LOCAL" -> "local"
                hasPro -> "pro"
                else -> "byok"
            },
        )

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

                CallBudgetCard(
                    speakerIdEnabled = uiState.enableSpeakerIdentification,
                    insightsEnabled = uiState.enableInsightAnalysis,
                )

                AiSectionCard(icon = Icons.Default.Mic, title = "Transcription") {
                    AiSourceSegment(
                        selected = transcriptionMode.toSegment(),
                        hasPro = hasPro,
                        onChange = { seg ->
                            val mode = executionModeFromSegment(seg)
                            viewModel.setTranscriptionProvider(if (mode == ExecutionMode.LOCAL) "LOCAL" else "OPENAI")
                        },
                    )
                    when (transcriptionMode) {
                        ExecutionMode.PRO ->
                            AiProManagedCard(
                                description = "Transcription via managed OpenAI Whisper. Pro subscription active — no personal key needed.",
                            )
                        ExecutionMode.BYOK, ExecutionMode.OFF -> {
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
                        ExecutionMode.LOCAL ->
                            LocalModelPanel(
                                sectionLabel = "Whisper — speech-to-text",
                                models = LocalWhisperModel.entries.toList(),
                                status = { (whisperStates[it] ?: LocalModelState.Absent).toStatus() },
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
                    if (transcriptionMode != ExecutionMode.LOCAL) {
                        OutlinedTextField(
                            value = spokenLanguagesText ?: storedSpokenLanguages,
                            onValueChange = {
                                spokenLanguagesText = it
                                viewModel.setSpokenLanguages(it)
                            },
                            singleLine = true,
                            label = { Text("Spoken languages") },
                            supportingText = {
                                Text("Comma-separated, e.g. English, Korean — keeps multilingual recordings in their original languages")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                AiSectionCard(icon = Icons.Default.AutoAwesome, title = "Transforms & Profiles") {
                    AiSourceSegment(
                        selected = featuresMode.toSegment(),
                        hasPro = hasPro,
                        onChange = { seg ->
                            val mode = executionModeFromSegment(seg)
                            viewModel.setAiFeaturesProvider(if (mode == ExecutionMode.LOCAL) "LOCAL" else "OPENAI")
                        },
                    )
                    when (featuresMode) {
                        ExecutionMode.PRO ->
                            AiProManagedCard(
                                description = "AI transforms managed by Pro subscription. No personal key needed.",
                            )
                        ExecutionMode.BYOK, ExecutionMode.OFF -> {
                            if (uiState.apiKey.isBlank()) AiNoKeyWarning()
                            SettingOptionRow(
                                title = "Transform model",
                                value = selectedTransformModel?.title ?: "Select",
                                onClick = { showTransformModelPicker = true },
                            )
                        }
                        ExecutionMode.LOCAL ->
                            LocalModelPanel(
                                sectionLabel = "Gemma — on-device LLM",
                                models = LocalLlmModel.entries.toList(),
                                status = { (llmStates[it] ?: LocalModelState.Absent).toStatus() },
                                selected = selectedLlmModel,
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
                        title = "AI call debug",
                        subtitle = "Log every AI request/response (transcription, diarization, insights) for on-device troubleshooting",
                        checked = uiState.debugDiarization,
                        onCheckedChange = viewModel::setDebugDiarization,
                    )
                    if (uiState.debugDiarization) {
                        HorizontalDivider()
                        AiNavigationRow(
                            title = "View AI call log",
                            subtitle = "Recent requests and responses across every AI feature",
                            onClick = onNavigateToAiCallLog,
                        )
                    }
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

@Composable
private fun AiNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallBudgetCard(
    speakerIdEnabled: Boolean,
    insightsEnabled: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "API calls per session",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CallBudgetRow("Transcription", calls = 1, color = MaterialTheme.colorScheme.primary)
                if (speakerIdEnabled) {
                    CallBudgetRow("Speakers", calls = 2, color = MaterialTheme.colorScheme.tertiary)
                }
                if (insightsEnabled) {
                    CallBudgetRow("Insights", calls = 2, color = MaterialTheme.colorScheme.secondary)
                }
                CallBudgetRow("Transforms", calls = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val parts =
                buildList {
                    add("Transcription = 1 call")
                    if (speakerIdEnabled) add("Speaker ID = 2 (timestamped re-transcription + assignment)")
                    if (insightsEnabled) add("Insights = 2 (sentiment + topics)")
                    add("Transforms = 1–2 per transform applied")
                }
            Text(
                "Per session. ${parts.joinToString(" · ")}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallBudgetRow(
    label: String,
    calls: Int,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(calls) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(color.copy(alpha = 0.6f + it * 0.2f), CircleShape),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
