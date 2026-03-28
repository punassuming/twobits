package dev.scrybe.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ReleaseNotes
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var showSavedFiles by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showPostStopPicker by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var showSampleRatePicker by remember { mutableStateOf(false) }
    var showBitRatePicker by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }

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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null)
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    }
                    SettingOptionRow(
                        title = "Theme",
                        value =
                            when (uiState.themeMode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        supportingText = "Use the system theme or force light or dark mode.",
                        onClick = { showThemePicker = true },
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SettingsSuggest, contentDescription = null)
                        Text("Provider", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Choose where transcription runs. Provider-specific credentials live inside the active provider section.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.OPENAI,
                        selected = uiState.defaultProvider == ProviderType.OPENAI.name,
                        enabled = true,
                        supportingText = "Cloud transcription, profile suggestions, and AI transforms.",
                        onSelect = { viewModel.setDefaultProvider(ProviderType.OPENAI.name) },
                        icon = {
                            Icon(Icons.Filled.CloudDone, contentDescription = null)
                        },
                        content = {
                            if (shouldShowOpenAiApiKey(uiState.defaultProvider)) {
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
                                                            ApiKeyValidationStatus.Invalid -> MaterialTheme.colorScheme.error
                                                            ApiKeyValidationStatus.Valid -> MaterialTheme.colorScheme.primary
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
                                }
                            }
                        },
                    )
                    ProviderOptionCard(
                        providerType = ProviderType.LOCAL,
                        selected = uiState.defaultProvider == ProviderType.LOCAL.name,
                        enabled = false,
                        supportingText = "On-device transcription is planned, but it is not available in this build.",
                        onSelect = {},
                        icon = {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                        },
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Storage, contentDescription = null)
                        Text("Recording Defaults", style = MaterialTheme.typography.titleMedium)
                    }
                    SettingOptionRow(
                        title = "Format",
                        value = uiState.audioFormat.name,
                        supportingText = "Choose the default container/codec for new recordings.",
                        optionsSummary =
                            buildOptionsSummary(
                                selected = uiState.audioFormat,
                                options = AudioFormat.entries.toList(),
                                label = { it.name },
                            ),
                        onClick = { showFormatPicker = true },
                    )
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
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text("Recording Behavior", style = MaterialTheme.typography.titleMedium)
                    }
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
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Text("Saved Files", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "${uiState.savedFiles.size} files available in app storage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            viewModel.refreshSavedFiles()
                            showSavedFiles = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Browse Saved Files")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Text("Usage", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Records: ${uiState.usageStats.recordCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("Transcriptions: ${uiState.usageStats.transcriptionCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("Transforms: ${uiState.usageStats.transformCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("Total recording time: ${formatTotalDuration(uiState.usageStats.totalDurationMs)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Storage used: ${formatFileSize(uiState.usageStats.totalStorageBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Estimated transcription spend: ${formatUsd(uiState.usageStats.totalEstimatedCostUsd)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Text("About & What's New", style = MaterialTheme.typography.titleMedium)
                    }
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

    if (showSavedFiles) {
        SavedFilesDialog(
            files = uiState.savedFiles,
            onDismiss = { showSavedFiles = false },
            onDelete = viewModel::deleteSavedFile,
        )
    }

    if (showThemePicker) {
        OptionPickerDialog(
            title = "Choose Theme",
            options = ThemeMode.entries.toList(),
            selected = uiState.themeMode,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> "Follow system"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            },
            onDismiss = { showThemePicker = false },
            onSelect = {
                viewModel.setThemeMode(it)
                showThemePicker = false
            },
        )
    }

    if (showFormatPicker) {
        OptionPickerDialog(
            title = "Default Format",
            options = AudioFormat.entries.toList(),
            selected = uiState.audioFormat,
            label = { it.name },
            onDismiss = { showFormatPicker = false },
            onSelect = {
                viewModel.setAudioFormat(it)
                showFormatPicker = false
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
