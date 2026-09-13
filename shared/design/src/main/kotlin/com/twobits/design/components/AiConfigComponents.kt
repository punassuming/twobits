package com.twobits.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
val AI_PRO_COLOR = Color(0xFF88D7A8)
val AI_BYOK_COLOR = Color(0xFF7DD4DC)
val AI_LOCAL_COLOR = Color(0xFFFFB695)

@Composable
fun AiCredentialsDock(
    proLabel: String,
    proPrice: String,
    hasPro: Boolean,
    apiKey: String,
    isValidating: Boolean,
    validationMessage: String?,
    isKeyValid: Boolean?,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
    onUpgrade: () -> Unit,
    byokColor: Color = AI_BYOK_COLOR,
    modifier: Modifier = Modifier,
) {
    var proExpanded by rememberSaveable { mutableStateOf(false) }
    var byokExpanded by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    val maskedKey = if (apiKey.length > 8) "sk-…${apiKey.takeLast(4)}" else null

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column {
            AiCredentialRow(
                icon = Icons.Default.WorkspacePremium,
                iconTint = AI_PRO_COLOR,
                title = proLabel,
                status = if (hasPro) "Active" else "Not subscribed",
                subtitle = if (hasPro) "Managed API · no key needed" else "$proPrice · tap to expand",
                subtitleMonospace = false,
                expanded = proExpanded,
                onToggle = { proExpanded = !proExpanded },
            )
            if (proExpanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (hasPro)
                            "Your $proLabel subscription is active. AI features are managed automatically."
                        else
                            "Upgrade to $proLabel for managed AI access — no personal API key needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!hasPro) {
                        Button(
                            onClick = onUpgrade,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AI_PRO_COLOR,
                                contentColor = Color(0xFF1A3A2A),
                            ),
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Upgrade to $proLabel", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            AiCredentialRow(
                icon = Icons.Default.Key,
                iconTint = byokColor,
                title = "OpenAI API key",
                status = if (apiKey.isNotBlank()) "Connected" else "Not configured",
                subtitle = maskedKey ?: "Bring your own key",
                subtitleMonospace = maskedKey != null,
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
                        label = { Text("OpenAI API key") },
                        placeholder = { Text("sk-…") },
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
                            colors = ButtonDefaults.buttonColors(
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
}

@Composable
private fun AiCredentialRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    status: String,
    subtitle: String,
    subtitleMonospace: Boolean,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.18f)) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = if (subtitleMonospace)
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                else
                    MaterialTheme.typography.bodySmall,
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
fun AiSourceSegment(
    selected: String,
    hasPro: Boolean,
    hasLocal: Boolean = true,
    onChange: (String) -> Unit,
    proColor: Color = AI_PRO_COLOR,
    byokColor: Color = AI_BYOK_COLOR,
    localColor: Color = AI_LOCAL_COLOR,
    modifier: Modifier = Modifier,
) {
    val pills = buildList {
        add(Triple("pro", "Pro", proColor))
        add(Triple("byok", "BYOK", byokColor))
        if (hasLocal) add(Triple("local", "Local", localColor))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pills.forEach { (value, label, color) ->
            val isSelected = selected == value
            val locked = value == "pro" && !hasPro
            Surface(
                onClick = { if (!locked) onChange(value) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (locked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp).padding(end = 3.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AiProManagedCard(
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AI_PRO_COLOR.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = AI_PRO_COLOR,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun AiNoKeyWarning(
    modifier: Modifier = Modifier,
    text: String = "No API key configured. Add your OpenAI key in the credentials panel above.",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
fun AiSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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
}

/**
 * An [AiSectionHeader] above an elevated card — the standard AI-configuration-screen section
 * shape, distinct from [AppLabeledSectionCard]'s flat card (regular Settings screens use that
 * one). Was previously hand-rolled identically as a private wrapper in each app's AI config
 * screen; call this directly instead.
 */
@Composable
fun AiSectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
