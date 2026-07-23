package com.shelfsnap.app.ui.summary

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.ui.components.ItemThumb
import com.shelfsnap.app.ui.inventory.ConditionBadge
import com.shelfsnap.app.ui.theme.LocalEstimateLabel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareLabel = stringResource(R.string.share_csv)
    val chooserTitle = stringResource(R.string.share_csv_chooser)

    LaunchedEffect(uiState.exportedPath) {
        uiState.exportedPath?.let { path ->
            val result =
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.exported_to_share),
                    actionLabel = shareLabel,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                shareCsv(context, path, chooserTitle)
            }
            viewModel.clearExportStatus()
        }
    }

    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackbarHostState.showSnackbar(
                context.getString(R.string.export_failed, it),
            )
            viewModel.clearExportStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.donation_summary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv(context) }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.export_csv),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            // Total banner – Story 5: total estimated donation value
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.VolunteerActivism,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.total_donation_value),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "$" + "%.2f".format(uiState.totalValue),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.items_count, uiState.items.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            text = stringResource(R.string.value_is_estimate),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalEstimateLabel.current,
                        )
                    }
                }
            }

            // Per-item list with subtotals
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    SummaryItemRow(item = item)
                    HorizontalDivider()
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.no_items))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shares the exported CSV via a system chooser using a [FileProvider] content URI,
 * so the file (which lives in scoped app storage) can be sent off-device.
 */
private fun shareCsv(
    context: Context,
    path: String,
    chooserTitle: String,
) {
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path),
        )
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

@Composable
private fun SummaryItemRow(item: Item) {
    val estimateColor = LocalEstimateLabel.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemThumb(item = item, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.category.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.description.ifBlank { "No description" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            ConditionBadge(condition = item.condition)
        }
        // Story 4 & 5: per-item subtotal labeled as estimate
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$" + "%.2f".format(item.estimatedValue),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = estimateColor,
            )
            Text(
                text = stringResource(R.string.subtotal),
                style = MaterialTheme.typography.labelSmall,
                color = estimateColor,
            )
        }
    }
}
