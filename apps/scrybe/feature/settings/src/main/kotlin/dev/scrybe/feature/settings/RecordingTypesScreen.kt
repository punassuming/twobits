package dev.scrybe.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.CustomTypeIcon
import dev.scrybe.core.common.customTypeIcon
import dev.scrybe.core.database.CustomRecordingTypeEntity

/**
 * Manage user-created recording types: create, rename, change icon or linked transform profile,
 * and delete (recordings of a deleted type are reassigned to Journal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingTypesScreen(
    onBack: () -> Unit,
    viewModel: RecordingTypesViewModel = hiltViewModel(),
) {
    val types by viewModel.types.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    var editorTarget by remember { mutableStateOf<CustomRecordingTypeEntity?>(null) }
    var showEditorForNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CustomRecordingTypeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording types") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditorForNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New recording type")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (types.isEmpty()) {
                Text(
                    "No custom types yet. Create one here or from the recording mode picker.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val profileNames = profiles.associate { it.id to it.name }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(types, key = { it.id }) { type ->
                        RecordingTypeRow(
                            type = type,
                            profileName = type.defaultProfileId?.let { profileNames[it] },
                            onEdit = { editorTarget = type },
                            onDelete = { deleteTarget = type },
                        )
                    }
                }
            }
        }
    }

    if (showEditorForNew || editorTarget != null) {
        val editing = editorTarget
        RecordingTypeEditorDialog(
            title = if (editing == null) "New recording type" else "Edit recording type",
            initialName = editing?.name.orEmpty(),
            initialIcon = CustomTypeIcon.fromName(editing?.iconName),
            initialProfileId = editing?.defaultProfileId,
            profiles = profiles,
            onConfirm = { name, icon, profileId ->
                viewModel.saveType(editing?.id, name, icon.name, profileId)
                editorTarget = null
                showEditorForNew = false
            },
            onDismiss = {
                editorTarget = null
                showEditorForNew = false
            },
        )
    }

    deleteTarget?.let { pending ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${pending.name}\"?") },
            text = {
                Text(
                    "Recordings made with this type are kept and become Journal recordings. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteType(pending.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecordingTypeRow(
    type: CustomRecordingTypeEntity,
    profileName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = customTypeIcon(type.iconName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(type.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    profileName ?: "Plain transcript",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit ${type.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${type.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingTypeEditorDialog(
    title: String,
    initialName: String,
    initialIcon: CustomTypeIcon,
    initialProfileId: String?,
    profiles: List<RecordingTypeProfileOption>,
    onConfirm: (name: String, icon: CustomTypeIcon, profileId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedProfileId by remember { mutableStateOf(initialProfileId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Type name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomTypeIcon.entries.forEach { option ->
                        val selected = option == selectedIcon
                        Surface(
                            onClick = { selectedIcon = option },
                            shape = CircleShape,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                        ) {
                            Icon(
                                imageVector = option.vector,
                                contentDescription = option.name.lowercase(),
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
                if (profiles.isNotEmpty()) {
                    Text("Default transform profile (optional)", style = MaterialTheme.typography.labelMedium)
                    profiles.forEach { profile ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProfileId = if (selectedProfileId == profile.id) null else profile.id
                                    }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = selectedProfileId == profile.id,
                                onCheckedChange = { checked ->
                                    selectedProfileId = if (checked) profile.id else null
                                },
                            )
                            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedIcon, selectedProfileId) },
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
