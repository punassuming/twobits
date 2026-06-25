package com.twobits.design.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** One selectable access mode for a [ProviderCredentialCard] (e.g. Off / BYOK / Pro). */
data class CredentialModeOption(
    val value: String,
    val label: String,
    val color: Color,
    val locked: Boolean = false,
)

/**
 * Generic per-provider credential card: a title, an optional mode segment (any set of modes —
 * Off/BYOK/Pro, Pro/BYOK/Local, etc.), and — when the key-bearing mode is selected — an API-key
 * field with Save / Test / Clear actions and colored validation feedback.
 *
 * When [modes] has fewer than 2 entries the mode segment is hidden and the key field is shown
 * unconditionally (useful for single-mode panels such as Shelf Snap's web-search key panels).
 *
 * Generalizes [AiCredentialsDock] (which is OpenAI-single-key shaped) so apps with several
 * independent providers (PriceDrop) can present each one consistently while reusing the same
 * save/validation behaviour Shelf Snap already has.
 */
@Composable
fun ProviderCredentialCard(
    title: String,
    modes: List<CredentialModeOption> = emptyList(),
    selectedMode: String,
    keyMode: String,
    apiKey: String,
    isValidating: Boolean,
    validationMessage: String?,
    isKeyValid: Boolean?,
    onModeChange: (String) -> Unit = {},
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    modeInfo: Map<String, String> = emptyMap(),
    keyLabel: String = "API key",
    keyHint: String? = null,
) {
    var showKey by rememberSaveable(title) { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (modes.size >= 2) {
                ModeSegment(
                    modes = modes,
                    selected = selectedMode,
                    onChange = onModeChange,
                )
            }

            if (modes.size < 2 || selectedMode == keyMode) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(keyLabel) },
                    supportingText = keyHint?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation =
                        if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !isValidating,
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Save", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Button(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        enabled = apiKey.isNotBlank() && !isValidating,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Clear")
                    }
                    TextButton(
                        onClick = onTest,
                        modifier = Modifier.weight(1f),
                        enabled = apiKey.isNotBlank() && !isValidating,
                    ) {
                        Text("Test")
                    }
                }
                if (validationMessage != null) {
                    Text(
                        text = validationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            when (isKeyValid) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            } else if (modes.size >= 2) {
                val info = modeInfo[selectedMode]
                if (info != null) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A collapsible credential row for a single provider. Connected rows display a masked key and a
 * "Connected" badge; unconfigured rows display the provider [description] and a "Not configured"
 * badge. Tapping the chevron expands to reveal the key field + Save/Clear/Test actions.
 *
 * Add to a [Column] inside a credential card — one row per provider.
 */
@Composable
fun CollapsibleProviderRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    maskedKey: String?,
    isKeyValid: Boolean?,
    isValidating: Boolean,
    validationMessage: String?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    setupHint: String = "",
    signupUrl: String = "",
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val connected = isKeyValid == true && !maskedKey.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            if (connected && !expanded) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = maskedKey!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectedBadge()
            } else if (!expanded) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NotConfiguredBadge()
            } else {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (setupHint.isNotBlank() && isKeyValid != true) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How to get a key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = setupHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (signupUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl)),
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = " Sign up",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                var showKey by rememberSaveable(title) { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API key") },
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isValidating) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Save", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Button(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        enabled = apiKey.isNotBlank() && !isValidating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Clear") }
                    TextButton(onClick = onTest, modifier = Modifier.weight(1f), enabled = apiKey.isNotBlank() && !isValidating) {
                        Text("Test")
                    }
                }
                if (validationMessage != null) {
                    Text(
                        text = validationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (isKeyValid) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedBadge() {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
            Text("Connected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NotConfiguredBadge() {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)) {
        Text(
            text = "Not configured",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ModeSegment(
    modes: List<CredentialModeOption>,
    selected: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        modes.forEach { mode ->
            val isSelected = selected == mode.value
            Surface(
                onClick = { if (!mode.locked) onChange(mode.value) },
                shape = RoundedCornerShape(10.dp),
                color =
                    if (isSelected) {
                        mode.color.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode.locked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp).padding(end = 3.dp),
                        )
                    }
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) mode.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
