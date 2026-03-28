package dev.scrybe.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.model.TransformProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestionState by viewModel.suggestionState.collectAsState()
    var editorProfile by remember { mutableStateOf<TransformProfile?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editorProfile = null
                isCreating = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add profile")
            }
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is ProfilesUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            is ProfilesUiState.Error ->
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            is ProfilesUiState.Success -> {
                if (state.profiles.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No profiles configured")
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.profiles) { profile ->
                            ProfileRow(
                                profile = profile,
                                onEdit = {
                                    editorProfile = profile
                                    isCreating = false
                                },
                                onDelete = { viewModel.deleteProfile(profile.id) },
                                onSetDefault = { viewModel.setDefaultProfile(profile.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (isCreating || editorProfile != null) {
        ProfileEditorDialog(
            profile = editorProfile,
            onDismiss = {
                viewModel.clearSuggestionState()
                editorProfile = null
                isCreating = false
            },
            onSave = { id, name, description, steps, isDefault ->
                viewModel.saveProfile(id, name, description, steps, isDefault)
                viewModel.clearSuggestionState()
                editorProfile = null
                isCreating = false
            },
            suggestionState = suggestionState,
            onSuggestionConsumed = viewModel::clearSuggestionState,
            onSuggest = viewModel::suggestProfile,
        )
    }
}

@Composable
private fun ProfileRow(
    profile: TransformProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile.isDefault) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Default profile",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Default",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onSetDefault,
                        enabled = !profile.isDefault,
                    ) {
                        Icon(
                            imageVector = if (profile.isDefault) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (profile.isDefault) "Default profile" else "Make default",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit profile")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete profile",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                text = "Prompt preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = profile.systemPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.steps.size} step${if (profile.steps.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    profile: TransformProfile?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, List<String>, Boolean) -> Unit,
    suggestionState: ProfileSuggestionUiState,
    onSuggestionConsumed: () -> Unit,
    onSuggest: (String, String, String, List<String>) -> Unit,
) {
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var description by remember(profile) { mutableStateOf(profile?.description.orEmpty()) }
    var steps by remember(profile) {
        mutableStateOf(profile?.steps?.ifEmpty { listOf("") } ?: listOf(""))
    }
    var isDefault by remember(profile) { mutableStateOf(profile?.isDefault == true) }
    var assistantRequest by remember(profile) { mutableStateOf(profile?.description.orEmpty()) }

    LaunchedEffect(suggestionState) {
        val success = suggestionState as? ProfileSuggestionUiState.Success ?: return@LaunchedEffect
        name = success.suggestion.name
        description = success.suggestion.description
        steps = success.suggestion.steps
        onSuggestionConsumed()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "New Profile" else "Edit Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = assistantRequest,
                    onValueChange = { assistantRequest = it },
                    label = { Text("Ask AI to suggest a profile") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    supportingText = {
                        Text(
                            "Describe the output you want. Scrybe can transcribe audio and run 1-3 prompt steps using {{transcript}} plus {{current_text}} or {{prior_output}}.",
                        )
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            onSuggest(
                                assistantRequest,
                                name,
                                description,
                                steps,
                            )
                        },
                        enabled = suggestionState !is ProfileSuggestionUiState.Loading,
                    ) {
                        Text(
                            if (suggestionState is ProfileSuggestionUiState.Loading) {
                                "Suggesting..."
                            } else {
                                "Suggest With AI"
                            },
                        )
                    }
                }
                if (suggestionState is ProfileSuggestionUiState.Error) {
                    Text(
                        text = suggestionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                steps.forEachIndexed { index, step ->
                    OutlinedTextField(
                        value = step,
                        onValueChange = { next ->
                            steps = steps.toMutableList().also { it[index] = next }
                        },
                        label = { Text("Step ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        supportingText = {
                            Text(
                                "Use {{transcript}} for the original transcription and {{prior_output}} or {{current_text}} for the previous step output.",
                            )
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { steps = steps + "" },
                    ) {
                        Text("Add Step")
                    }
                    if (steps.size > 1) {
                        TextButton(
                            onClick = { steps = steps.dropLast(1) },
                        ) {
                            Text("Remove Last")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Use as default")
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(profile?.id, name, description, steps, isDefault)
                },
                enabled = name.isNotBlank() && steps.any { it.isNotBlank() },
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
