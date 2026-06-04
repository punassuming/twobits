# Agent instructions for the TwoBits monorepo

This file is the authoritative instruction source for all AI coding agents (Claude, Copilot, Codex, etc.) working in this repository. It supersedes any older per-app instruction files.

---

## Repository layout

```
apps/
  scrybe/          — Scrybe Android app (voice recording + Whisper transcription)
  shelf-snap/      — Shelf Snap Android app (camera inventory + price research)
shared/            — Gradle composite build: shared library modules (billing, common, api-keys, network, design)
scrybe-re-think/   — Design exploration docs
```

The managed API key proxy lives in the separate **[punassuming/twobits-worker](https://github.com/punassuming/twobits-worker)** repository and is deployed independently via Cloudflare Workers.

Both Android apps share the same tech stack and billing infrastructure but are independently buildable Gradle projects.

---

## Session setup — run once after cloning

```bash
git config core.hooksPath .githooks
```

This activates the tracked pre-commit hook in `.githooks/pre-commit`. It runs changelog validation, manifest validation, and standalone `ktlint --format` + check on every staged `.kt`/`.kts` file — **no Android SDK required**. ktlint 1.5.0 is self-installed on first run if not already on PATH or at `~/.local/bin/ktlint`.

---

## Mandatory pre-commit checks

**Before every `git commit`, run ALL of the following and ensure they pass. Do not commit or push code that fails any check.**

### Scrybe (`apps/scrybe/`)

```bash
cd apps/scrybe

# 1. Validate changelog structure
python3 scripts/manage-changelog.py validate --changelog CHANGELOG.md

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

One-liner:

```bash
python3 scripts/manage-changelog.py validate --changelog CHANGELOG.md && \
  python3 scripts/validate-manifests.py && \
  ./gradlew ktlintFormat assembleDebug testDebugUnitTest lint ktlintCheck detekt --no-daemon
```

### Shelf Snap (`apps/shelf-snap/`)

```bash
cd apps/shelf-snap

# Fast checks (no Android SDK required)
python3 ../scrybe/scripts/manage-changelog.py validate --changelog CHANGELOG.md
python3 ../scrybe/scripts/validate-manifests.py --root .

# Full build + test + lint (requires Android SDK)
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
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

### 3. Coroutine scope inside Composable animation effects

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

### 4. Missing Gradle module dependency → Hilt `error.NonExistentClass`

Every cross-module import requires `implementation(project(":module:name"))` in the importing module's `build.gradle.kts`. Hilt's annotation processor fails at compile time with `error.NonExistentClass` without naming the missing module.

**Rule:** when you add `import dev.scrybe.X.Y.SomeClass`, confirm `implementation(project(":X:Y"))` is in the importing module's `build.gradle.kts`. If missing, add it.

Package → Gradle module:

| Package prefix | Gradle module |
|---|---|
| `dev.scrybe.core.billing` | `:core:billing` |
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

### 5. Non-existent Android SDK members

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

### 6. `AnimatedVisibility` receiver ambiguity inside Column > Box

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

## Branch management — always rebase onto origin/main before starting work AND before pushing

**At the start of every session**, before writing any code, fetch and rebase onto `origin/main`:

```bash
git fetch origin main
git rebase origin/main
```

Skipping this step causes merge conflicts on the PR even when the code itself is correct. Always establish a clean base first, then make changes.

**Before every push**, rebase again to pick up any commits that landed on main while you were working:

```bash
git fetch origin main
git rebase origin/main
git push -u origin <branch-name> --force-with-lease
```

Never push a branch that diverged from a stale base. If the rebase produces a conflict, resolve it, `git add` the affected files, and run `git rebase --continue` before pushing.

---

## Mandatory changelog updates

### Scrybe
Update `apps/scrybe/CHANGELOG.md` `## Unreleased` section before any commit destined for `main`. Add bullets under `### Features`, `### Improvements`, or `### Fixes`.

Validate with: `python3 apps/scrybe/scripts/manage-changelog.py validate --changelog apps/scrybe/CHANGELOG.md`

### Shelf Snap
Update `apps/shelf-snap/CHANGELOG.md` `## Unreleased` section before any commit destined for `main`. Format is identical to the Scrybe changelog.

Validate with: `python3 apps/scrybe/scripts/manage-changelog.py validate --changelog apps/shelf-snap/CHANGELOG.md`

The CI `changelog` job blocks merges when the changelog was not updated alongside other tracked changes. Do **not** invent version numbers — the release workflow promotes `Unreleased` automatically.

---

## Detekt rules (Scrybe — zero tolerance, `maxIssues = 0`)

- Functions: ≤ 60 lines
- Parameter lists: ≤ 8 items
- Return count per function: ≤ 4
- Magic numbers: disabled (use named constants anyway)

---

## CI pipeline

### Scrybe
- **`scrybe-ci.yml`** — runs on push to `main`, `copilot/**`, `claude/**`, and on PRs targeting `main` (path-filtered to Scrybe files). Jobs: `validate` (changelog + manifests) → `build` (assembleDebug, testDebugUnitTest, lint, ktlintCheck, detekt). A `detect-changes` job skips the build step for version-only commits by inheriting the previous `android/verified` commit status.
- **`scrybe-release.yml`** — triggers on successful `scrybe-ci.yml` run on `main`. Computes next semantic version from conventional commits, promotes changelog, bumps app version, creates tag and GitHub Release with signed APK/AAB.

### Shelf Snap
- **`shelf-snap-ci.yml`** — runs on push to `main`, `copilot/**`, `claude/**`, and on PRs targeting `main` (path-filtered to Shelf Snap files). Jobs: `validate` (changelog + manifests) → `build` (assembleDebug, testDebugUnitTest, lintDebug).
- **`shelf-snap-release.yml`** — triggers on successful `shelf-snap-ci.yml` run on `main`. Computes next version, promotes changelog, bumps app version, creates tag and GitHub Release with signed APK/AAB.

### Shared
- **`reusable-validate.yml`** — shared changelog validation and manifest validation logic, called by both CI workflows.
- **`pages.yml`** — deploys the `docs/` folder to GitHub Pages on push to `main`.

Signing secrets (same names for both apps): `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. If not configured, the release workflow generates a one-off keystore so the APK is still installable.
