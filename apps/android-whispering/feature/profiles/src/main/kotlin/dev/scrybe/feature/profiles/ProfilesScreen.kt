package dev.scrybe.feature.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.model.TransformProfile

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ProfilesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ProfilesUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        is ProfilesUiState.Success -> {
            if (state.profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No profiles configured")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.profiles) { profile ->
                        ProfileRow(profile)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: TransformProfile) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(profile.name, style = MaterialTheme.typography.titleMedium)
        Text(profile.description, style = MaterialTheme.typography.bodySmall)
    }
}
