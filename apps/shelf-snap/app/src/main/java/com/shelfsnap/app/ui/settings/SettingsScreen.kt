package com.shelfsnap.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.BuildConfig
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.ReasoningModel
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.twobits.billing.SubscriptionTier

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onWhatsNew: () -> Unit = {},
    onAiConfig: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val searchSavedMessage = stringResource(R.string.search_settings_saved)
    val toggleVisibilityLabel = stringResource(R.string.toggle_key_visibility)

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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProSubscriptionCard(
                tier = uiState.subscriptionTier,
                isPurchasing = uiState.isPurchasing,
                purchaseError = uiState.purchaseError,
                onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                onRestore = viewModel::restorePurchases,
                onDismissError = viewModel::dismissPurchaseError,
            )

            AiConfigCard(onClick = onAiConfig)

            // ── Web search ─────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.Search,
                title = stringResource(R.string.search_section_title),
            ) {
                Text(
                    text = stringResource(R.string.search_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SearchProviderDropdown(
                    selected = uiState.searchProvider,
                    onSelected = viewModel::onSearchProviderChange
                )
                if (uiState.searchProvider == SearchProvider.BRAVE) {
                    var showSearchKey by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = uiState.editSearchApiKey,
                        onValueChange = viewModel::onSearchApiKeyChange,
                        label = { Text(stringResource(R.string.search_api_key_label)) },
                        supportingText = { Text(stringResource(R.string.search_api_key_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showSearchKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(
                                onClick = { showSearchKey = !showSearchKey },
                                modifier = Modifier.semantics { contentDescription = toggleVisibilityLabel }
                            ) {
                                Text(stringResource(if (showSearchKey) R.string.hide else R.string.show))
                            }
                        }
                    )
                    Button(
                        onClick = viewModel::saveSearchSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }

            // ── Capture & storage ──────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.PhotoCamera,
                title = stringResource(R.string.capture_storage_section),
            ) {
                SettingToggle(
                    title = stringResource(R.string.auto_analyze_title),
                    subtitle = stringResource(R.string.auto_analyze_subtitle),
                    checked = uiState.autoAnalyze,
                    onCheckedChange = viewModel::onAutoAnalyzeChange
                )
                SettingToggle(
                    title = stringResource(R.string.keep_photos_title),
                    subtitle = stringResource(R.string.keep_photos_subtitle),
                    checked = uiState.keepPhotos,
                    onCheckedChange = viewModel::onKeepPhotosChange
                )
                StorageBreakdownCard(storage = uiState.storage)
            }

            // ── About ──────────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about_section),
            ) {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    onClick = onWhatsNew,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.whats_new_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    onClick = { uriHandler.openUri("https://punassuming.github.io/twobits/privacy.html") },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.privacy_policy_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiConfigCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI configuration",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vision model · pricing · local models · API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
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
    ElevatedCard(shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Shelf Snap Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (tier is SubscriptionTier.Pro) {
                    Surface(
                        shape = RoundedCornerShape(50),
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
                        "Skip the API key — Pro includes managed OpenAI access so vision analysis and price research work out of the box.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPurchasing,
                    ) {
                        if (isPurchasing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isPurchasing) "Processing…" else "Upgrade to Pro — $1.99 / month")
                    }
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore purchases")
                    }
                }
                SubscriptionTier.Pro -> {
                    Text(
                        "You have Pro. Managed API keys are active — no personal key required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore purchases")
                    }
                }
            }
            if (purchaseError != null) {
                Text(
                    purchaseError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StorageBreakdownCard(storage: StorageInfo) {
    val total = storage.totalBytes.coerceAtLeast(1L)
    val photoFraction = (storage.photosBytes.toFloat() / total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.storage_usage),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = photoFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.storage_photos) + " · " + formatBytes(storage.photosBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.storage_database) + " · " + formatBytes(storage.dbBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.storage_total, formatBytes(storage.totalBytes)),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Formats a byte count as a compact human-readable size (B/KB/MB/GB). */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisionModelDropdown(
    selected: VisionModel,
    onSelected: (VisionModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.vision_model_label)) },
            supportingText = { Text(selected.supportingText) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VisionModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName)
                            Text(
                                text = model.supportingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchProviderDropdown(
    selected: SearchProvider,
    onSelected: (SearchProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_provider_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasoningModelDropdown(
    selected: ReasoningModel,
    onSelected: (ReasoningModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.reasoning_model_label)) },
            supportingText = { Text(selected.supportingText) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReasoningModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName)
                            Text(
                                text = model.supportingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}
