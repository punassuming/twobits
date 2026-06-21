package com.twobits.pricedrop.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val PAGES =
    listOf(
        OnboardingPage(
            icon = Icons.Filled.Notifications,
            title = "Never overpay",
            body = "PriceDrop watches products you care about and alerts you the moment the price falls below your target.",
        ),
        OnboardingPage(
            icon = Icons.Filled.Lock,
            title = "Your privacy first",
            body = "Price checks run through our secure proxy. Your API keys never leave your device. No account required.",
        ),
        OnboardingPage(
            icon = Icons.Filled.AutoAwesome,
            title = "AI-powered research",
            body = "Ask our AI to find deals, look up price history, or discover coupons — all in plain language.",
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
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
                current.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                current.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(48.dp))
            if (page < PAGES.lastIndex) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                TextButton(onClick = onFinish) { Text("Skip") }
            } else {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            }
        }
    }
}
