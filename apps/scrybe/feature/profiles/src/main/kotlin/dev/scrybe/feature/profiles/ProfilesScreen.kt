package dev.scrybe.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AppSectionCard
import com.twobits.design.components.AppSectionHeader
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTransformModel
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.TransformProfile

private enum class ProfileIcon(
    val vector: ImageVector,
) {
    MIC(Icons.Filled.Mic),
    GROUPS(Icons.Filled.Groups),
    LIGHTBULB(Icons.Filled.Lightbulb),
    FORUM(Icons.Filled.Forum),
    BOOK(Icons.Filled.Book),
    BOLT(Icons.Filled.Bolt),
    SCHOOL(Icons.Filled.School),
    WORK(Icons.Filled.Work),
    PSYCHOLOGY(Icons.Filled.Psychology),
    FLAG(Icons.Filled.Flag),
    HEADPHONES(Icons.Filled.Headphones),
    CAMERA(Icons.Filled.Camera),
    BRUSH(Icons.Filled.Brush),
    TUNE(Icons.Filled.Tune),
    CAMPAIGN(Icons.Filled.Campaign),
    SCIENCE(Icons.Filled.Science),
    ;

    companion object {
        fun fromName(name: String): ProfileIcon = entries.firstOrNull { it.name == name } ?: MIC
    }
}

private enum class ProfileColor(
    val color: Color,
) {
    BLUE(Color(0xFF3D9CF5)),
    GREEN(Color(0xFF4CD6A5)),
    AMBER(Color(0xFFF5A23D)),
    PURPLE(Color(0xFFA57BF5)),
    PINK(Color(0xFFF57BAF)),
    GRAY(Color(0xFF8B9EAF)),
    ;

    companion object {
        fun fromName(name: String): ProfileColor = entries.firstOrNull { it.name == name } ?: BLUE
    }
}

private data class ProfileTemplate(
    val name: String,
    val iconName: String,
    val colorName: String,
    val steps: List<String>,
    val mode: RecordingMode? = null,
)

private val PROFILE_TEMPLATES =
    listOf(
        ProfileTemplate(
            name = "Daily Standup",
            iconName = "GROUPS",
            colorName = "BLUE",
            steps = listOf("Summarize this standup: what each person is working on, blockers, and key decisions. Use {{transcript}}."),
            mode = RecordingMode.MEETING,
        ),
        ProfileTemplate(
            name = "Product Ideas",
            iconName = "LIGHTBULB",
            colorName = "AMBER",
            steps = listOf("Turn this voice note into a structured list of product ideas with brief descriptions. Use {{transcript}}."),
            mode = RecordingMode.IDEA,
        ),
        ProfileTemplate(
            name = "Interview",
            iconName = "FORUM",
            colorName = "PURPLE",
            steps =
                listOf(
                    "Format this interview into a clean Q&A with speaker labels. Use {{transcript}}.",
                    "Extract 3-5 key highlights and notable quotes. Use {{current_text}}.",
                ),
            mode = RecordingMode.INTERVIEW,
        ),
        ProfileTemplate(
            name = "Voice Journal",
            iconName = "BOOK",
            colorName = "PINK",
            steps = listOf("Clean up and format this journal entry, improving grammar and flow while keeping the personal voice. Use {{transcript}}."),
            mode = RecordingMode.JOURNAL,
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestionState by viewModel.suggestionState.collectAsState()
    val profileSuggestionModel by viewModel.profileSuggestionModel.collectAsState()
    val editorDraft by viewModel.editorDraft.collectAsState()
    val aiCreatorOpen by viewModel.aiCreatorOpen.collectAsState()
    var detailProfile by remember { mutableStateOf<TransformProfile?>(null) }

    val detail = detailProfile
    if (detail != null) {
        ProfileDetailView(
            profile = detail,
            onBack = { detailProfile = null },
            onEdit = {
                viewModel.clearSuggestionState()
                viewModel.openEditor(detail)
                detailProfile = null
            },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profiles") },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
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
                                    onClick = { detailProfile = profile },
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
    }

    editorDraft?.let { draft ->
        ProfileEditorDialog(
            draft = draft,
            onUpdate = { viewModel.updateEditorDraft(it) },
            onDismiss = { viewModel.closeEditor() },
            onSave = {
                viewModel.saveProfile(it)
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
                    ProfileEditorDraft(
                        name = suggestion.name,
                        description = suggestion.description,
                        steps = suggestion.steps,
                        isDefault = isDefault,
                    ),
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
    AppSectionCard {
        AppSectionHeader(
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
    AppSectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    AppSectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileRowHeader(
                profile = profile,
                onEdit = onEdit,
                onDelete = onDelete,
                onSetDefault = onSetDefault,
            )
            PipelineStepFlow(steps = profile.steps)
        }
    }
}

@Composable
private fun ProfileRowHeader(
    profile: TransformProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileIconAvatar(iconName = profile.iconName, colorName = profile.colorName)
        profile.mode?.let { ProfileModeBadge(mode = it) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.description.isNotBlank()) {
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSetDefault, enabled = !profile.isDefault) {
                Icon(
                    imageVector = if (profile.isDefault) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (profile.isDefault) "Default" else "Make default",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileIconAvatar(
    iconName: String,
    colorName: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val icon = ProfileIcon.fromName(iconName)
    val color = ProfileColor.fromName(colorName).color
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.small,
            color = color.copy(alpha = 0.15f),
        ) {}
        Icon(
            imageVector = icon.vector,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun PipelineStepFlow(steps: List<String>) {
    val visibleSteps = steps.take(3)
    val overflow = steps.size - visibleSteps.size
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PipelineChip(
            label = "Transcribe",
            icon = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(12.dp)) },
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        visibleSteps.forEachIndexed { index, step ->
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            val label = stepShortLabel(step, index)
            PipelineChip(
                label = label,
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp)) },
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (overflow > 0) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            PipelineChip(
                label = "+$overflow more",
                icon = null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PipelineChip(
    label: String,
    icon: (@Composable () -> Unit)?,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides color,
                ) { icon() }
            }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

private fun stepShortLabel(
    step: String,
    index: Int,
): String {
    val trimmed = step.trim()
    if (trimmed.isBlank()) return "Step ${index + 1}"
    val firstLine = trimmed.lines().firstOrNull { it.isNotBlank() } ?: return "Step ${index + 1}"
    return if (firstLine.length <= 20) firstLine else firstLine.take(18).trimEnd() + "…"
}

@Composable
private fun ProfileDetailView(
    profile: TransformProfile,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    var modeOpen by remember { mutableStateOf(true) }
    var transformsOpen by remember { mutableStateOf(false) }
    var sendToOpen by remember { mutableStateOf(false) }
    var triggerOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ProfileDetailHeader(profile = profile, onBack = onBack, onEdit = onEdit)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PipelineStepFlow(steps = profile.steps)
            BuilderSection(
                title = "Mode",
                icon = Icons.Filled.Mic,
                isOpen = modeOpen,
                onToggle = { modeOpen = !modeOpen },
            ) {
                val mode = profile.mode
                if (mode != null) {
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        mode.outputDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Any mode", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "This profile applies to any recording mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BuilderSection(
                title = "AI Transforms",
                icon = Icons.Filled.AutoAwesome,
                badge = profile.steps.size,
                isOpen = transformsOpen,
                onToggle = { transformsOpen = !transformsOpen },
            ) { TransformOptionRows(profile.steps) }
            BuilderSection(
                title = "Send to",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                isOpen = sendToOpen,
                onToggle = { sendToOpen = !sendToOpen },
            ) { SendToOptionRows() }
            BuilderSection(
                title = "Auto-trigger",
                icon = Icons.Filled.AutoAwesome,
                isOpen = triggerOpen,
                onToggle = { triggerOpen = !triggerOpen },
            ) { TriggerOptionRows() }
        }
    }
}

@Composable
private fun ProfileDetailHeader(
    profile: TransformProfile,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        profile.mode?.let { ProfileModeBadge(mode = it) }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit profile")
        }
    }
}

@Composable
private fun BuilderSection(
    title: String,
    icon: ImageVector,
    badge: Int = 0,
    isOpen: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (badge > 0) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                imageVector = if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = isOpen) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    sub: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = selected, onCheckedChange = onToggle)
    }
}

@Composable
private fun ModeOptionRows() {
    var selected by remember { mutableStateOf(RecordingMode.JOURNAL) }
    RecordingMode.entries.forEach { mode ->
        OptionRow(
            icon = Icons.Filled.Mic,
            label = mode.label,
            sub = mode.outputDescription,
            selected = selected == mode,
            onToggle = { if (it) selected = mode },
        )
    }
}

@Composable
private fun TransformOptionRows(steps: List<String>) {
    if (steps.isEmpty()) {
        Text(
            text = "No steps configured",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    steps.forEachIndexed { idx, step ->
        OptionRow(
            icon = Icons.Filled.AutoAwesome,
            label = stepShortLabel(step, idx),
            sub = if (step.length > 60) step.take(60).trimEnd() + "…" else step,
            selected = true,
            onToggle = {},
        )
    }
}

@Composable
private fun SendToOptionRows() {
    OptionRow(
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        label = "Notion",
        sub = "Export session as page",
        selected = false,
        onToggle = {},
    )
    OptionRow(
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        label = "Slack",
        sub = "Post summary to channel",
        selected = false,
        onToggle = {},
    )
    OptionRow(
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        label = "Share",
        sub = "System share sheet",
        selected = false,
        onToggle = {},
    )
}

@Composable
private fun TriggerOptionRows() {
    var autoEnabled by remember { mutableStateOf(false) }
    OptionRow(
        icon = Icons.Filled.AutoAwesome,
        label = "After every recording",
        sub = "Run automatically when recording stops",
        selected = autoEnabled,
        onToggle = { autoEnabled = it },
    )
    OptionRow(
        icon = Icons.Filled.Mic,
        label = "Manual only",
        sub = "Trigger transforms manually from session view",
        selected = !autoEnabled,
        onToggle = { if (it) autoEnabled = false },
    )
}

@Composable
private fun ProfileEditorDialog(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProfileEditorDraft) -> Unit,
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
                        onClick = { onSave(draft) },
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
        if (draft.existingId == null) {
            ProfileTemplateSection { template ->
                onUpdate(draft.copy(name = template.name, iconName = template.iconName, colorName = template.colorName, steps = template.steps, mode = template.mode))
            }
        }
        ProfilePromptInputsCard()
        OutlinedTextField(value = draft.name, onValueChange = { onUpdate(draft.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = draft.description, onValueChange = { onUpdate(draft.copy(description = it)) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        ProfileModePickerRow(selectedMode = draft.mode, onModeSelected = { onUpdate(draft.copy(mode = it)) })
        ProfileAppearanceSection(draft = draft, onUpdate = onUpdate)
        ProfileStepsSection(draft = draft, onUpdate = onUpdate)
        ProfileProviderSection(draft = draft, onUpdate = onUpdate)
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
private fun ProfilePromptInputsCard() {
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
}

@Composable
private fun ProfileStepsSection(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
) {
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
            TextButton(onClick = { onUpdate(draft.copy(steps = draft.steps.dropLast(1))) }) { Text("Remove Last") }
        }
    }
}

@Composable
private fun ProfileProviderSection(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
) {
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
    ModelPickerRow(draft = draft, onUpdate = onUpdate)
}

@Composable
private fun ProfileModePickerRow(
    selectedMode: RecordingMode?,
    onModeSelected: (RecordingMode?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Mode", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedMode == null, onClick = { onModeSelected(null) }, label = { Text("Any") })
            RecordingMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
    }
}

@Composable
private fun ProfileModeBadge(mode: RecordingMode) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = mode.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ProfileAppearanceSection(
    draft: ProfileEditorDraft,
    onUpdate: (ProfileEditorDraft) -> Unit,
) {
    var showIconPicker by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ICON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileIconPickerRow(
                    selectedIconName = draft.iconName,
                    selectedColorName = draft.colorName,
                    onIconSelected = { onUpdate(draft.copy(iconName = it)) },
                    onShowAll = { showIconPicker = true },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileColorPickerRow(
                    selectedColorName = draft.colorName,
                    onColorSelected = { onUpdate(draft.copy(colorName = it)) },
                )
            }
        }
    }
    if (showIconPicker) {
        ProfileIconPickerModal(
            selectedIconName = draft.iconName,
            selectedColorName = draft.colorName,
            onIconSelected = { onUpdate(draft.copy(iconName = it)) },
            onDismiss = { showIconPicker = false },
        )
    }
}

@Composable
private fun ProfileIconPickerRow(
    selectedIconName: String,
    selectedColorName: String,
    onIconSelected: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    val selectedColor = ProfileColor.fromName(selectedColorName).color
    val quickIcons = ProfileIcon.entries.take(8)
    val isInQuick = quickIcons.any { it.name == selectedIconName }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quickIcons.forEach { icon ->
            val sel = icon.name == selectedIconName
            Surface(
                onClick = { onIconSelected(icon.name) },
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.small,
                color = if (sel) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (sel) BorderStroke(2.dp, selectedColor) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon.vector, contentDescription = null, tint = if (sel) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }
        val moreIcon = if (!isInQuick) ProfileIcon.fromName(selectedIconName).vector else Icons.Filled.MoreHoriz
        val moreTint = if (!isInQuick) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
        Surface(
            onClick = onShowAll,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            color = if (!isInQuick) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            border = if (!isInQuick) BorderStroke(2.dp, selectedColor) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(moreIcon, contentDescription = "More icons", tint = moreTint, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ProfileColorPickerRow(
    selectedColorName: String,
    onColorSelected: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileColor.entries.forEach { profileColor ->
            val isSelected = profileColor.name == selectedColorName
            Surface(
                onClick = { onColorSelected(profileColor.name) },
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = profileColor.color,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileIconPickerModal(
    selectedIconName: String,
    selectedColorName: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedColor = ProfileColor.fromName(selectedColorName).color
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose an icon", style = MaterialTheme.typography.titleMedium)
                ProfileIcon.entries.chunked(4).forEach { rowIcons ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowIcons.forEach { icon ->
                            val sel = icon.name == selectedIconName
                            Surface(
                                onClick = {
                                    onIconSelected(icon.name)
                                    onDismiss()
                                },
                                modifier = Modifier.size(52.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = if (sel) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (sel) BorderStroke(2.dp, selectedColor) else null,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon.vector, contentDescription = null, tint = if (sel) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ProfileTemplateSection(onApplyTemplate: (ProfileTemplate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "START FROM A TEMPLATE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                PROFILE_TEMPLATES.forEachIndexed { i, template ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onApplyTemplate(template) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileIconAvatar(iconName = template.iconName, colorName = template.colorName, size = 34)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.bodyMedium)
                            val stepLabel = if (template.steps.size == 1) "1 step" else "${template.steps.size} steps"
                            Text(stepLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
