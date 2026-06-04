# GitHub Copilot instructions for TwoBits

## Session setup — run once after cloning

```bash
git config core.hooksPath .githooks
```

This activates the tracked pre-commit hook in `.githooks/pre-commit`. It enforces changelog and manifest validity, and runs standalone `ktlint --format` + check on staged Kotlin files — **no Android SDK required**. ktlint 1.5.0 self-installs on first run.

---

## Pre-commit checks (mandatory)

Before every `git commit`, run the following and ensure **all checks pass**. Do not commit code that fails any of these.

### Scrybe (`apps/scrybe/`)

```bash
cd apps/scrybe

# Validate changelog and manifests
python3 scripts/manage-changelog.py validate --changelog CHANGELOG.md
python3 scripts/validate-manifests.py

# Auto-fix formatting FIRST, then build + test + lint
./gradlew ktlintFormat assembleDebug testDebugUnitTest lint ktlintCheck detekt --no-daemon
```

### Shelf Snap (`apps/shelf-snap/`)

```bash
cd apps/shelf-snap

python3 ../scrybe/scripts/manage-changelog.py validate --changelog CHANGELOG.md
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

`ktlintFormat` **must come before** `ktlintCheck` — format first, then verify.

---

## Known CI failure patterns — fix before committing

### 1. KtLint `import-ordering`

Imports must be in strict lexicographic order with no blank lines between them (`java`/`javax`/`kotlin`/aliases last). IDEs insert imports at the cursor — always run `ktlintFormat` after adding imports.

```kotlin
// WRONG
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications   // N out of order
import androidx.compose.material.icons.filled.CloudDone

// CORRECT
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
```

### 2. KtLint `multiline-expression-wrapping` (most frequent failure)

When the RHS of an assignment/condition spans multiple lines, the **operator stays on line 1** and the continuation indents one extra level.

```kotlin
// WRONG
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

Applies to `in`, `==`, `&&`, `||`, `+`, etc. Run `./gradlew ktlintFormat` to auto-fix.

### 3. Coroutine launches inside `LaunchedEffect`

Bare `launch {}` inside a `LaunchedEffect` requires a `CoroutineScope` receiver. Use `coroutineScope {}`.

```kotlin
// WRONG — compile error
LaunchedEffect(key) {
    launch { animA.animateTo(...) }
    launch { animB.animateTo(...) }
}

// CORRECT
LaunchedEffect(key) {
    coroutineScope {
        launch { animA.animateTo(...) }
        launch { animB.animateTo(...) }
    }
}
```

### 4. Missing Gradle module dependency → `error.NonExistentClass`

Every `import dev.scrybe.X.Y.*` needs `implementation(project(":X:Y"))` in the importing module's `build.gradle.kts`. Hilt fails silently with `error.NonExistentClass` when the dep is absent.

Package → module: `core.common`→`:core:common` · `core.model`→`:core:model` · `core.database`→`:core:database` · `core.datastore`→`:core:datastore` · `core.audio`→`:core:audio` · `core.network`→`:core:network` · `core.transcription`→`:core:transcription` · `core.transforms`→`:core:transforms` · `core.export`→`:core:export` · `feature.capture`→`:feature:capture` · `feature.history`→`:feature:history` · `feature.profiles`→`:feature:profiles` · `feature.session-detail`→`:feature:session-detail` · `feature.settings`→`:feature:settings`

### 5. Non-existent Android API members

Verify constants in the Android API reference before use. Example of a landmine:

```kotlin
// WRONG — does not exist
MediaMetadataRetriever.METADATA_KEY_CHANNEL_COUNT

// CORRECT
extractor.getTrackFormat(0).getInteger(MediaFormat.KEY_CHANNEL_COUNT)
```

### 6. `AnimatedVisibility` inside Column > Box

Qualify with the inner scope to resolve the `ColumnScope`/`BoxScope` ambiguity:

```kotlin
// WRONG — ambiguous
Column { Box { AnimatedVisibility(visible) { ... } } }

// CORRECT
Column { Box { this@Box.AnimatedVisibility(visible) { ... } } }
```

---

## Changelog (required for every PR)

Each app has its own changelog:
- Scrybe: `apps/scrybe/CHANGELOG.md`
- Shelf Snap: `apps/shelf-snap/CHANGELOG.md`

Update the relevant `## Unreleased` section under `### Features`, `### Improvements`, or `### Fixes` before any commit targeting `main`. The CI changelog job will block the PR otherwise.

---

## Architecture reference

- Scrybe project root: `apps/scrybe/`
- Shelf Snap project root: `apps/shelf-snap/`
- Scrybe modules (17): `:app`, `:core:{common,model,database,datastore,audio,network,transcription,transforms,export}`, `:feature:{capture,history,session-detail,profiles,settings}`, `:service:recording`, `:workers`
- Hilt 2.51.1 · Kotlin 1.9.25 · AGP 8.7.3 · minSdk 26 / targetSdk 35 · Jetpack Compose
- Detekt: zero tolerance (`maxIssues = 0`) — functions ≤ 60 lines, params ≤ 8, returns ≤ 4

See `CONTRIBUTING.md` and `AGENTS.md` for full setup and PR checklist.
