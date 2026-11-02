package com.shelfsnap.app.ui.itemdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.TwoBitsTopAppBar

@Composable
fun MarketResearchScreen(
    itemId: Long,
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(itemId) { viewModel.load(itemId) }

    Scaffold(
        topBar = {
            TwoBitsTopAppBar(
                title = "Market research",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            MarketTab(uiState = uiState, viewModel = viewModel)
        }
    }
}
