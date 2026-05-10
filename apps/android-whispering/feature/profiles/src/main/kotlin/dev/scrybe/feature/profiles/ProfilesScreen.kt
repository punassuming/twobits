package dev.scrybe.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTransformModel
import dev.scrybe.core.model.ProviderType
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
    val editorDraft by viewModel.editorDraft.collectAsState()
    val aiCreatorOpen by viewModel.aiCreatorOpen.collectAsState()

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
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = ScrybeLayoutDefaults.screenHorizontalPadding,
                            vertical = 12.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        ProfileCreationCard(
                            onCreateManual = {
                                viewModel.clearSuggestionState()
                                viewModel.openNewEditor()
                            },
                            onCreateWithAi = {
                                viewModel.clearSuggestionState()
                                viewModel.openAiCreator()
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
                                    viewModel.openEditor(profile)
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

    editorDraft?.let { draft ->
        ProfileEditorDialog(
            draft = draft,
            onUpdate = { viewModel.updateEditorDraft(it) },
            onDismiss = { viewModel.closeEditor() },
            onSave = { id, name, description, steps, isDefault, providerType, modelName ->
                viewModel.saveProfile(id, name, description, steps, isDefault, providerType, modelName)
                viewModel.closeEditor()
            },
        )
    }

    if (aiCreatorOpen) {
        AiProfileDraftDialog(
            selectedModelName = profileSuggestionModel,
            suggestionState = suggestionState,
            onDismiss = {
                viewModel.clearSuggestionState()
                viewModel.closeAiCreator()
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
                viewModel.closeAiCreator()
            },
            onEditSuggestion = { suggestion, isDefault ->
                viewModel.updateEditorDraft(
                    ProfileEditorDraft(
                        existingId = null,
                        name = suggestion.name,
                        description = suggestion.description,
                        steps = suggestion.steps,
                        isDefault = isDefault,
                    ),
                )
                viewModel.clearSuggestionState()
                viewModel.closeAiCreator()
            },
        )
    }
}

@Composable
private fun ProfileCreationCard(
    onCreateManual: () -> Unit,
    onCreateWithAi: () -> Unit,
) {
    ScrybeSectionCard {
        ScrybeSectionHeader(
            title = "Create a profile",
            subtitle = "Build one manually or let AI draft a starting point, then refine it before saving.",
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
                Text("New Profile", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onCreateWithAi,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("AI Draft", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyProfilesCard() {
    ScrybeSectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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

@Composable
private fun ProfileRow(
    profile: TransformProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    ScrybeSectionCard {
        Column(
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
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    onUpdate: (ProfileEditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, List<String>, Boolean, ProviderType, String?) -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (draft.existingId == null) "New Profile" else "Edit Profile",
                    style = MaterialTheme.typography.headlineSmall,
                )
                ProfileEditorFormBody(
                    draft = draft,
                    onUpdate = onUpdate,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                draft.existingId,
                                draft.name,
                                draft.description,
                                draft.steps,
                                draft.isDefault,
                                draft.providerType,
                                draft.modelName,
                            )
                        },
                        enabled = draft.name.isNotBlank() && draft.steps.any { it.isNotBlank() },
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorFormBody(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Prompt inputs", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Step 1 should usually use {{transcript}}. Bulk consolidation transforms can also use {{combined_transcripts}}. Later steps can use {{current_text}} or {{prior_output}} to build on earlier output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onUpdate(draft.copy(name = it)) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onUpdate(draft.copy(description = it)) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        draft.steps.forEachIndexed { index, step ->
            OutlinedTextField(
                value = step,
                onValueChange = { next ->
                    onUpdate(draft.copy(steps = draft.steps.toMutableList().also { it[index] = next }))
                },
                label = { Text("Step ${index + 1}") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                supportingText = {
                    Text("Use {{transcript}} for the original transcription, {{combined_transcripts}} for multi-recording consolidation, and {{prior_output}} or {{current_text}} for previous-step output.")
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onUpdate(draft.copy(steps = draft.steps + "")) }) { Text("Add Step") }
            if (draft.steps.size > 1) {
                TextButton(onClick = { onUpdate(draft.copy(steps = draft.steps.dropLast(1))) }) {
                    Text("Remove Last")
                }
            }
        }
        Column {
            Text("Provider", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.providerType == ProviderType.OPENAI,
                    onClick = { onUpdate(draft.copy(providerType = ProviderType.OPENAI)) },
                    label = { Text("OpenAI") },
                )
                FilterChip(
                    selected = draft.providerType == ProviderType.LOCAL,
                    onClick = { onUpdate(draft.copy(providerType = ProviderType.LOCAL)) },
                    label = { Text("On-device") },
                )
            }
        }
        ModelPickerRow(
            draft = draft,
            onUpdate = onUpdate,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Use as default")
            Switch(checked = draft.isDefault, onCheckedChange = { onUpdate(draft.copy(isDefault = it)) })
        }
    }
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
        if (seedName.isBlank()) seedName = success.suggestion.name
        if (seedDescription.isBlank()) seedDescription = success.suggestion.description
        onSuggestionConsumed()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "AI Profile Draft",
                    style = MaterialTheme.typography.headlineSmall,
                )
                AiDraftModelInfoCard(selectedModel)
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
                    AiDraftSuggestionCard(suggestion)
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
                AiDraftActions(
                    suggestionState = suggestionState,
                    latestSuggestion = latestSuggestion,
                    isDefault = isDefault,
                    request = request,
                    seedName = seedName,
                    seedDescription = seedDescription,
                    onDismiss = onDismiss,
                    onSuggest = onSuggest,
                    onSaveSuggestion = onSaveSuggestion,
                    onEditSuggestion = onEditSuggestion,
                )
            }
        }
    }
}

@Composable
private fun AiDraftModelInfoCard(selectedModel: OpenAiProfileSuggestionModel) {
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
}

@Composable
private fun AiDraftSuggestionCard(suggestion: dev.scrybe.core.transforms.ProfileSuggestion) {
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

@Composable
private fun AiDraftActions(
    suggestionState: ProfileSuggestionUiState,
    latestSuggestion: dev.scrybe.core.transforms.ProfileSuggestion?,
    isDefault: Boolean,
    request: String,
    seedName: String,
    seedDescription: String,
    onDismiss: () -> Unit,
    onSuggest: (String, String, String, List<String>) -> Unit,
    onSaveSuggestion: (dev.scrybe.core.transforms.ProfileSuggestion, Boolean) -> Unit,
    onEditSuggestion: (dev.scrybe.core.transforms.ProfileSuggestion, Boolean) -> Unit,
) {
    val isLoading = suggestionState is ProfileSuggestionUiState.Loading
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
        if (latestSuggestion != null) {
            TextButton(onClick = { onEditSuggestion(latestSuggestion, isDefault) }) {
                Text("Edit Draft")
            }
            Button(onClick = { onSaveSuggestion(latestSuggestion, isDefault) }) {
                Text("Create Profile")
            }
        } else {
            Button(
                onClick = { onSuggest(request, seedName, seedDescription, emptyList()) },
                enabled = !isLoading && request.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating…")
                } else {
                    Text("Generate Draft")
                }
            }
        }
    }
}

@Composable
private fun ModelPickerRow(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    when (draft.providerType) {
        ProviderType.OPENAI -> {
            val currentModel = OpenAiTransformModel.entries.firstOrNull { it.apiName == draft.modelName }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showPicker = true },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Model override", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        currentModel?.title ?: "Global default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Leave blank to use the global AI features model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showPicker) {
                OpenAiModelPickerDialog(
                    currentApiName = draft.modelName,
                    onDismiss = { showPicker = false },
                    onSelect = { apiName ->
                        onUpdate(draft.copy(modelName = apiName))
                        showPicker = false
                    },
                )
            }
        }
        ProviderType.LOCAL -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Model", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "On-device model (managed in Settings → Provider → Local)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAiModelPickerDialog(
    currentApiName: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModelOptionRow(
                    title = "Global default",
                    subtitle = "Use the model set in AI Features settings",
                    selected = currentApiName == null,
                    onClick = { onSelect(null) },
                )
                OpenAiTransformModel.entries.forEach { model ->
                    ModelOptionRow(
                        title = model.title,
                        subtitle = model.supportingText,
                        selected = currentApiName == model.apiName,
                        onClick = { onSelect(model.apiName) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ModelOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
