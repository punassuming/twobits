# Agent instructions for Scrybe

This file contains mandatory instructions for AI coding agents (Copilot, Codex, Claude, etc.) working in this repository.

## Session setup — run once after cloning

```bash
git config core.hooksPath .githooks
```

This activates the tracked pre-commit hook in `.githooks/pre-commit`. It runs changelog validation, manifest validation, and standalone `ktlint --format` + check on every staged `.kt`/`.kts` file — **no Android SDK required**. ktlint 1.5.0 is self-installed on first run if not already on PATH or at `~/.local/bin/ktlint`.

---

## Mandatory pre-commit checks

**Before every `git commit`, run ALL of the following from `apps/android-whispering/` and ensure they pass. Do not commit or push code that fails any check.**

```bash
cd apps/android-whispering

# 1. Validate changelog structure
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md

# 2. Validate all AndroidManifest.xml files
python3 scripts/validate-manifests.py

# 3. Auto-fix Kotlin formatting (must run BEFORE ktlintCheck)
./gradlew ktlintFormat --no-daemon

# 4. Build debug APK — catches compile errors, Hilt wiring, missing dependencies
./gradlew assembleDebug --no-daemon

# 5. Unit tests
./gradlew testDebugUnitTest --no-daemon

# 6. Android Lint
./gradlew lint --no-daemon

# 7. Kotlin formatting check (after auto-fix)
./gradlew ktlintCheck --no-daemon

# 8. Static analysis
./gradlew detekt --no-daemon
```

One-liner equivalent:

```bash
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md && \
  python3 scripts/validate-manifests.py && \
  ./gradlew ktlintFormat assembleDebug testDebugUnitTest lint ktlintCheck detekt --no-daemon
```

---

## Known CI failure patterns — read carefully before writing code

These are the exact mistakes that have repeatedly broken CI on this project.

### 1. KtLint `import-ordering` — imports must be in strict lexicographic order

All imports must be sorted lexicographically with no blank lines between them. `java`, `javax`, `kotlin`, and aliases go at the end. IDEs insert new imports at the cursor, not in sorted order — always auto-fix before committing.

```kotlin
// WRONG — Notifications (N) sits between AutoAwesome (A) and CloudDone (C)
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CloudDone

// CORRECT
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
```

**Run `./gradlew ktlintFormat` to auto-fix.** The pre-commit hook also runs `ktlint --format` on staged files automatically.

### 2. KtLint `multiline-expression-wrapping` (most frequent)

When the right-hand side of an assignment or boolean expression spans multiple lines, the **operator must stay on the first line**; the continuation indents by one extra level.

```kotlin
// WRONG — CI will reject this
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

This applies to `in`, `==`, `!=`, `&&`, `||`, `+`, `-`, etc. **Run `./gradlew ktlintFormat` to auto-fix before checking in.**

### 2. Coroutine scope inside Composable animation effects

Inside a `LaunchedEffect` block you are already in a coroutine, but bare `launch {}` requires a `CoroutineScope` receiver. Wrap parallel launches in `coroutineScope {}`.

```kotlin
// WRONG — compile error: unresolved reference: launch
LaunchedEffect(isActive) {
    launch { scale.animateTo(2f, ...) }
    launch { alpha.animateTo(0f, ...) }
}

// CORRECT
LaunchedEffect(isActive) {
    coroutineScope {
        launch { scale.animateTo(2f, ...) }
        launch { alpha.animateTo(0f, ...) }
    }
}
```

### 3. Missing Gradle module dependency → Hilt `error.NonExistentClass`

Every cross-module import requires `implementation(project(":module:name"))` in the importing module's `build.gradle.kts`. Hilt's annotation processor fails at compile time with `error.NonExistentClass` without naming the missing module.

**Rule:** when you add `import dev.scrybe.X.Y.SomeClass`, confirm `implementation(project(":X:Y"))` is in the importing module's `build.gradle.kts`. If missing, add it.

Package → Gradle module:
- `dev.scrybe.core.common` → `:core:common`
- `dev.scrybe.core.model` → `:core:model`
- `dev.scrybe.core.database` → `:core:database`
- `dev.scrybe.core.datastore` → `:core:datastore`
- `dev.scrybe.core.audio` → `:core:audio`
- `dev.scrybe.core.network` → `:core:network`
- `dev.scrybe.core.transcription` → `:core:transcription`
- `dev.scrybe.core.transforms` → `:core:transforms`
- `dev.scrybe.core.export` → `:core:export`
- `dev.scrybe.feature.capture` → `:feature:capture`
- `dev.scrybe.feature.history` → `:feature:history`
- `dev.scrybe.feature.profiles` → `:feature:profiles`
- `dev.scrybe.feature.session-detail` → `:feature:session-detail`
- `dev.scrybe.feature.settings` → `:feature:settings`

### 4. Non-existent Android SDK members

Do not use Android API members that sound plausible but do not exist.

```kotlin
// WRONG — METADATA_KEY_CHANNEL_COUNT does not exist in any Android SDK
retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CHANNEL_COUNT)

// CORRECT
val extractor = MediaExtractor()
extractor.setDataSource(path)
extractor.selectTrack(0)
val channels = extractor.getTrackFormat(0).getInteger(MediaFormat.KEY_CHANNEL_COUNT)
```

Verify unfamiliar constants in the Android API reference or by grepping the existing codebase before using them.

### 5. `AnimatedVisibility` receiver ambiguity inside Column > Box

```kotlin
// WRONG — ambiguous between ColumnScope and BoxScope
Column {
    Box {
        AnimatedVisibility(visible = show) { ... }
    }
}

// CORRECT — qualify with the inner scope
Column {
    Box {
        this@Box.AnimatedVisibility(visible = show) { ... }
    }
}
```

---

## Mandatory changelog updates

Update `CHANGELOG.md` `## Unreleased` → `### Features`, `### Improvements`, or `### Fixes` before any commit destined for `main`. The CI `changelog` job blocks merges when `CHANGELOG.md` was not updated alongside other tracked changes. Do not invent version numbers — the release workflow promotes `Unreleased` automatically.

---

## Detekt rules (zero tolerance — `maxIssues = 0`)

- Functions: ≤ 60 lines
- Parameter lists: ≤ 8 items
- Return count per function: ≤ 4

---

## CI pipeline

GitHub Actions runs on push to `main`, `copilot/**`, `claude/**`, and on pull requests targeting `main`. All jobs (changelog validation → manifest validation → `assembleDebug testDebugUnitTest lint ktlintCheck detekt`) must pass for a merge to succeed. Release APK assembly is in the separate release workflow that runs on successful pushes to `main`.
