package com.shelfsnap.app.ui.itemdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.ui.inventory.conditionColor
import com.shelfsnap.app.ui.theme.LocalEstimateLabel
import java.io.File

@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onAddPhoto: () -> Unit = {},
    onNavigateToMarketResearch: () -> Unit = {},
    onNavigateToListingSummary: () -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) { viewModel.load(itemId) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    val listedMessage = stringResource(R.string.listings_created)
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            snackbarHostState.showSnackbar(listedMessage)
            viewModel.clearMessage()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showPhotoViewer) {
        val photos = uiState.item?.photoPaths ?: emptyList()
        if (photos.isNotEmpty()) {
            PhotoViewerDialog(
                photos = photos,
                initialIndex = uiState.viewerPhotoIndex,
                onDismiss = viewModel::closePhotoViewer,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (uiState.item?.isDraft == true) {
                                stringResource(R.string.draft_label)
                            } else {
                                uiState.item?.category ?: ""
                            },
                        )
                        val brand = uiState.item?.brand.orEmpty()
                        if (brand.isNotBlank()) {
                            val model = uiState.item?.model.orEmpty()
                            Text(
                                text = if (model.isNotBlank()) "$brand · $model" else brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading ->
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    Alignment.Center,
                ) { CircularProgressIndicator() }

            uiState.item != null ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    DetailTabBar(
                        selected = uiState.tab,
                        onSelect = viewModel::selectTab,
                    )

                    Box(Modifier.weight(1f)) {
                        when (uiState.tab) {
                            DetailTab.DETAILS ->
                                DetailsTab(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onPhotoClick = viewModel::openPhotoViewer,
                                    onAddPhoto = onAddPhoto,
                                )
                            DetailTab.MARKET -> MarketTab(uiState = uiState, viewModel = viewModel)
                            DetailTab.LIST -> ListTab(uiState = uiState, viewModel = viewModel, onNavigateToSummary = onNavigateToListingSummary)
                        }
                    }

                    // Sticky footer actions — Details tab only
                    if (uiState.tab == DetailTab.DETAILS) {
                        HorizontalDivider()
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::saveDraft,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isAnalysing,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(stringResource(R.string.save_draft)) }
                            Button(
                                onClick = viewModel::confirm,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isAnalysing,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun DetailTabBar(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
) {
    val tabs =
        listOf(
            DetailTab.DETAILS to stringResource(R.string.tab_details),
            DetailTab.MARKET to stringResource(R.string.tab_market),
            DetailTab.LIST to stringResource(R.string.tab_list),
        )
    TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == selected }) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(label) },
            )
        }
    }
}

@Composable
private fun DetailsTab(
    uiState: ItemDetailUiState,
    viewModel: ItemDetailViewModel,
    onPhotoClick: (Int) -> Unit,
    onAddPhoto: () -> Unit,
) {
    val item = uiState.item ?: return
    val estimateColor = LocalEstimateLabel.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Photos — numbered gallery with primary-star selector and "Add photo" slot
        Text(
            text = stringResource(R.string.photos),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.photoPaths.forEachIndexed { index, path ->
                val isPrimary = index == uiState.editPrimaryPhotoIndex
                Box(modifier = Modifier.size(80.dp)) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPhotoClick(index) },
                        contentScale = ContentScale.Crop,
                    )
                    // Star tap zone — top-left corner
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { viewModel.setPrimaryPhoto(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPrimary) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription =
                                if (isPrimary) {
                                    stringResource(R.string.primary_photo)
                                } else {
                                    stringResource(R.string.set_as_primary)
                                },
                            tint = if (isPrimary) Color(0xFFFFD580) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${index + 1}/${item.photoPaths.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            AddPhotoSlot(onClick = onAddPhoto)
        }

        if (item.confidencePercent > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.confidence_short, item.confidencePercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(uiState.lastAnalysisModel ?: VisionModel.default).displayName} analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Core fields
        OutlinedTextField(
            value = uiState.editTitle,
            onValueChange = viewModel::onTitleChange,
            label = { Text(stringResource(R.string.item_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = uiState.editCategory,
            onValueChange = viewModel::onCategoryChange,
            label = { Text(stringResource(R.string.category)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.editBrand,
                onValueChange = viewModel::onBrandChange,
                label = { Text(stringResource(R.string.brand)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = uiState.editModel,
                onValueChange = viewModel::onModelChange,
                label = { Text(stringResource(R.string.model)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }
        OutlinedTextField(
            value = uiState.editDescription,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
        )
        ConditionSelector(selected = uiState.editCondition, onSelect = viewModel::onConditionChange)

        // Additional details
        Text(
            text = stringResource(R.string.additional_details),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.additional_details_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.editSize,
                onValueChange = viewModel::onSizeChange,
                label = { Text(stringResource(R.string.size)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = uiState.editColor,
                onValueChange = viewModel::onColorChange,
                label = { Text(stringResource(R.string.color)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.editQuantity,
                onValueChange = viewModel::onQuantityChange,
                label = { Text(stringResource(R.string.quantity)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = uiState.editOriginalPrice,
                onValueChange = viewModel::onOriginalPriceChange,
                label = { Text(stringResource(R.string.original_price)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
            )
        }

        TagEditor(
            tags = uiState.editTags,
            onAdd = viewModel::addTag,
            onRemove = viewModel::removeTag,
        )

        // Asking price — clearly labeled as estimate
        OutlinedTextField(
            value = uiState.editEstimatedValue,
            onValueChange = viewModel::onEstimatedValueChange,
            label = { Text(stringResource(R.string.asking_price)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = { Text("$") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            supportingText = {
                Text(
                    stringResource(R.string.value_is_estimate),
                    color = estimateColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )

        // Re-analyse
        if (item.photoPaths.isNotEmpty()) {
            if (uiState.visionSource == "byok") {
                ReanalyseModelPicker(
                    selected = uiState.overrideVisionModel,
                    onSelect = viewModel::onOverrideVisionModelChange,
                )
            }
            OutlinedButton(
                onClick = viewModel::reanalyse,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isAnalysing,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isAnalysing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.analyzing))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reanalyze))
                }
            }
        }
    }
}

@Composable
private fun ReanalyseModelPicker(
    selected: com.shelfsnap.app.data.model.VisionModel?,
    onSelect: (com.shelfsnap.app.data.model.VisionModel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val effective = selected ?: com.shelfsnap.app.data.model.VisionModel.default
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = effective.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Vision model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            supportingText = {
                Text(
                    if (selected == null) "Using Settings default · ${effective.costLabel}" else effective.costLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.shelfsnap.app.data.model.VisionModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${model.supportingText} · ${model.costLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Dashed, tappable tile that launches the camera to append photos to this item. */
@Composable
private fun AddPhotoSlot(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(
        modifier =
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .drawBehind {
                    drawRoundRect(
                        color = outline,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            ),
                    )
                }.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = outline, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.add_photo), style = MaterialTheme.typography.labelSmall, color = outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.tags),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag) },
                    label = { Text("#$tag") },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.add_tag)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = {
                    onAdd(input)
                    input = ""
                }, enabled = input.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_tag))
                }
            },
        )
    }
}

/**
 * Four color-coded segmented buttons (Excellent/Good/Fair/Poor) matching the design,
 * so the selected condition reads at a glance by color rather than hiding in a dropdown.
 */
@Composable
private fun ConditionSelector(
    selected: Condition,
    onSelect: (Condition) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.condition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Condition.entries.forEach { condition ->
                val color = conditionColor(condition)
                val active = condition == selected
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color =
                        if (active) {
                            color.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                    border = if (active) BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null,
                    onClick = { onSelect(condition) },
                ) {
                    Text(
                        text = condition.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoViewerDialog(
    photos: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = File(photos[currentIndex]),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
                if (photos.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = null) }
                        Text("${currentIndex + 1} / ${photos.size}", style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = { if (currentIndex < photos.size - 1) currentIndex++ },
                            enabled = currentIndex < photos.size - 1,
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
