package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.twobits.design.components.CollapsibleProviderRow
import com.twobits.design.components.CredentialRequirement
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.PriceDropProvider

/** True only for OpenAI — the sole provider shown in AI Configuration → Credentials. All other
 *  providers are supporting search/shopping/enrichment services shown in Settings → Services,
 *  mirroring Shelf Snap's Credentials/Services split. */
internal fun PriceDropProvider.isAiModelProvider(): Boolean = this == PriceDropProvider.OPENAI

internal fun PriceDropProvider.icon(): ImageVector =
    when (this) {
        PriceDropProvider.OPENAI -> Icons.Filled.AutoAwesome
        PriceDropProvider.WEB_SEARCH -> Icons.Filled.Search
        PriceDropProvider.SHOPPING -> Icons.Filled.ShoppingCart
        PriceDropProvider.SERPER -> Icons.Filled.ShoppingCart
        PriceDropProvider.FIRECRAWL -> Icons.Filled.Public
        PriceDropProvider.RAINFOREST -> Icons.Filled.Park
    }

/**
 * Overall importance of this provider's key across the whole app (not one feature) — see each
 * provider's [PriceDropProvider.description] for exactly what breaks vs degrades without it.
 */
internal fun PriceDropProvider.requirement(): CredentialRequirement =
    when (this) {
        PriceDropProvider.OPENAI -> CredentialRequirement.REQUIRED
        PriceDropProvider.RAINFOREST -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.WEB_SEARCH -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.SHOPPING -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.SERPER -> CredentialRequirement.RECOMMENDED
        PriceDropProvider.FIRECRAWL -> CredentialRequirement.OPTIONAL
    }

/** Where a feature's providers' keys are managed — "Credentials" (AI Config), "Services"
 *  (Settings), or both, depending which of [AiFeature.providers] it lists. */
internal fun providerKeyLocation(feature: AiFeature): String {
    val locations = feature.providers.map { if (it.isAiModelProvider()) "Credentials" else "Services" }.distinct()
    return when (locations.size) {
        0 -> "Credentials"
        1 -> locations.first()
        else -> "Credentials or Services"
    }
}

/** Copy for the missing-key banner shown in a BYOK feature's detail view when none of its
 *  enabled providers has a valid key — names the actual missing provider(s) rather than always
 *  saying "OpenAI," since a feature like [AiFeature.SEARCH] can depend on Jina/SearchAPI.io/
 *  Serper.dev instead. */
internal fun noKeyMessage(feature: AiFeature): String {
    val names = feature.providers.joinToString(" or ") { it.displayName }
    return "No API key configured for $names. Add one in ${providerKeyLocation(feature)}."
}

/** One provider's credential row — shared by AI Configuration → Credentials (OpenAI only) and
 *  Settings → Services (every other provider). */
@Composable
internal fun ProviderCredentialItem(
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
