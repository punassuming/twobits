package com.twobits.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    scrim: Color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = scrim,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
