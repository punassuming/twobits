package dev.scrybe.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.AppSectionCard
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFileManager: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToAiConfig: () -> Unit = {},
    onNavigateToWhatsNew: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    val uriHandler = LocalUriHandler.current
    var showPostStopPicker by remember { mutableStateOf(false) }
    var showSampleRatePicker by remember { mutableStateOf(false) }
    var showBitRatePicker by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
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
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) viewModel.setLocationRecordingEnabled(false)
        }
    LaunchedEffect(uiState.locationRecordingEnabled) {
        if (uiState.locationRecordingEnabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
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
                ProSubscriptionCard(
                    tier = uiState.subscriptionTier,
                    isPurchasing = uiState.isPurchasing,
                    purchaseError = uiState.purchaseError,
                    onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                    onRestore = viewModel::restorePurchases,
                    onDismissError = viewModel::dismissPurchaseError,
                )

                ProfilesProminentCard(onClick = onNavigateToProfiles)

                SettingsSectionCard(title = "Intelligence", icon = Icons.Filled.AutoAwesome) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNavigateToAiConfig)
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI configuration", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Transcription · transforms · local models · API key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Location tagging", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Tag recordings with place name automatically",
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
                    title = "Recording",
                    icon = Icons.Filled.Storage,
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
                }

                SettingsSectionCard(
                    title = "Recording Behavior",
                    icon = Icons.Filled.AutoAwesome,
                ) {
                    Text(
                        text = "Default transform profile: ${uiState.defaultTransformProfileName ?: "None selected"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-transcribe", style = MaterialTheme.typography.bodyLarge)
                            Text("Begins immediately after stopping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.autoTranscribe, onCheckedChange = viewModel::setAutoTranscribe)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsSectionCard(
                    title = "Recording Feedback",
                    icon = Icons.Filled.Notifications,
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
                        onClick = onNavigateToWhatsNew,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("What's new")
                    }
                    Surface(
                        onClick = { uriHandler.openUri("https://punassuming.github.io/twobits/privacy.html") },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Privacy policy", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "punassuming.github.io/twobits/privacy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
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
private fun ProSubscriptionCard(
    tier: SubscriptionTier,
    isPurchasing: Boolean,
    purchaseError: String?,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
) {
    AppSectionCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Scrybe Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (tier is SubscriptionTier.Pro) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        when (tier) {
            SubscriptionTier.Free -> {
                Text(
                    "Skip the API key — Pro includes managed OpenAI access for transcription and transforms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPurchasing,
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isPurchasing) "Processing…" else "Upgrade to Pro — \$1.99 / month")
                }
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore purchases")
                }
            }
            SubscriptionTier.Pro -> {
                Text(
                    "Managed API keys are active — no personal key required for transcription or AI features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore purchases")
                }
            }
        }
        if (purchaseError != null) {
            Text(purchaseError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError) { Text("Dismiss") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesProminentCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = ScrybeLayoutDefaults.contentMaxWidth),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ScrybeLayoutDefaults.sectionPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Profiles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Pipeline recipes for recording + AI transforms + destinations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppSectionCard(
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
