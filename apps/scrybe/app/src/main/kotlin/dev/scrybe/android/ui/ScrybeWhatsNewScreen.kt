package dev.scrybe.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import com.twobits.design.components.WhatsNewCategory
import com.twobits.design.components.WhatsNewItem
import com.twobits.design.components.WhatsNewRelease
import com.twobits.design.components.WhatsNewScreenLayout

private val SCRYBE_RELEASES = listOf(
    WhatsNewRelease(
        version = "1.6.2",
        date = "Jun 2026",
        isLatest = true,
        categories = listOf(
            WhatsNewCategory(
                id = "improve",
                label = "Improvements",
                icon = Icons.Filled.TrendingUp,
                items = listOf(
                    WhatsNewItem(
                        id = "i1",
                        icon = Icons.Filled.VerifiedUser,
                        title = "Keystore validation before release",
                        description = "The release workflow now validates your keystore secret before any irreversible commit or tag — bad credentials are caught early, not mid-deploy.",
                    ),
                ),
            ),
        ),
    ),
    WhatsNewRelease(
        version = "1.4.0",
        date = "Jun 2026",
        categories = listOf(
            WhatsNewCategory(
                id = "new",
                label = "Features & Enhancements",
                icon = Icons.Filled.AutoAwesome,
                items = listOf(
                    WhatsNewItem(
                        id = "n1",
                        icon = Icons.Filled.Psychology,
                        title = "Vision model selector for BYOK users",
                        description = "Choose your AI model for transcription and transforms: GPT-4o, GPT-4o mini, GPT-5 mini, or GPT-4.1 mini. Your selection persists between sessions.",
                        actionLabel = "View in Settings →",
                        actionTarget = "settings",
                    ),
                ),
            ),
            WhatsNewCategory(
                id = "improve",
                label = "Improvements",
                icon = Icons.Filled.TrendingUp,
                items = listOf(
                    WhatsNewItem(
                        id = "i1",
                        icon = Icons.Filled.Bolt,
                        title = "Atomic spend tracking",
                        description = "Worker spend limits now use Durable Objects to atomically reserve budget before forwarding requests — eliminates race conditions in concurrent sessions.",
                    ),
                ),
            ),
        ),
    ),
    WhatsNewRelease(
        version = "1.2.0",
        date = "Jun 2026",
        categories = listOf(
            WhatsNewCategory(
                id = "new",
                label = "Features & Enhancements",
                icon = Icons.Filled.AutoAwesome,
                items = listOf(
                    WhatsNewItem(
                        id = "n1",
                        icon = Icons.Filled.WorkspacePremium,
                        title = "Scrybe Pro subscription",
                        description = "Upgrade to Pro (\$1.99/mo) for managed OpenAI access — no personal API key required. Purchase, restore, and refresh via Google Play.",
                        actionLabel = "View in Settings →",
                        actionTarget = "settings",
                    ),
                    WhatsNewItem(
                        id = "n2",
                        icon = Icons.Filled.Lock,
                        title = "ProGate paywall interceptor",
                        description = "Any AI feature that needs Pro or a BYOK key now shows a smart sheet: \"Go Pro\" or \"Use your own API key\". One consistent experience app-wide.",
                    ),
                    WhatsNewItem(
                        id = "n3",
                        icon = Icons.Filled.Palette,
                        title = "Shared design system",
                        description = "DM Sans typography, TwoBits shape scale, and unified color tokens are now shared across Scrybe and Shelf Snap via shared:design.",
                    ),
                    WhatsNewItem(
                        id = "n4",
                        icon = Icons.Filled.LocationOn,
                        title = "Location tagging",
                        description = "Each recording can be tagged with a place name automatically. Enable or disable in Settings → Intelligence.",
                        actionLabel = "View in Settings →",
                        actionTarget = "settings",
                    ),
                    WhatsNewItem(
                        id = "n5",
                        icon = Icons.Filled.RecordVoiceOver,
                        title = "Speaker identification",
                        description = "Multiple voices in a recording are now colour-coded in both the waveform and the transcript. Tap any speaker segment to rename or merge speakers.",
                    ),
                    WhatsNewItem(
                        id = "n6",
                        icon = Icons.Filled.FolderOpen,
                        title = "Folder organisation",
                        description = "Group recordings into folders. Create, rename, move and delete folders from the session list. Expand/collapse with a tap.",
                    ),
                ),
            ),
            WhatsNewCategory(
                id = "improve",
                label = "Improvements",
                icon = Icons.Filled.TrendingUp,
                items = listOf(
                    WhatsNewItem(
                        id = "i1",
                        icon = Icons.Filled.DarkMode,
                        title = "System / Light / Dark theme",
                        description = "Theme mode is now fully wired in Settings — choose System default, Light, or Dark. Persists across restarts.",
                        actionLabel = "View in Settings →",
                        actionTarget = "settings",
                    ),
                    WhatsNewItem(
                        id = "i2",
                        icon = Icons.Filled.Key,
                        title = "Smarter API key routing",
                        description = "BYOK keys go directly to the provider. Pro keys are routed via api.twobits.app with spend tracking and rate limiting.",
                    ),
                ),
            ),
            WhatsNewCategory(
                id = "fix",
                label = "Bug Fixes",
                icon = Icons.Filled.BuildCircle,
                items = listOf(
                    WhatsNewItem(
                        id = "f1",
                        icon = Icons.Filled.Code,
                        title = "Settings screen compile errors resolved",
                        description = "Missing Compose imports removed. Duplicate AutoAwesome import fixed.",
                    ),
                ),
            ),
        ),
    ),
    WhatsNewRelease(
        version = "1.0.0",
        date = "Jan 2026",
        categories = listOf(
            WhatsNewCategory(
                id = "new",
                label = "Initial Release",
                icon = Icons.Filled.RocketLaunch,
                items = listOf(
                    WhatsNewItem(
                        id = "n1",
                        icon = Icons.Filled.Mic,
                        title = "Record, transcribe, transform",
                        description = "Six recording modes — Journal, Meeting, Voice memo, Brainstorm, Interview, Lecture. Each shapes the AI pipeline differently.",
                    ),
                    WhatsNewItem(
                        id = "n2",
                        icon = Icons.Filled.Tune,
                        title = "AI Profiles",
                        description = "Reusable transform pipelines. Save a prompt + destination as a profile, then run it on any session in one tap.",
                        actionLabel = "View Profiles →",
                        actionTarget = "profiles",
                    ),
                    WhatsNewItem(
                        id = "n3",
                        icon = Icons.Filled.Psychology,
                        title = "Multi-provider support",
                        description = "OpenAI (GPT-4o + Whisper), local on-device Whisper and Gemma. Swap providers any time in Settings.",
                        actionLabel = "View in Settings →",
                        actionTarget = "settings",
                    ),
                ),
            ),
        ),
    ),
)

@Composable
fun ScrybeWhatsNewScreen(
    onBack: () -> Unit,
    onNavigate: (target: String) -> Unit,
) {
    WhatsNewScreenLayout(
        title = "Version history",
        releases = SCRYBE_RELEASES,
        onBack = onBack,
        onNavigate = onNavigate,
    )
}
