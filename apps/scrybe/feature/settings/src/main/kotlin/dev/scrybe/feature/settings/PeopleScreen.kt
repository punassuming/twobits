package dev.scrybe.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.database.PersonEntity
import kotlinx.coroutines.launch

private val PERSON_AVATAR_COLORS =
    listOf(
        Color(0xFF4A90D9),
        Color(0xFF27AE60),
        Color(0xFFE67E22),
        Color(0xFF8E44AD),
        Color(0xFF16A085),
        Color(0xFFE74C3C),
    )

private fun personAvatarColor(id: String): Color = PERSON_AVATAR_COLORS[Math.abs(id.hashCode()) % PERSON_AVATAR_COLORS.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val persons by viewModel.persons.collectAsState()
    val scope = rememberCoroutineScope()

    var renameTarget by remember { mutableStateOf<PersonEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PersonEntity?>(null) }
    var showReIdentifyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("People")
                        if (persons.isNotEmpty()) {
                            Text(
                                text = "${persons.size} speaker${if (persons.size != 1) "s" else ""} identified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showReIdentifyDialog = true }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Re-identify")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (persons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No people yet — they're created when you assign a name to a speaker in a session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(persons, key = { it.id }) { person ->
                    var sessionCount by remember { mutableIntStateOf(0) }
                    var segmentCount by remember { mutableIntStateOf(0) }
                    var talkRatio by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(person.id) {
                        sessionCount = viewModel.sessionCountForPerson(person.id)
                        segmentCount = viewModel.segmentCountForPerson(person.id)
                        talkRatio = viewModel.talkRatioForPerson(person.id)
                    }
                    PersonCard(
                        person = person,
                        sessionCount = sessionCount,
                        segmentCount = segmentCount,
                        talkRatio = talkRatio,
                        otherPersons = persons.filter { it.id != person.id },
                        onRename = { renameTarget = person },
                        onMergeInto = { targetId ->
                            scope.launch { viewModel.mergePersons(person.id, targetId) }
                        },
                        onDelete = { deleteTarget = person },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ⓘ  Re-identify re-runs speaker diarization across all sessions using the current model. Existing manual renames and merges are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    renameTarget?.let { person ->
        RenamePersonDialog(
            currentName = person.name,
            onConfirm = { newName ->
                viewModel.renamePerson(person.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { person ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${person.name}?") },
            text = { Text("Speaker assignments for this person will be removed from all sessions.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerson(person.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showReIdentifyDialog) {
        AlertDialog(
            onDismissRequest = { showReIdentifyDialog = false },
            title = { Text("Re-identify speakers?") },
            text = {
                Text(
                    "Re-runs speaker diarization across all sessions using the current model. " +
                        "Existing manual renames and merges are preserved.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reIdentifyAll()
                    showReIdentifyDialog = false
                }) {
                    Text("Re-identify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReIdentifyDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PersonCard(
    person: PersonEntity,
    sessionCount: Int,
    segmentCount: Int,
    talkRatio: Float,
    otherPersons: List<PersonEntity>,
    onRename: () -> Unit,
    onMergeInto: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMergePicker by remember { mutableStateOf(false) }
    val avatarColor = personAvatarColor(person.id)
    val initial =
        person.name
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?"
    val talkPercent = (talkRatio * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = talkRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "talkProgress",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Name + edit icon
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Merge + Delete buttons
                if (otherPersons.isNotEmpty()) {
                    IconButton(onClick = { showMergePicker = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.MergeType,
                            contentDescription = "Merge",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatLabel(value = sessionCount.toString(), unit = if (sessionCount == 1) "session" else "sessions")
                StatLabel(value = "$talkPercent%", unit = "talk time")
                StatLabel(value = segmentCount.toString(), unit = "segments")
            }

            Spacer(Modifier.height(8.dp))

            // Talk-time progress bar colored by avatar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = avatarColor,
                trackColor = avatarColor.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
        }
    }

    if (showMergePicker) {
        MergePersonDialog(
            candidates = otherPersons,
            onConfirm = { targetId ->
                showMergePicker = false
                onMergeInto(targetId)
            },
            onDismiss = { showMergePicker = false },
        )
    }
}

@Composable
private fun StatLabel(
    value: String,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenamePersonDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename person") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MergePersonDialog(
    candidates: List<PersonEntity>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge into…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "All speaker assignments will move to the selected person.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = selected == candidate.id,
                            onClick = { selected = candidate.id },
                        )
                        Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected.isNotEmpty()) onConfirm(selected) },
                enabled = selected.isNotEmpty(),
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
