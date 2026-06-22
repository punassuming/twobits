package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AI_BYOK_COLOR
import com.twobits.design.components.AI_PRO_COLOR
import com.twobits.design.components.CredentialModeOption
import com.twobits.design.components.ProviderCredentialCard
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val providerStates by viewModel.providerStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard2("API endpoint") {
                    ListItem(
                        headlineContent = { Text("Pro base URL") },
                        supportingContent = {
                            Text(uiState.apiBaseUrl, style = MaterialTheme.typography.bodySmall)
                        },
                    )
                }
            }
            item {
                SectionCard2("Providers") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
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
            }
        }
    }
}

@Composable
private fun ProviderCredentialItem(
    provider: PriceDropProvider,
    state: ProviderState,
    viewModel: SettingsViewModel,
) {
    // Local draft so typing doesn't persist on every keystroke; Save commits it.
    var draft by remember(provider) { mutableStateOf(state.key) }
    LaunchedEffect(state.key) { if (draft != state.key) draft = state.key }

    ProviderCredentialCard(
        title = provider.displayName,
        modes = PRICEDROP_MODES,
        selectedMode = state.mode.value,
        keyMode = ProviderMode.BYOK.value,
        apiKey = draft,
        isValidating = state.isValidating,
        validationMessage = state.validationMessage,
        isKeyValid = state.isKeyValid,
        onModeChange = { viewModel.setProviderMode(provider, ProviderMode.fromValue(it)) },
        onApiKeyChange = { draft = it },
        onSave = { viewModel.setProviderKey(provider, draft) },
        onTest = { viewModel.testProviderKey(provider, draft) },
        onClear = {
            draft = ""
            viewModel.clearProviderKey(provider)
        },
        modeInfo = PRICEDROP_MODE_INFO,
    )
}

private val PRICEDROP_MODES =
    listOf(
        CredentialModeOption(ProviderMode.OFF.value, "Off", Color.Gray),
        CredentialModeOption(ProviderMode.BYOK.value, "BYOK", AI_BYOK_COLOR),
        CredentialModeOption(ProviderMode.PRO.value, "Pro", AI_PRO_COLOR),
    )

private val PRICEDROP_MODE_INFO =
    mapOf(
        ProviderMode.PRO.value to "Using TwoBits managed provider — no key needed (requires PriceDrop Pro).",
        ProviderMode.OFF.value to "This provider is disabled.",
    )

@Composable
private fun SectionCard2(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            content()
        }
    }
}
