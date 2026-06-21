# Agent instructions for the TwoBits monorepo

This file is the authoritative instruction source for AI coding agents working in this repository. It supersedes any older per-app instruction files.

> **Claude Code users:** also read [`CLAUDE.md`](CLAUDE.md) for Claude-specific session setup and active hooks.

---

## Repository layout

```
apps/
  scrybe/          — Scrybe Android app (voice recording + Whisper transcription)
  shelf-snap/      — Shelf Snap Android app (camera inventory + price research)
  price-drop/      — PriceDrop Android app (price tracking + deal alerts)
shared/            — Gradle composite build: shared library modules (billing, common, api-keys, network, design)
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
python3 ../../scripts/manage-changelog.py validate --changelog CHANGELOG.md

# 2. Validate all AndroidManifest.xml files
python3 ../../scripts/validate-manifests.py

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
python3 ../../scripts/manage-changelog.py validate --changelog CHANGELOG.md && \
  python3 ../../scripts/validate-manifests.py && \
  ./gradlew ktlintFormat assembleDebug testDebugUnitTest lint ktlintCheck detekt --no-daemon
```

### Shelf Snap (`apps/shelf-snap/`)

```bash
cd apps/shelf-snap

# Fast checks (no Android SDK required)
python3 ../../scripts/manage-changelog.py validate --changelog CHANGELOG.md
python3 ../../scripts/validate-manifests.py --root .

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

### Changelog reconciliation after rebasing over a release commit

When a release workflow fires on `main`, it promotes `## Unreleased` bullets to a versioned section and commits that back to `main` (e.g. `## 1.6.0 (2026-06-04)`). If your feature branch was rebased onto that main _after_ the release commit landed, the rebase can preserve your old `## Unreleased` bullets verbatim — even though those exact bullets are now in the versioned section. The release workflow will see them and fire a duplicate release.

**After every rebase, check:**

```bash
git log --oneline origin/main | head -5   # look for a "chore: prepare release" commit
```

If a release was cut since your branch diverged, open the relevant `CHANGELOG.md` and manually remove any `## Unreleased` bullets that already appear in a versioned section below. Leave only bullets that are genuinely new in your branch. If there are no new bullets, add a real one before merging so the release workflow has something to promote.

The `has-new-unreleased-since-tag` check in both release workflows enforces this automatically, but fixing it in the branch before merge produces a cleaner changelog history.

---

## Mandatory changelog updates

> **Changelogs are written manually — nothing generates them from git commits.**
> `manage-changelog.py` only validates structure. The release workflow only *promotes* `## Unreleased` → a versioned section. All content must be written by the agent or developer making the change.

Update the relevant `CHANGELOG.md` `## Unreleased` section before any commit destined for `main`. Add entries under `### Features`, `### Improvements`, or `### Fixes`.

Validate commands:
```bash
python3 scripts/manage-changelog.py validate --changelog apps/scrybe/CHANGELOG.md
python3 scripts/manage-changelog.py validate --changelog apps/shelf-snap/CHANGELOG.md
```

The CI `changelog` job blocks merges when the changelog was not updated alongside other tracked changes. Do **not** invent version numbers — the release workflow promotes `Unreleased` automatically.

### Changelog entry format — how entries appear in-app

Changelog entries are parsed and displayed in the **What's New** screen inside each app. Two formats are supported; choose based on whether the change warrants detail:

#### Format A — any user-visible change (Features and Improvements)

Use Format A whenever the change is something a user would notice in the app — new screens, redesigned UI, new settings, changed behaviour, performance improvements the user feels, etc. This applies to entries in **both** `### Features` and `### Improvements`.

```markdown
**[Screen or component]** — [short one-line description]:
* detail bullet one
* detail bullet two
* detail bullet three
```

- The `**bold**` text becomes the collapsed item title visible at all times.
- The em-dash `—` (U+2014, not a hyphen) separates the component name from the description; both are required.
- Sub-bullets (`* `) accumulate as the expanded description, revealed when the user taps the row.
- End the bold line with `:` when sub-bullets follow; omit it for a one-liner with no sub-bullets.
- **Blank line required** between consecutive bold-title items, and between a bold-title item and any following plain bullets in the same section. Without it the parser merges the next bullet into the preceding item's description.

```markdown
### Features

**Camera** — viewfinder redesign:
* close and flash controls overlaid on the viewfinder surface
* teal L-bracket corner guides frame the subject

**Settings** — AI configuration card:
* prominent primaryContainer card at the top of Settings

### Improvements

**Item Detail** — visual polish:
* brand · model subtitle uses middle-dot separator
* AI confidence badge replaced with a primaryContainer pill

* CI no longer fires duplicate runs — push trigger restricted to main
```

#### Format B — internal/infra change only (flat non-expandable row)

Use Format B **only** when the change is invisible to the user: CI tweaks, build fixes, dependency bumps, tooling changes, ProGuard rules, release workflow adjustments. If a user would notice it, use Format A instead.

```markdown
* short description of what changed and why
```

- Renders as a non-interactive single-line row with no expand chevron.
- Can appear anywhere in a section, mixed with Format A entries (separated by a blank line from any preceding bold-title block).

#### Category → icon mapping in the WhatsNew screen

The section heading determines the icon shown on each category row:

| `### ` heading | Displayed label | Icon |
|---|---|---|
| `### Features` | Features & Enhancements | ✨ AutoAwesome |
| `### Improvements` | Improvements | 📈 TrendingUp |
| `### Fixes` | Bug Fixes | 🔧 BuildCircle |
| `### Initial Release` / `### Launch` | (heading as-is) | 🚀 RocketLaunch |

#### What the CI validates

`manage-changelog.py has-unreleased-bullets` (used by the release gate) accepts a section as non-empty when it contains **either** a plain `* `/`- ` bullet **or** a `**bold**` title line. A bold-title item with no sub-bullets is valid release content.

`manage-changelog.py validate` checks structure only: `# Changelog` header, exactly one `## Unreleased` section first, and the three required sub-headings in order.

---

## Commit message format

Both release workflows compute the next semantic version from conventional commit prefixes. Use the correct prefix on every commit.

| Prefix | Version bump | When to use |
|---|---|---|
| `feat:` | minor — x.**Y**.0 | new user-visible feature or screen |
| `fix:` | patch — x.y.**Z** | bug fix visible to users |
| `chore:` | none | tooling, CI, dependencies, build config |
| `refactor:` | none | code restructuring with no behaviour change |
| `ci:` | none | workflow/pipeline changes only |
| `docs:` | none | documentation only |
| `BREAKING CHANGE:` in commit footer | major — **X**.0.0 | incompatible data/API change |

**Rules:**
- One prefix per commit. Use the highest-impact prefix when a commit mixes concerns (e.g. a `feat:` that also tidies code is still `feat:`).
- `chore:` / `refactor:` / `ci:` commits do **not** trigger a release on their own. If a changelog `## Unreleased` section has bullets and a `feat:` or `fix:` commit lands on `main`, the release workflow fires and promotes those bullets.
- Do not use `feat:` or `fix:` for pure infra work even if it feels significant — the prefix drives automated versioning, not importance.

---

## Detekt rules (Scrybe — zero tolerance, `maxIssues = 0`)

- Functions: ≤ 60 lines
- Parameter lists: ≤ 8 items
- Return count per function: ≤ 4
- Magic numbers: disabled (use named constants anyway)

---

## CI pipeline

Each app's CI and release workflow is a thin caller that passes app-specific inputs to shared reusable workflows.

### Per-app CI workflows (thin callers)
- **`scrybe-ci.yml`** — path-filtered to Scrybe files. Jobs: `validate` → `detect-changes` (skips build for version-only commits) → `build`.
- **`shelf-snap-ci.yml`** — path-filtered to Shelf Snap files. Jobs: `validate` → `build`.
- **`pricedrop-ci.yml`** — path-filtered to PriceDrop files. Jobs: `validate` → `build`.

### Per-app release workflows (thin callers)
Each triggers on its respective CI passing on `main` and via `workflow_dispatch`. `rebuild_for_tag` dispatches the rebuild job instead.
- **`scrybe-release.yml`** — `tag_prefix: scrybe-v`, `default_bump: minor` (Scrybe gets minor bumps for new features)
- **`shelf-snap-release.yml`** — `tag_prefix: shelf-snap-v`, `default_bump: patch`
- **`pricedrop-release.yml`** — `tag_prefix: pricedrop-v`, `default_bump: patch`

### Shared reusable workflows
- **`reusable-validate.yml`** — changelog structure + update enforcement, manifest validation. Called by all three CI workflows.
- **`reusable-build.yml`** — Gradle build + test + lint + ktlint + detekt. Inputs: `app_root`, `app_name`, `gradle_tasks`, `use_retry_script`.
- **`reusable-release.yml`** — full release pipeline: stale-SHA check, changelog gate, semver tagging, signing, APK build, GitHub Release. Inputs: `app_name`, `app_root`, `tag_prefix`, `default_bump`, `keystore_alias`, `keystore_storepass`, `use_retry_script`, `head_sha`, `rebuild_for_tag`.
- **`pages.yml`** — deploys the `docs/` folder to GitHub Pages on push to `main`.

Signing secrets (same names for both apps): `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. If not configured, the release workflow generates a one-off keystore so the APK is still installable.
