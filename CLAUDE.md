# Claude Code instructions for Scrybe

## Project layout

Android app at `apps/android-whispering/`. All Gradle commands run from that directory.

17 modules: `:app` | `:core:{common,model,database,datastore,audio,network,transcription,transforms,export}` | `:feature:{capture,history,session-detail,profiles,settings}` | `:service:recording` | `:workers`

Stack: Kotlin 1.9.25 · AGP 8.7.3 · Jetpack Compose · Hilt 2.51.1 · Room 2.6.1 · minSdk 26 / targetSdk 35

## Verification — run before every commit

From `apps/android-whispering/`:

```bash
# Fast checks (no Android SDK required)
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md
python3 scripts/validate-manifests.py

# Full build + test + lint (requires Android SDK / CI environment)
./gradlew assembleDebug testDebugUnitTest lint ktlintFormat ktlintCheck detekt --no-daemon
```

`ktlintFormat` must come **before** `ktlintCheck` — format first, then verify.

The CI runs `assembleDebug testDebugUnitTest lint ktlintCheck detekt` on every push to `main`, `copilot/**`, and `claude/**`. A failing CI blocks merges.

## Kotlin / Compose rules — these are the patterns that keep breaking CI

### 1. KtLint `import-ordering` — imports must be in strict lexicographic order

All imports must be sorted lexicographically with no blank lines between them. `java`, `javax`, `kotlin`, and aliases go at the end.

```kotlin
// WRONG — Notifications (N) is out of order between AutoAwesome (A) and CloudDone (C)
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CloudDone

// CORRECT — strict A–Z order
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
```

IDEs often insert new imports at the cursor position rather than in sorted order. **Always run `ktlint --format` or `./gradlew ktlintFormat` after adding imports.**

### 2. KtLint `multiline-expression-wrapping` — the most common failure

When the right-hand side of an assignment or condition would make the line long, the **operator stays on the first line** and the continuation indents by one extra level.

```kotlin
// WRONG — KtLint rejects this
val showBar =
    currentRoute in setOf(
        Screen.A.route,
        Screen.B.route,
    )

// CORRECT
val showBar =
    currentRoute in
        setOf(
            Screen.A.route,
            Screen.B.route,
        )
```

Same rule applies to `==`, `&&`, `||`, `+`, etc. when splitting across lines.

**Always run `./gradlew ktlintFormat` to auto-fix before checking in.**

### 2. Coroutine launches inside Composable animation callbacks

Inside `LaunchedEffect` or `Animatable` effect blocks you are already in a coroutine, but `launch {}` at the top level of a lambda that is not itself a `CoroutineScope` receiver is unresolved.

```kotlin
// WRONG — bare launch {} is not in scope inside animatable effect callback
LaunchedEffect(isActive) {
    launch { burstScale.animateTo(2.2f, ...) }
    launch { burstAlpha.animateTo(0f, ...) }
}

// CORRECT — wrap in coroutineScope to open a child scope
LaunchedEffect(isActive) {
    coroutineScope {
        launch { burstScale.animateTo(2.2f, ...) }
        launch { burstAlpha.animateTo(0f, ...) }
    }
}
```

### 3. Missing Gradle module dependency (causes Hilt `error.NonExistentClass`)

Every `import dev.scrybe.core.*` or `import dev.scrybe.feature.*` in a module requires a matching `implementation(project(":path:name"))` in that module's `build.gradle.kts`. Hilt does annotation processing at compile time and fails with `error.NonExistentClass` when the dependency is absent — the error message does not name the missing module.

**Checklist when adding a cross-module import:**
1. Find which module owns the class: match the package `dev.scrybe.X.Y` → module `:X:Y`.
2. Open the importing module's `build.gradle.kts`.
3. Add `implementation(project(":X:Y"))` under `dependencies {}` if it is not already there.
4. Run `./gradlew assembleDebug` to confirm.

Module → Gradle path reference:
| Package prefix | Gradle module |
|---|---|
| `dev.scrybe.core.common` | `:core:common` |
| `dev.scrybe.core.model` | `:core:model` |
| `dev.scrybe.core.database` | `:core:database` |
| `dev.scrybe.core.datastore` | `:core:datastore` |
| `dev.scrybe.core.audio` | `:core:audio` |
| `dev.scrybe.core.network` | `:core:network` |
| `dev.scrybe.core.transcription` | `:core:transcription` |
| `dev.scrybe.core.transforms` | `:core:transforms` |
| `dev.scrybe.core.export` | `:core:export` |
| `dev.scrybe.feature.capture` | `:feature:capture` |
| `dev.scrybe.feature.history` | `:feature:history` |
| `dev.scrybe.feature.profiles` | `:feature:profiles` |
| `dev.scrybe.feature.session-detail` | `:feature:session-detail` |
| `dev.scrybe.feature.settings` | `:feature:settings` |

### 4. Android API existence — verify before using

Do not use Android SDK members that sound plausible but do not exist. Known landmine:

```kotlin
// WRONG — this constant does not exist in any Android SDK version
retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CHANNEL_COUNT)

// CORRECT — use MediaExtractor + MediaFormat
val extractor = MediaExtractor()
extractor.setDataSource(path)
extractor.selectTrack(0)
val format = extractor.getTrackFormat(0)
val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
```

When in doubt, check the [Android API reference](https://developer.android.com/reference) or grep the existing codebase for usage examples.

### 5. `AnimatedVisibility` implicit-receiver ambiguity

`AnimatedVisibility` inside a `Box` that is itself inside a `Column` can trigger an "overload resolution ambiguity" compile error because both `ColumnScope` and `BoxScope` define it.

```kotlin
// WRONG — ambiguous inside Column > Box
Column {
    Box {
        AnimatedVisibility(visible = ...) { ... }
    }
}

// CORRECT — qualify with the outer scope or use a plain Box without ColumnScope
Column {
    Box(modifier = ...) {
        this@Box.AnimatedVisibility(visible = ...) { ... }
    }
}
```

## Detekt rules (zero tolerance — `maxIssues = 0`)

- Functions: ≤ 60 lines
- Parameter lists: ≤ 8 items (functions and constructors)
- Return statements per function: ≤ 4
- Magic numbers: disabled (use named constants anyway for readability)

## Changelog — required for every PR

Update `CHANGELOG.md` `## Unreleased` section before preparing any commit destined for main. Add bullets under `### Features`, `### Improvements`, or `### Fixes` as appropriate.

- Do **not** invent version numbers — the release workflow promotes `Unreleased` automatically.
- The CI `changelog` job will block the PR if `CHANGELOG.md` was not updated when other tracked files changed.
- Validate with: `python3 apps/android-whispering/scripts/manage-changelog.py validate --changelog CHANGELOG.md`
