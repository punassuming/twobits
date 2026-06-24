package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.scrybe.core.model.RecordingMode

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSaveApiKey: (String) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf<RecordingMode?>(null) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingTopBar(step = step, onBack = { step-- }, onSkip = onComplete)
            Spacer(Modifier.height(12.dp))
            StepDots(currentStep = step, totalSteps = 4)
            Spacer(Modifier.height(28.dp))
            when (step) {
                0 -> OnboardingWelcomeStep(onNext = { step = 1 })
                1 ->
                    OnboardingPickModeStep(
                        selected = selectedMode,
                        onSelect = { selectedMode = it },
                        onNext = { step = 2 },
                    )
                2 ->
                    OnboardingSetupStep(
                        onSaveApiKey = onSaveApiKey,
                        onNext = { step = 3 },
                    )
                else -> OnboardingPipelinesStep(onComplete = onComplete)
            }
        }
    }
}

@Composable
private fun StepDots(
    currentStep: Int,
    totalSteps: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { i ->
            val w = if (i == currentStep) 22.dp else 5.dp
            val color =
                when {
                    i == currentStep -> MaterialTheme.colorScheme.primary
                    i < currentStep -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            Box(
                modifier =
                    Modifier
                        .height(5.dp)
                        .width(w)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    step: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        if (step in 1..2) {
            TextButton(onClick = onSkip) {
                Text("Skip", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OnboardingHero() {
    val transition = rememberInfiniteTransition(label = "hero-pulse")
    val outerScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "outerScale",
    )
    val midScale by transition.animateFloat(
        initialValue = 1.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "midScale",
    )
    val primary = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scale(outerScale)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .size(112.dp)
                    .scale(midScale)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.32f)),
        )
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OnboardingHero()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Your voice, into memory",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Scrybe turns your recordings into structured notes and action items.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
    }
}

@Composable
private fun OnboardingPickModeStep(
    selected: RecordingMode?,
    onSelect: (RecordingMode) -> Unit,
    onNext: () -> Unit,
) {
    val modes = listOf(RecordingMode.MEETING, RecordingMode.IDEA, RecordingMode.TASKS, RecordingMode.JOURNAL)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Pick a mode to start", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Text(
            "The mode shapes how your audio becomes notes. You can change it any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingModeCard(modes[0], selected == modes[0], { onSelect(modes[0]) }, Modifier.weight(1f))
                OnboardingModeCard(modes[1], selected == modes[1], { onSelect(modes[1]) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingModeCard(modes[2], selected == modes[2], { onSelect(modes[2]) }, Modifier.weight(1f))
                OnboardingModeCard(modes[3], selected == modes[3], { onSelect(modes[3]) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "You can build your own modes later with AI templates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(enabled = selected != null, onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun OnboardingModeCard(
    mode: RecordingMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = onboardingModeAccentColor(mode, colors)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) accent.copy(alpha = 0.12f) else colors.surfaceContainerHigh,
            ),
        border = if (isSelected) BorderStroke(1.5.dp, accent.copy(alpha = 0.33f)) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(onboardingModeIcon(mode), null, modifier = Modifier.size(20.dp), tint = accent)
            Text(mode.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                mode.outputDescription,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingSetupStep(
    onSaveApiKey: (String) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var apiKey by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Quick setup", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow microphone access and optionally add your API key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        MicPermissionCard(isGranted = micGranted, onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
        Spacer(Modifier.height(8.dp))
        ApiKeyCard(apiKey = apiKey, onChange = { apiKey = it })
        Spacer(Modifier.weight(1f))
        Button(
            enabled = micGranted,
            onClick = {
                onSaveApiKey(apiKey)
                onNext()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (micGranted) "Continue" else "Grant mic access first")
        }
    }
}

@Composable
private fun MicPermissionCard(
    isGranted: Boolean,
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Mic,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Microphone access", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "Required to record audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isGranted) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            } else {
                TextButton(onClick = onRequest) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: String,
    onChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.VpnKey,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text("OpenAI API key", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-…", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Filled.Lock,
                    null,
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Stored encrypted on this device only",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPipelinesStep(onComplete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "One-tap pipelines",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Community-built workflows that turn recordings into structured output.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OnboardingPipelineCard("Meeting notes", "Action items + summary", 1247)
        Spacer(Modifier.height(8.dp))
        OnboardingPipelineCard("Daily journal", "Plain transcript + reflection", 834)
        Spacer(Modifier.weight(1f))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Start using Scrybe")
        }
    }
}

@Composable
private fun OnboardingPipelineCard(
    name: String,
    description: String,
    installs: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "→ $description",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$installs installs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun onboardingModeAccentColor(
    mode: RecordingMode,
    colors: ColorScheme,
): Color =
    when (mode) {
        RecordingMode.MEETING -> colors.primary
        RecordingMode.IDEA -> colors.tertiary
        RecordingMode.TASKS -> colors.secondary
        RecordingMode.CONVERSATION -> colors.primary
        RecordingMode.STORY -> colors.tertiary
        RecordingMode.INTERVIEW -> colors.secondary
        RecordingMode.JOURNAL -> colors.onSurfaceVariant
        RecordingMode.CUSTOM -> colors.secondary
    }

private fun onboardingModeIcon(mode: RecordingMode): ImageVector =
    when (mode) {
        RecordingMode.MEETING -> Icons.Filled.AutoAwesome
        RecordingMode.IDEA -> Icons.Filled.AutoAwesome
        RecordingMode.TASKS -> Icons.Filled.Check
        RecordingMode.CONVERSATION -> Icons.Filled.AutoAwesome
        RecordingMode.STORY -> Icons.Filled.AutoAwesome
        RecordingMode.INTERVIEW -> Icons.Filled.AutoAwesome
        RecordingMode.JOURNAL -> Icons.Filled.AutoAwesome
        RecordingMode.CUSTOM -> Icons.Filled.AutoAwesome
    }
