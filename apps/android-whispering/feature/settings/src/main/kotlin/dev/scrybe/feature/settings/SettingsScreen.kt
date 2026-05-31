package dev.scrybe.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ReleaseNotes
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.localai.LocalModelState
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.LocalGemmaModel
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTransformModel
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFileManager: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val whisperStates by viewModel.whisperStates.collectAsState()
    val selectedWhisperModel by viewModel.selectedWhisperModel.collectAsState()
    val gemmaStates by viewModel.gemmaStates.collectAsState()
    val selectedGemmaModel by viewModel.selectedGemmaModel.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var showPostStopPicker by remember { mutableStateOf(false) }
    var showSampleRatePicker by remember { mutableStateOf(false) }
    var showBitRatePicker by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showProfileModelPicker by remember { mutableStateOf(false) }
    var showTransformModelPicker by remember { mutableStateOf(false) }
    val selectedProfileModel = OpenAiProfileSuggestionModel.fromApiName(uiState.profileSuggestionModel)
    val selectedTransformModel = OpenAiTransformModel.fromApiName(uiState.transformModel)
    val context = LocalContext.current
    val obsidianVaultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.setObsidianVaultUri(uri.toString())
            }
        }
    var pendingImportGemmaModel by remember { mutableStateOf<LocalGemmaModel?>(null) }
    val importGemmaFilePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.importGemmaModel(it, pendingImportGemmaModel ?: return@let) }
            pendingImportGemmaModel = null
        }
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.setLocationRecordingEnabled(true)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                SettingsSectionCard(
                    title = "Intelligence",
                    icon = Icons.Filled.AutoAwesome,
                ) {
                    SettingOptionRow(
                        title = "Profiles",
                        value = "Manage",
                        supportingText = "Pipeline recipes for recording + AI transforms + destinations",
                        onClick = onNavigateToProfiles,
                    )
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-transcribe", style = MaterialTheme.typography.bodyLarge)
                            Text("Begins immediately after stopping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.autoTranscribe, onCheckedChange = viewModel::setAutoTranscribe)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Speaker identification", style = MaterialTheme.typography.bodyLarge)
                            Text("Color-code multiple voices in transcript", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.enableSpeakerIdentification, onCheckedChange = viewModel::setEnableSpeakerIdentification)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-extract tasks", style = MaterialTheme.typography.bodyLarge)
                            Text("Pull action items from transcript with AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.enableInsightAnalysis, onCheckedChange = viewModel::setEnableInsightAnalysis)
                    }
                }

                SettingsSectionCard(
                    title = "Appearance",
                    icon = Icons.Filled.Palette,
                ) {
                    val themeOptions =
                        listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                SettingsSectionCard(
                    title = "Transcription",
                    icon = Icons.Filled.SettingsSuggest,
                ) {
                    Text(
                        text = "Choose where speech-to-text runs.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.OPENAI,
                        selected = uiState.transcriptionProvider == ProviderType.OPENAI.name,
                        enabled = true,
                        supportingText = "Cloud transcription via OpenAI Whisper API.",
                        onSelect = { viewModel.setTranscriptionProvider(ProviderType.OPENAI.name) },
                        icon = {
                            Icon(Icons.Filled.CloudDone, contentDescription = null)
                        },
                        content = {
                            if (shouldShowOpenAiApiKey(uiState.transcriptionProvider)) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = uiState.apiKey,
                                        onValueChange = viewModel::updateApiKey,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        placeholder = { Text("sk-...") },
                                        label = { Text("OpenAI API key") },
                                        trailingIcon = {
                                            when (uiState.apiKeyValidationStatus) {
                                                ApiKeyValidationStatus.Valid ->
                                                    Icon(
                                                        Icons.Filled.CloudDone,
                                                        contentDescription = "OpenAI connected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                ApiKeyValidationStatus.Invalid ->
                                                    Icon(
                                                        Icons.Filled.CloudOff,
                                                        contentDescription = "OpenAI connection failed",
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                ApiKeyValidationStatus.Validating ->
                                                    Icon(
                                                        Icons.Filled.Sync,
                                                        contentDescription = "Validating API key",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                ApiKeyValidationStatus.Unknown -> Unit
                                            }
                                        },
                                        supportingText = {
                                            uiState.apiKeyValidationMessage?.let { message ->
                                                Text(
                                                    text = message,
                                                    color =
                                                        when (uiState.apiKeyValidationStatus) {
                                                            ApiKeyValidationStatus.Invalid ->
                                                                MaterialTheme.colorScheme.error
                                                            ApiKeyValidationStatus.Valid ->
                                                                MaterialTheme.colorScheme.primary
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                )
                                            }
                                        },
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = viewModel::saveApiKey,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Save Key")
                                        }
                                        Button(
                                            onClick = viewModel::clearApiKey,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Clear Key")
                                        }
                                    }
                                    val isValidating =
                                        uiState.apiKeyValidationStatus ==
                                            ApiKeyValidationStatus.Validating
                                    OutlinedButton(
                                        onClick = viewModel::testApiConnection,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isValidating,
                                    ) {
                                        Text(if (isValidating) "Testing…" else "Test Connection")
                                    }
                                }
                            }
                        },
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.LOCAL,
                        selected = uiState.transcriptionProvider == ProviderType.LOCAL.name,
                        enabled = whisperStates.values.any { it is LocalModelState.Ready },
                        supportingText = "On-device transcription using Whisper. No internet required.",
                        alwaysShowContent = true,
                        onSelect = { viewModel.setTranscriptionProvider(ProviderType.LOCAL.name) },
                        icon = {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                        },
                        content = {
                            Text(
                                "Speech-to-text model (Whisper)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LocalWhisperModel.entries.forEach { model ->
                                WhisperModelRow(
                                    model = model,
                                    state = whisperStates[model] ?: LocalModelState.NotDownloaded,
                                    isSelected = selectedWhisperModel == model,
                                    onSelect = { viewModel.selectWhisperModel(model) },
                                    onDownload = { viewModel.downloadWhisperModel(model) },
                                    onDelete = { viewModel.deleteWhisperModel(model) },
                                )
                            }
                        },
                    )
                }

                SettingsSectionCard(
                    title = "AI Features",
                    icon = Icons.Filled.AutoAwesome,
                ) {
                    Text(
                        text = "Choose where AI transforms, rename suggestions, and tag clustering run.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.OPENAI,
                        selected = uiState.aiFeaturesProvider == ProviderType.OPENAI.name,
                        enabled = true,
                        supportingText = "Cloud AI via OpenAI models.",
                        onSelect = { viewModel.setAiFeaturesProvider(ProviderType.OPENAI.name) },
                        icon = {
                            Icon(Icons.Filled.CloudDone, contentDescription = null)
                        },
                        content = {
                            if (uiState.aiFeaturesProvider == ProviderType.OPENAI.name) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "AI profile draft model",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = selectedProfileModel.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = selectedProfileModel.supportingText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Profiles uses this model whenever you generate an AI draft.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.clearProfileSuggestionModelTestState()
                                                showProfileModelPicker = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Choose Model")
                                        }
                                    }
                                }
                            }
                        },
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.LOCAL,
                        selected = uiState.aiFeaturesProvider == ProviderType.LOCAL.name,
                        enabled = gemmaStates.values.any { it is LocalModelState.Ready },
                        supportingText = "On-device AI using Gemma. No internet required.",
                        alwaysShowContent = true,
                        onSelect = { viewModel.setAiFeaturesProvider(ProviderType.LOCAL.name) },
                        icon = {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                        },
                        content = {
                            Text(
                                "LLM model (transforms, rename, tags, clustering)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LocalGemmaModel.entries.forEach { model ->
                                GemmaModelRow(
                                    model = model,
                                    state = gemmaStates[model] ?: LocalModelState.NotDownloaded,
                                    isSelected = selectedGemmaModel == model,
                                    onSelect = { viewModel.selectGemmaModel(model) },
                                    onImport = {
                                        pendingImportGemmaModel = model
                                        importGemmaFilePicker.launch("*/*")
                                    },
                                    onGetModel = {
                                        val pageUrl = model.downloadUrl.substringBefore("/resolve/")
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)),
                                        )
                                    },
                                    onDelete = { viewModel.deleteGemmaModel(model) },
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text("Transform Model", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text =
                                "Model used to run transform profiles against your transcripts. " +
                                    "When using on-device AI, this selection applies to profiles pinned to OpenAI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedTransformModel.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = selectedTransformModel.supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedTransformModel.costSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showTransformModelPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Choose Transform Model")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Speaker identification", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Identify who is speaking after transcription. Colored speaker timeline and inline text coloring appear on the recording detail. May increase API costs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.enableSpeakerIdentification,
                            onCheckedChange = { viewModel.setEnableSpeakerIdentification(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Insight analysis", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Generate sentiment and topic markers after transcription. Results appear in the recording detail AI Analysis timeline. May increase API costs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.enableInsightAnalysis,
                            onCheckedChange = { viewModel.setEnableInsightAnalysis(it) },
                        )
                    }
                }

                SettingsSectionCard(
                    title = "Recording Defaults",
                    icon = Icons.Filled.Storage,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "Format",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Choose the default container/codec for new recordings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AudioFormat.entries.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = uiState.audioFormat == format,
                                onClick = { viewModel.setAudioFormat(format) },
                                shape =
                                    SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AudioFormat.entries.size,
                                    ),
                            ) {
                                Text(format.name)
                            }
                        }
                    }
                    SettingOptionRow(
                        title = "Sample Rate",
                        value = "${uiState.sampleRateHz / 1000} kHz",
                        supportingText = "Higher sample rates capture more detail and use more space.",
                        optionsSummary =
                            buildOptionsSummary(
                                selected = uiState.sampleRateHz,
                                options = listOf(16_000, 22_050, 44_100, 48_000),
                                label = { "${it / 1000} kHz" },
                            ),
                        onClick = { showSampleRatePicker = true },
                    )
                    SettingOptionRow(
                        title = "Bit Rate",
                        value = "${uiState.encodingBitRate / 1000} kbps",
                        supportingText = "Higher bit rates improve quality and increase file size.",
                        optionsSummary =
                            buildOptionsSummary(
                                selected = uiState.encodingBitRate,
                                options = listOf(64_000, 96_000, 128_000, 192_000, 256_000),
                                label = { "${it / 1000} kbps" },
                            ),
                        onClick = { showBitRatePicker = true },
                    )
                    SettingOptionRow(
                        title = "Channels",
                        value = if (uiState.channelCount == 1) "Mono" else "Stereo",
                        supportingText = "Mono keeps files smaller. Stereo is wider but larger.",
                        optionsSummary =
                            buildOptionsSummary(
                                selected = uiState.channelCount,
                                options = listOf(1, 2),
                                label = { if (it == 1) "Mono" else "Stereo" },
                            ),
                        onClick = { showChannelPicker = true },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Attach location to recordings")
                            Text(
                                "Saves city/region with each recording",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.locationRecordingEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    viewModel.setLocationRecordingEnabled(false)
                                } else if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.setLocationRecordingEnabled(true)
                                } else {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                }
                            },
                        )
                    }
                }

                SettingsSectionCard(
                    title = "Recording Automation",
                    icon = Icons.Filled.AutoAwesome,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "Default transform profile: ${uiState.defaultTransformProfileName ?: "None selected"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Auto-transcribe", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.autoTranscribe,
                            onCheckedChange = { viewModel.setAutoTranscribe(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Keep screen on while recording", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Prompt to rename after saving", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.showRenameAfterRecording,
                            onCheckedChange = { viewModel.setShowRenameAfterRecording(it) },
                        )
                    }
                    SettingOptionRow(
                        title = "After recording stops",
                        value =
                            when (uiState.postStopDestination) {
                                PostStopDestination.HOME -> "Return to home"
                                PostStopDestination.SESSION_REVIEW -> "Open session review"
                            },
                        supportingText = "Choose where Scrybe should land after a recording is saved from the app or notification.",
                        onClick = { showPostStopPicker = true },
                    )
                    Text(
                        text = "You can change the default prompt profile from the Profiles screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                SettingsSectionCard(
                    title = "Recording Feedback",
                    icon = Icons.Filled.Notifications,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Confirm record swipe actions", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.confirmRecordSwipeActions,
                            onCheckedChange = { viewModel.setConfirmRecordSwipeActions(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Show recording information in list", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.showRecordingInfoInList,
                            onCheckedChange = { viewModel.setShowRecordingInfoInList(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Vibrate on record start/stop", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.recordingVibrateOnStartStop,
                            onCheckedChange = { viewModel.setRecordingVibrateOnStartStop(it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Sound on record start/stop", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.recordingSoundOnStartStop,
                            onCheckedChange = { viewModel.setRecordingSoundOnStartStop(it) },
                        )
                    }
                }

                SettingsSectionCard(
                    title = "Send to App",
                    icon = Icons.Filled.IosShare,
                ) {
                    Text(
                        text = "Send transcripts to another app via a configurable intent.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Enable external integration", modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.taskForgeEnabled,
                            onCheckedChange = { viewModel.setTaskForgeEnabled(it) },
                        )
                    }
                    if (uiState.taskForgeEnabled) {
                        OutlinedTextField(
                            value = uiState.taskForgePackageName,
                            onValueChange = viewModel::setTaskForgePackageName,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Target package name") },
                            placeholder = { Text("com.example.taskforge") },
                        )
                        OutlinedTextField(
                            value = uiState.taskForgeAction,
                            onValueChange = viewModel::setTaskForgeAction,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Intent action") },
                            placeholder = { Text("android.intent.action.SEND") },
                        )
                        Text(
                            text = "Leave package name empty to show the system chooser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsSectionCard(
                    title = "Integrations",
                    icon = Icons.Filled.Sync,
                ) {
                    Text(
                        text = "Connect external apps and services.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Obsidian vault", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text =
                                        if (uiState.obsidianVaultUri.isBlank()) {
                                            "No vault selected"
                                        } else {
                                            Uri.parse(uiState.obsidianVaultUri).lastPathSegment
                                                ?: uiState.obsidianVaultUri
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { obsidianVaultLauncher.launch(null) }) {
                                Text(if (uiState.obsidianVaultUri.isBlank()) "Choose vault" else "Change")
                            }
                        }
                    }
                    HorizontalDivider()
                    IntegrationRow(Icons.Filled.CalendarToday, "Calendar", "Suggest title from active event", Color(0xFF4285F4))
                    IntegrationRow(Icons.Filled.Chat, "Slack", "Post summaries to channels", Color(0xFFE01E5A))
                    IntegrationRow(Icons.Filled.Article, "Notion", "Export sessions as pages", MaterialTheme.colorScheme.onSurface)
                    AddIntegrationRow()
                }

                SettingsSectionCard(
                    title = "File Manager",
                    icon = Icons.Filled.FolderOpen,
                ) {
                    Text(
                        text = "Browse, import, export, and manage recording files.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onNavigateToFileManager,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Manage Files")
                    }
                }

                SettingsSectionCard(
                    title = "Usage",
                    icon = Icons.Filled.Info,
                ) {
                    val storageGb = uiState.usageStats.totalStorageBytes / (1024f * 1024f * 1024f)
                    val storageFraction = (storageGb / 10f).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Audio storage", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${"%.1f".format(storageGb)} / 10 GB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { storageFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UsageMetricCell(
                            label = "Total recordings",
                            value = uiState.usageStats.recordCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        UsageMetricCell(
                            label = "Active",
                            value = uiState.usageStats.activeRecordCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UsageMetricCell(
                            label = "Archived",
                            value = uiState.usageStats.archivedRecordCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        UsageMetricCell(
                            label = "Average length",
                            value = formatCompactDuration(uiState.usageStats.averageDurationMs),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UsageMetricCell(
                            label = "Transcriptions",
                            value = uiState.usageStats.transcriptionCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        UsageMetricCell(
                            label = "Transforms",
                            value = uiState.usageStats.transformCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UsageMetricCell(
                            label = "Exports",
                            value = uiState.usageStats.exportFileCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        UsageMetricCell(
                            label = "Saved copies",
                            value = uiState.usageStats.savedCopyCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UsageMetricCell(
                            label = "Total recording time",
                            value = formatTotalDuration(uiState.usageStats.totalDurationMs),
                            modifier = Modifier.weight(1f),
                        )
                        UsageMetricCell(
                            label = "Storage used",
                            value = formatFileSize(uiState.usageStats.totalStorageBytes),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    UsageMetricCell(
                        label = "Estimated transcription spend",
                        value = formatUsd(uiState.usageStats.totalEstimatedCostUsd),
                    )
                }

                SettingsSectionCard(
                    title = "About & What's New",
                    icon = Icons.Filled.Info,
                ) {
                    Text(
                        text = "Version ${uiState.versionName.ifBlank { "dev" }} (${uiState.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = uiState.latestReleaseTitle ?: "Bundled repository changelog available for this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showChangelog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View Release Notes")
                    }
                }
            }
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Release Notes") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.releaseHistory.isEmpty()) {
                        Text(
                            text = "Release notes unavailable in this build.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        uiState.releaseHistory.forEachIndexed { index, release ->
                            ReleaseVersionCard(
                                release = release,
                                expandedByDefault = index == 0,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showPostStopPicker) {
        OptionPickerDialog(
            title = "After Recording Stops",
            options = PostStopDestination.entries.toList(),
            selected = uiState.postStopDestination,
            label = {
                when (it) {
                    PostStopDestination.HOME -> "Return to home"
                    PostStopDestination.SESSION_REVIEW -> "Open session review"
                }
            },
            onDismiss = { showPostStopPicker = false },
            onSelect = {
                viewModel.setPostStopDestination(it)
                showPostStopPicker = false
            },
        )
    }

    if (showSampleRatePicker) {
        val options = listOf(16_000, 22_050, 44_100, 48_000)
        OptionPickerDialog(
            title = "Sample Rate",
            options = options,
            selected = uiState.sampleRateHz,
            label = { "${it / 1000} kHz" },
            onDismiss = { showSampleRatePicker = false },
            onSelect = {
                viewModel.setSampleRateHz(it)
                showSampleRatePicker = false
            },
        )
    }

    if (showBitRatePicker) {
        val options = listOf(64_000, 96_000, 128_000, 192_000, 256_000)
        OptionPickerDialog(
            title = "Bit Rate",
            options = options,
            selected = uiState.encodingBitRate,
            label = { "${it / 1000} kbps" },
            onDismiss = { showBitRatePicker = false },
            onSelect = {
                viewModel.setEncodingBitRate(it)
                showBitRatePicker = false
            },
        )
    }

    if (showChannelPicker) {
        val options = listOf(1, 2)
        OptionPickerDialog(
            title = "Channels",
            options = options,
            selected = uiState.channelCount,
            label = { if (it == 1) "Mono" else "Stereo" },
            onDismiss = { showChannelPicker = false },
            onSelect = {
                viewModel.setChannelCount(it)
                showChannelPicker = false
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected && isReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
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
                        if (!isSelected) {
                            TextButton(onClick = onSelect) { Text("Use") }
                        }
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
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${state.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                is LocalModelState.Ready ->
                    if (isSelected) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected && isReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
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
                        if (!isSelected) {
                            TextButton(onClick = onSelect) { Text("Use") }
                        }
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
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Importing… ${state.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                is LocalModelState.Ready ->
                    if (isSelected) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                is LocalModelState.Error ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                    }
            }
        }
    }
}

@Composable
private fun LocalModelDownloadSection(
    label: String,
    state: LocalModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        when (state) {
            is LocalModelState.NotDownloaded ->
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Download")
                }
            is LocalModelState.Downloading ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${state.progressPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            is LocalModelState.Ready ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete model")
                    }
                }
            is LocalModelState.Error ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry")
                    }
                }
        }
    }
}

@Composable
private fun AddIntegrationRow() {
    HorizontalDivider(Modifier.padding(start = 34.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text("Add integration", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("Browse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun IntegrationRow(
    icon: ImageVector,
    label: String,
    sub: String,
    color: Color,
    isLast: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Connect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isLast) HorizontalDivider(Modifier.padding(start = 34.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrybeSectionCard(
        containerColor = containerColor,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun ReleaseVersionCard(
    release: ReleaseNotes,
    expandedByDefault: Boolean,
) {
    var expanded by remember(release.title) { mutableStateOf(expandedByDefault) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "${release.summaryItems.size} highlighted changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse release notes" else "Expand release notes",
                    )
                }
            }

            if (expanded) {
                release.groups.forEachIndexed { index, group ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        group.items.forEachIndexed { itemIndex, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "${itemIndex + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
