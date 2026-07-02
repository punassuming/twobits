package com.twobits.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class WhatsNewRelease(
    val version: String,
    val date: String,
    val isLatest: Boolean = false,
    val categories: List<WhatsNewCategory>,
)

data class WhatsNewCategory(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val items: List<WhatsNewItem>,
)

data class WhatsNewItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val actionTarget: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreenLayout(
    title: String,
    releases: List<WhatsNewRelease>,
    onBack: () -> Unit,
    onNavigate: ((target: String) -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(releases, key = { it.version }) { release ->
                WhatsNewVersionBlock(
                    release = release,
                    onNavigate = onNavigate,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WhatsNewVersionBlock(
    release: WhatsNewRelease,
    onNavigate: ((String) -> Unit)?,
) {
    val expandedCats = remember(release.version) {
        mutableStateMapOf<String, Boolean>().also { map ->
            if (release.isLatest) {
                release.categories.firstOrNull()?.id?.let { map[it] = true }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Version header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Version ${release.version}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (release.isLatest) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = "Current version",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Text(
                    text = release.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            release.categories.forEach { category ->
                val isOpen = expandedCats[category.id] == true
                CategorySection(
                    category = category,
                    isOpen = isOpen,
                    onToggle = { expandedCats[category.id] = !isOpen },
                    onNavigate = onNavigate,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CategorySection(
    category: WhatsNewCategory,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onNavigate: ((String) -> Unit)?,
) {
    val expandedItems = remember(category.id) { mutableStateMapOf<String, Boolean>() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "cat_chevron",
    )

    Column {
        // Category row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = category.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = category.items.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                category.items.forEach { item ->
                    val isItemOpen = expandedItems[item.id] == true
                    WhatsNewItemRow(
                        item = item,
                        isOpen = isItemOpen,
                        onToggle = { expandedItems[item.id] = !isItemOpen },
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsNewItemRow(
    item: WhatsNewItem,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onNavigate: ((String) -> Unit)?,
) {
    val hasDescription = item.description.isNotBlank()
    val itemChevronRotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "item_chevron",
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isOpen) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Column {
            if (hasDescription) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (isOpen) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(itemChevronRotation),
                    )
                }

                AnimatedVisibility(
                    visible = isOpen,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 56.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WhatsNewDescriptionText(
                            description = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.actionLabel != null && item.actionTarget != null && onNavigate != null) {
                            val target = item.actionTarget
                            Row(
                                modifier = Modifier.clickable { onNavigate(target) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(0.dp))
                                Text(
                                    text = item.actionLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Renders a changelog item's description, splitting on the " · " join the parser uses for
 * multiple detail bullets into separate "• " lines instead of one inline paragraph. Falls back to
 * a single plain line when there's nothing to split. Shared by the full What's New screen and the
 * automatic update dialog so both surfaces render sub-bullets identically.
 */
@Composable
internal fun WhatsNewDescriptionText(
    description: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (description.isBlank()) return
    val bullets = description.split(" · ")
    if (bullets.size <= 1) {
        Text(text = description, style = style, color = color, modifier = modifier)
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            bullets.forEach { bullet ->
                Text(text = "• $bullet", style = style, color = color)
            }
        }
    }
}
