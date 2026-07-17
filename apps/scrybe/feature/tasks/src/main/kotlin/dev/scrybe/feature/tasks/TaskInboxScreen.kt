package dev.scrybe.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AppChipRow
import com.twobits.design.components.AppEmptyState
import dev.scrybe.core.common.ModeBadge

private val TODAY_LABELS = setOf("today", "Today")

private fun groupTasks(tasks: List<InboxTask>): Map<String, List<InboxTask>> =
    tasks
        .groupBy { task ->
            when {
                task.isDone -> "Done"
                task.dueLabel.isNullOrEmpty() -> "No due date"
                else -> task.dueLabel!!
            }
        }.toSortedMap(
            compareBy { key ->
                when (key) {
                    "Today", "today" -> 0
                    "No due date" -> Int.MAX_VALUE
                    "Done" -> Int.MAX_VALUE - 1
                    else -> 1
                }
            },
        )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskInboxScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    viewModel: TaskInboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val openCount = (uiState as? TaskInboxUiState.Success)?.counts?.open ?: 0
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks")
                        if (openCount > 0) {
                            Text(
                                "$openCount open across sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Back",
                            modifier = Modifier.clip(CircleShape).size(24.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is TaskInboxUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

            is TaskInboxUiState.Success ->
                TaskInboxContent(
                    state = state,
                    modifier = Modifier.padding(paddingValues),
                    onFilterChange = viewModel::setFilter,
                    onToggleDone = { task -> viewModel.toggleDone(task.id, task.isDone) },
                    onSessionClick = onNavigateToSession,
                )
        }
    }
}

@Composable
private fun TaskInboxContent(
    state: TaskInboxUiState.Success,
    modifier: Modifier,
    onFilterChange: (TaskFilter) -> Unit,
    onToggleDone: (InboxTask) -> Unit,
    onSessionClick: (String) -> Unit,
) {
    val grouped = remember(state.tasks) { groupTasks(state.tasks) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            StatCardsRow(counts = state.counts, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        item {
            FilterChipRow(
                filter = state.filter,
                counts = state.counts,
                onSelect = onFilterChange,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
            )
        }
        if (state.tasks.isEmpty()) {
            item {
                AppEmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = "All clear",
                    subtitle = "No tasks match this filter",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else {
            grouped.forEach { (groupName, tasks) ->
                item(key = "header_$groupName") {
                    TaskGroupHeader(groupName = groupName, count = tasks.size)
                }
                items(tasks, key = { it.id }) { task ->
                    TaskRow(task = task, onToggle = { onToggleDone(task) }, onSessionClick = onSessionClick)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskGroupHeader(
    groupName: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(groupName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatCardsRow(
    counts: TaskInboxCounts,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(icon = Icons.Filled.Event, label = "Today", count = counts.today, tint = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        StatCard(icon = Icons.Filled.DateRange, label = "This week", count = counts.week, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
        StatCard(icon = Icons.Filled.Person, label = "Mine", count = counts.mine, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$count", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FilterChipRow(
    filter: TaskFilter,
    counts: TaskInboxCounts,
    onSelect: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChipRow(
        modifier = modifier,
        horizontalPadding = 0.dp,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TaskFilterChip("Open", counts.open, filter == TaskFilter.OPEN) { onSelect(TaskFilter.OPEN) }
        TaskFilterChip("Today", counts.today, filter == TaskFilter.TODAY) { onSelect(TaskFilter.TODAY) }
        TaskFilterChip("Week", counts.week, filter == TaskFilter.WEEK) { onSelect(TaskFilter.WEEK) }
        TaskFilterChip("Mine", counts.mine, filter == TaskFilter.MINE) { onSelect(TaskFilter.MINE) }
        TaskFilterChip("Delegated", counts.delegated, filter == TaskFilter.DELEGATED) { onSelect(TaskFilter.DELEGATED) }
        TaskFilterChip("Done", counts.done, filter == TaskFilter.DONE) { onSelect(TaskFilter.DONE) }
    }
}

@Composable
private fun TaskFilterChip(
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text("$label $count", style = MaterialTheme.typography.labelMedium) },
        colors =
            if (active) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
    )
}

@Composable
private fun TaskRow(
    task: InboxTask,
    onToggle: () -> Unit,
    onSessionClick: (String) -> Unit,
) {
    val priorityColor =
        when {
            task.dueLabel in TODAY_LABELS -> MaterialTheme.colorScheme.error
            !task.dueLabel.isNullOrEmpty() -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TaskCheckbox(checked = task.isDone, priorityColor = priorityColor, onToggle = onToggle)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = task.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TaskMetaRow(task = task)
            TaskSessionLink(task = task, onSessionClick = onSessionClick)
        }
    }
}

@Composable
private fun TaskMetaRow(task: InboxTask) {
    if (task.dueLabel.isNullOrEmpty() && task.assignee.isNullOrEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!task.dueLabel.isNullOrEmpty()) {
            DueChip(dueLabel = task.dueLabel!!)
        }
        if (!task.assignee.isNullOrEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(task.assignee!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaskSessionLink(
    task: InboxTask,
    onSessionClick: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModeBadge(mode = task.sessionMode)
        Text(task.sessionTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { onSessionClick(task.sessionId) }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open session", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCheckbox(
    checked: Boolean,
    priorityColor: Color,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.size(22.dp).padding(top = 2.dp),
        shape = RoundedCornerShape(6.dp),
        color = if (checked) MaterialTheme.colorScheme.tertiary else Color.Transparent,
        border =
            androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (checked) MaterialTheme.colorScheme.tertiary else priorityColor,
            ),
    ) {
        if (checked) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Done", modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onTertiary)
        }
    }
}

@Composable
private fun DueChip(dueLabel: String) {
    val isToday = dueLabel in TODAY_LABELS
    val tint = if (isToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(11.dp), tint = tint)
        Text(dueLabel, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
