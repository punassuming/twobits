package com.shelfsnap.app.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shelfsnap.app.R

private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val PAGES =
    listOf(
        OnboardingPage(
            icon = Icons.Filled.PhotoCamera,
            title = R.string.onboarding_capture_title,
            body = R.string.onboarding_capture_body,
        ),
        OnboardingPage(
            icon = Icons.Filled.AutoAwesome,
            title = R.string.onboarding_ai_title,
            body = R.string.onboarding_ai_body,
        ),
        OnboardingPage(
            icon = Icons.Filled.Sell,
            title = R.string.onboarding_sell_title,
            body = R.string.onboarding_sell_body,
        ),
        OnboardingPage(
            icon = Icons.Filled.Lock,
            title = R.string.onboarding_privacy_title,
            body = R.string.onboarding_privacy_body,
        ),
    )

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    // rememberSaveable so the page survives rotation and system-initiated process death.
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = PAGES[page]

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                current.icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(current.title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(current.body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            PageDots(count = PAGES.size, selected = page)
            Spacer(Modifier.height(24.dp))
            if (page < PAGES.lastIndex) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_next))
                }
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            } else {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }
}

@Composable
private fun PageDots(
    count: Int,
    selected: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val color =
                if (index == selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}
