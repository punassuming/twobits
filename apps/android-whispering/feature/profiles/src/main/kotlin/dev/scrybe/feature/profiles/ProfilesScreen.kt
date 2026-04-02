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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.TransformProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestionState by viewModel.suggestionState.collectAsState()
    val profileSuggestionModel by viewModel.profileSuggestionModel.collectAsState()
    var editorDraft by remember { mutableStateOf<ProfileEditorDraft?>(null) }
    var showAiCreator by remember { mutableStateOf(false) }

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
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ProfileCreationCard(
                            onCreateManual = {
                                viewModel.clearSuggestionState()
                                editorDraft = ProfileEditorDraft()
                            },
                            onCreateWithAi = {
                                viewModel.clearSuggestionState()
                                showAiCreator = true
                            },
                        )
                    }
                    if (state.profiles.isEmpty()) {
                        item {
                            EmptyProfilesCard()
                        }
                    } else {
                        items(state.profiles) { profile ->
                            ProfileRow(
                                profile = profile,
                                onEdit = {
                                    viewModel.clearSuggestionState()
                                    editorDraft = profile.toDraft()
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

    if (editorDraft != null) {
        ProfileEditorDialog(
            draft = editorDraft!!,
            onDismiss = {
                editorDraft = null
            },
            onSave = { id, name, description, steps, isDefault ->
                viewModel.saveProfile(id, name, description, steps, isDefault)
                editorDraft = null
            },
        )
    }

    if (showAiCreator) {
        AiProfileDraftDialog(
            selectedModelName = profileSuggestionModel,
            suggestionState = suggestionState,
            onDismiss = {
                viewModel.clearSuggestionState()
                showAiCreator = false
            },
            onSuggest = viewModel::suggestProfile,
            onSuggestionConsumed = viewModel::clearSuggestionState,
            onSaveSuggestion = { suggestion, isDefault ->
                viewModel.saveProfile(
                    existingId = null,
                    name = suggestion.name,
                    description = suggestion.description,
                    steps = suggestion.steps,
                    setAsDefault = isDefault,
                )
                viewModel.clearSuggestionState()
                showAiCreator = false
            },
            onEditSuggestion = { suggestion, isDefault ->
                editorDraft =
                    ProfileEditorDraft(
                        existingId = null,
                        name = suggestion.name,
                        description = suggestion.description,
                        steps = suggestion.steps,
                        isDefault = isDefault,
                    )
                viewModel.clearSuggestionState()
                showAiCreator = false
            },
        )
    }
}

private data class ProfileEditorDraft(
    val existingId: String? = null,
    val name: String = "",
    val description: String = "",
    val steps: List<String> = listOf(""),
    val isDefault: Boolean = false,
)

private fun TransformProfile.toDraft(): ProfileEditorDraft =
    ProfileEditorDraft(
        existingId = id,
        name = name,
        description = description,
        steps = steps.ifEmpty { listOf(systemPrompt) },
        isDefault = isDefault,
    )

@Composable
private fun ProfileCreationCard(
    onCreateManual: () -> Unit,
    onCreateWithAi: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create a profile",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Build one manually or let AI draft a starting point, then refine it before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onCreateManual,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Profile")
                }
                OutlinedButton(
                    onClick = onCreateWithAi,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("AI Draft")
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No profiles configured",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Profiles let Scrybe run one to three prompt steps against a transcript after recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    draft: ProfileEditorDraft,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, List<String>, Boolean) -> Unit,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var description by remember(draft) { mutableStateOf(draft.description) }
    var steps by remember(draft) {
        mutableStateOf(draft.steps.ifEmpty { listOf("") })
    }
    var isDefault by remember(draft) { mutableStateOf(draft.isDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.existingId == null) "New Profile" else "Edit Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Prompt inputs",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "Step 1 should usually use {{transcript}}. Bulk consolidation transforms can also use {{combined_transcripts}}. Later steps can use {{current_text}} or {{prior_output}} to build on earlier output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                steps.forEachIndexed { index, step ->
                    OutlinedTextField(
                        value = step,
                        onValueChange = { next ->
                            steps = steps.toMutableList().also { it[index] = next }
                        },
                        label = { Text("Step ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        supportingText = {
                            Text(
                                "Use {{transcript}} for the original transcription, {{combined_transcripts}} for multi-recording consolidation, and {{prior_output}} or {{current_text}} for previous-step output.",
                            )
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    onSave(draft.existingId, name, description, steps, isDefault)
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

@Composable
private fun AiProfileDraftDialog(
    selectedModelName: String,
    suggestionState: ProfileSuggestionUiState,
    onDismiss: () -> Unit,
    onSuggest: (String, String, String, List<String>) -> Unit,
    onSuggestionConsumed: () -> Unit,
    onSaveSuggestion: (dev.scrybe.core.transforms.ProfileSuggestion, Boolean) -> Unit,
    onEditSuggestion: (dev.scrybe.core.transforms.ProfileSuggestion, Boolean) -> Unit,
) {
    var request by remember { mutableStateOf("") }
    var seedName by remember { mutableStateOf("") }
    var seedDescription by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var latestSuggestion by remember { mutableStateOf<dev.scrybe.core.transforms.ProfileSuggestion?>(null) }
    val selectedModel = OpenAiProfileSuggestionModel.fromApiName(selectedModelName)

    LaunchedEffect(suggestionState) {
        val success = suggestionState as? ProfileSuggestionUiState.Success ?: return@LaunchedEffect
        latestSuggestion = success.suggestion
        onSuggestionConsumed()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Profile Draft") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Psychology, contentDescription = null)
                            Text(
                                text = "Drafted with OpenAI ${selectedModel.apiName}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            text = "The AI creates a 1-3 step starting profile. Saved steps still run through Scrybe's normal transform pipeline using {{transcript}} or {{combined_transcripts}} first, then {{current_text}} or {{prior_output}}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Uses ${selectedModel.title}. Change or test this model from Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = selectedModel.supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    label = { Text("What should this profile produce?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    supportingText = {
                        Text("Example: turn meeting transcripts into action items with owners and due dates.")
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = seedName,
                        onValueChange = { seedName = it },
                        label = { Text("Seed name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = seedDescription,
                        onValueChange = { seedDescription = it },
                        label = { Text("Seed description") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                if (suggestionState is ProfileSuggestionUiState.Error) {
                    Text(
                        text = suggestionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                latestSuggestion?.let { suggestion ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = suggestion.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            suggestion.steps.forEachIndexed { index, step ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Step ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                latestSuggestion?.let { suggestion ->
                    TextButton(onClick = { onEditSuggestion(suggestion, isDefault) }) {
                        Text("Edit Draft")
                    }
                    Button(onClick = { onSaveSuggestion(suggestion, isDefault) }) {
                        Text("Create Profile")
                    }
                } ?: Button(
                    onClick = {
                        onSuggest(
                            request,
                            seedName,
                            seedDescription,
                            emptyList(),
                        )
                    },
                    enabled = suggestionState !is ProfileSuggestionUiState.Loading && request.isNotBlank(),
                ) {
                    Text(
                        if (suggestionState is ProfileSuggestionUiState.Loading) {
                            "Generating..."
                        } else {
                            "Generate Draft"
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
