package com.shelfsnap.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.VisionModel
import com.twobits.billing.SubscriptionTier

private val PRO_COLOR get() = androidx.compose.ui.graphics.Color(0xFF88D7A8)
private val BYOK_COLOR get() = androidx.compose.ui.graphics.Color(0xFF7DD4DC)

@Composable
fun AIConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.api_key_saved)
    val toggleVisibilityLabel = stringResource(R.string.toggle_key_visibility)

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.onSavedShown()
        }
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CredentialsDock(
                tier = uiState.subscriptionTier,
                apiKey = uiState.editApiKey,
                isVerifying = uiState.isVerifyingKey,
                isVerified = uiState.isKeyVerified,
                verifyError = uiState.keyVerifyError,
                toggleVisibilityLabel = toggleVisibilityLabel,
                onApiKeyChange = viewModel::onApiKeyChange,
                onSave = viewModel::save,
            )

            AiSection(
                icon = Icons.Default.ImageSearch,
                title = "Vision — item identification",
            ) {
                if (uiState.subscriptionTier is SubscriptionTier.Pro) {
                    ProManagedInfo(
                        text = "Managed vision API active — items analysed automatically.",
                    )
                } else {
                    VisionModelPicker(
                        selected = uiState.visionModel,
                        onSelected = viewModel::onVisionModelChange,
                    )
                }
            }

            AiSection(
                icon = Icons.Default.AutoAwesome,
                title = "Pricing & descriptions",
            ) {
                if (uiState.subscriptionTier is SubscriptionTier.Pro) {
                    ProManagedInfo(
                        text = "Managed listing & pricing API active.",
                    )
                } else {
                    ReasoningModelPicker(
                        selected = uiState.reasoningModel,
                        onSelected = viewModel::onReasoningModelChange,
                    )
                }
            }

            AiSection(
                icon = Icons.Default.Insights,
                title = "Analysis",
            ) {
                AiToggleRow(
                    title = stringResource(R.string.auto_analyze_title),
                    subtitle = stringResource(R.string.auto_analyze_subtitle),
                    checked = uiState.autoAnalyze,
                    onCheckedChange = viewModel::onAutoAnalyzeChange,
                )
                HorizontalDivider()
                AiToggleRow(
                    title = stringResource(R.string.keep_photos_title),
                    subtitle = stringResource(R.string.keep_photos_subtitle),
                    checked = uiState.keepPhotos,
                    onCheckedChange = viewModel::onKeepPhotosChange,
                )
            }
        }
    }
}

@Composable
private fun CredentialsDock(
    tier: SubscriptionTier,
    apiKey: String,
    isVerifying: Boolean,
    isVerified: Boolean?,
    verifyError: String?,
    toggleVisibilityLabel: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var proExpanded by rememberSaveable { mutableStateOf(false) }
    var byokExpanded by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column {
            CredentialRow(
                icon = Icons.Default.WorkspacePremium,
                iconTint = PRO_COLOR,
                title = "Pro subscription",
                status = if (tier is SubscriptionTier.Pro) "Active" else "Not subscribed",
                subtitle = if (tier is SubscriptionTier.Pro) "Managed API · no key needed"
                else "\$1.99/mo · tap to expand",
                expanded = proExpanded,
                onToggle = { proExpanded = !proExpanded },
            )
            if (proExpanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                ) {
                    when (tier) {
                        SubscriptionTier.Free -> {
                            Text(
                                "Upgrade to Pro for managed OpenAI access — no personal API key needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SubscriptionTier.Pro -> {
                            Text(
                                "Your Pro subscription is active. Vision and pricing models are managed automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            CredentialRow(
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
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text(stringResource(R.string.api_key_label)) },
                        placeholder = { Text(stringResource(R.string.api_key_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(
                                onClick = { showKey = !showKey },
                                modifier = Modifier.semantics { contentDescription = toggleVisibilityLabel },
                            ) {
                                Text(stringResource(if (showKey) R.string.hide else R.string.show))
                            }
                        }
                    )
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifying,
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.api_key_testing))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                    when (isVerified) {
                        true -> StatusRow(
                            icon = Icons.Default.Check,
                            text = stringResource(R.string.api_key_verified),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        false -> StatusRow(
                            icon = Icons.Default.Close,
                            text = stringResource(
                                R.string.api_key_test_failed,
                                verifyError ?: "Unknown error",
                            ),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    status: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun AiSection(
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
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ProManagedInfo(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PRO_COLOR.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = PRO_COLOR,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VisionModelPicker(
    selected: VisionModel,
    onSelected: (VisionModel) -> Unit,
) {
    Text(
        text = stringResource(R.string.vision_model_section_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    VisionModel.entries.forEach { model ->
        ModelRadioRow(
            name = model.displayName,
            detail = model.supportingText,
            selected = model == selected,
            onClick = { onSelected(model) },
        )
    }
}

@Composable
private fun ReasoningModelPicker(
    selected: com.shelfsnap.app.data.model.ReasoningModel,
    onSelected: (com.shelfsnap.app.data.model.ReasoningModel) -> Unit,
) {
    Text(
        text = stringResource(R.string.reasoning_model_section_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    com.shelfsnap.app.data.model.ReasoningModel.entries.forEach { model ->
        ModelRadioRow(
            name = model.displayName,
            detail = model.supportingText,
            selected = model == selected,
            onClick = { onSelected(model) },
        )
    }
}

@Composable
private fun ModelRadioRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
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
        modifier = Modifier
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
