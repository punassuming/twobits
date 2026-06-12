package dev.scrybe.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.database.PersonEntity
import kotlinx.coroutines.launch

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (persons.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(persons, key = { it.id }) { person ->
                    var sessionCount by remember { mutableIntStateOf(0) }
                    LaunchedEffect(person.id) {
                        sessionCount = viewModel.sessionCountForPerson(person.id)
                    }
                    PersonRow(
                        person = person,
                        sessionCount = sessionCount,
                        otherPersons = persons.filter { it.id != person.id },
                        onRename = { renameTarget = person },
                        onMergeInto = { targetId ->
                            scope.launch { viewModel.mergePersons(person.id, targetId) }
                        },
                        onDelete = { deleteTarget = person },
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
}

@Composable
private fun PersonRow(
    person: PersonEntity,
    sessionCount: Int,
    otherPersons: List<PersonEntity>,
    onRename: () -> Unit,
    onMergeInto: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (sessionCount == 1) "1 session" else "$sessionCount sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                if (otherPersons.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Merge into…") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showMergePicker = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
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
                        modifier =
                            Modifier
                                .clickable { selected = candidate.id }
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
