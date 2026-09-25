# CI/CD Pipeline, Linting & Build-Scripting Audit

**Date**: 2026-09-25  
**Repository**: TwoBits monorepo

## Overview

This audit examines the GitHub Actions workflows, pre-commit hooks, static analysis configuration, build scripts, and release automation for the TwoBits monorepo (Scrybe, Shelf Snap, PriceDrop Android apps plus shared/ modules).

---

## 1. GitHub Actions Workflows

### Per-App CI Workflows

Three nearly-identical CI workflows trigger on push to main and pull requests:
- **scrybe-ci.yml** (.github/workflows/scrybe-ci.yml:1-207)
- **shelf-snap-ci.yml** (.github/workflows/shelf-snap-ci.yml:1-117)
- **pricedrop-ci.yml** (.github/workflows/pricedrop-ci.yml:1-117)

Each contains:
1. **`validate` job** (calls reusable-validate.yml) — validates changelog structure and AndroidManifest.xml files
2. **`detect-changes` job** (scrybe only) — checks if the push is version-only; if so, inherits the previous commit's `android/verified` status to skip redundant builds (.github/workflows/scrybe-ci.yml:49-122)
3. **`build` job** — assembles release APK, runs unit tests, lint, ktlint, and detekt

#### Build Commands Differ Across Apps

- **Scrybe** (.github/workflows/scrybe-ci.yml:159): Uses `ci-gradle-retry.sh` wrapper with exponential backoff for transient network failures
  ```
  bash ../../scripts/ci-gradle-retry.sh assembleRelease testDebugUnitTest sharedUnitTest lint ktlintCheck detekt
  ```
- **Shelf Snap** (.github/workflows/shelf-snap-ci.yml:72) and **PriceDrop** (.github/workflows/pricedrop-ci.yml:72): Direct gradlew calls with `--no-daemon`
  ```
  ./gradlew assembleRelease testDebugUnitTest sharedUnitTest lintRelease ktlintCheck detekt --no-daemon
  ```

**Finding**: Shelf Snap and PriceDrop use `lintRelease` while Scrybe uses `lint` (debug variant). This inconsistency is likely unintentional — Scrybe should probably use `lintRelease` for consistency with the release APK being built.

### Reusable Workflows

**reusable-validate.yml** (.github/workflows/reusable-validate.yml:1-72):
- Validates changelog structure via `manage-changelog.py validate` (line:46)
- Enforces changelog updates via `manage-changelog.py check-updated` (line:53-57)
- Validates AndroidManifest.xml files via `validate-manifests.py` (line:71)
- Properly called by all three CI workflows with app-specific paths

**reusable-build.yml** (.github/workflows/reusable-build.yml:1-110):
- Implements a templated build job with configurable Gradle tasks and retry script option
- **FINDING**: This workflow is **defined but not used** — all three CI workflows duplicate the build logic inline instead of calling this reusable workflow
- Contains parameters for `gradle_tasks`, `use_retry_script`, `app_root`, and `app_name` but none of the CI workflows invoke it

**reusable-release.yml** (.github/workflows/reusable-release.yml:1-509):
- Comprehensive release automation workflow
- Handles version computation with semantic versioning and conventional commits (.github/workflows/reusable-release.yml:151-201)
- Promotes changelog from `## Unreleased` to versioned release section (.github/workflows/reusable-release.yml:266-279)
- Updates app version in build.gradle.kts (.github/workflows/reusable-release.yml:284-308)
- Builds signed APK and AAB, creates GitHub Release
- Detects stale release targets (main has advanced with app-relevant commits) and skips to avoid redundant releases (.github/workflows/reusable-release.yml:88-99)
- Properly called by all three per-app release workflows

### Release Workflows

- **scrybe-release.yml** (.github/workflows/scrybe-release.yml:1-58)
- **shelf-snap-release.yml** (similar structure)
- **pricedrop-release.yml** (similar structure)

All trigger on `workflow_run` after their corresponding CI workflow succeeds on main, plus `workflow_dispatch` for manual rebuilds.

### Other Workflows

**pages.yml** (.github/workflows/pages.yml:1-42):
- Deploys `/docs` directory to GitHub Pages on push to main
- Properly gated and configured with correct permissions

---

## 2. Pre-Commit Hooks

The tracked pre-commit hook at `.githooks/pre-commit` (lines 1-280) is comprehensive and runs **before every commit**:

### Checks Performed (in order)

1. **Changelog structure validation** (lines 26-40):
   - All three app CHANGELOGs validated via `manage-changelog.py validate`
   - Ensures `## Unreleased` section exists with `### Features`, `### Improvements`, `### Fixes` subheadings in order

2. **Changelog update enforcement** (lines 42-102):
   - Blocks commits when code files staged but respective CHANGELOG.md not staged
   - Detects staged changes and enforces new bullets in `## Unreleased` section
   - Applies to all three apps independently

3. **AndroidManifest.xml validation** (lines 104-107):
   - Validates XML structure and proper namespace declarations
   - Runs `validate-manifests.py --root $SCRYBE_DIR` for Scrybe only — **Shelf Snap and PriceDrop manifests are NOT validated locally**

4. **Kotlin formatting via ktlint** (lines 109-155):
   - Auto-downloads ktlint 1.5.0 if not found on PATH or at `~/.local/bin/ktlint`
   - Runs `ktlint --format` on staged `.kt`/`.kts` files in all three apps
   - Re-stages formatted files
   - Verifies no violations remain after auto-fix

5. **Kotlin import completeness** (lines 157-220):
   - Checks for missing imports of Flow extensions (asStateFlow, filterNotNull, etc.)
   - Checks for missing java.* imports in .kts build scripts
   - Hard-coded check list with no mechanism to extend — new extension functions must be manually added to the hook

6. **External dependency URL reachability** (lines 222-233):
   - Verifies GitHub release asset URLs and JitPack artifact URLs before committing
   - Runs `validate-external-urls.py`
   - Uses curl with 15s connection timeout, 20s max time

7. **Detekt static analysis** (lines 235-275):
   - Auto-downloads detekt CLI 1.23.7 if not cached
   - Runs against staged `.kt` files only (build scripts `.kts` excluded)
   - Uses single `detekt.yml` config at repo root
   - Exit code 2 = violations (fails commit); exit code 1 = type-resolution error (warning only)

### Sync Between Local and CI

**FINDING — Critical divergence**:
- Pre-commit hook validates manifests **only for Scrybe** (.githooks/pre-commit:105), but CI validates **for all three apps** (.github/workflows/reusable-validate.yml:71)
- Pre-commit hook runs `lint` (debug) for Scrybe only, but CI runs `lint` or `lintRelease` depending on app
- Pre-commit hook does NOT run `testDebugUnitTest` or `assembleRelease/Debug` — tests only run in CI
- Pre-commit hook uses `detekt.yml` config; CI also uses `detekt.yml` ✓
- Pre-commit hook uses ktlint 1.5.0; CI (via Gradle) uses ktlint plugin bundled with version 12.1.1 — potential version mismatch on ktlint engine

**Finding**: The hook's hard-coded comment at line 279 ("CI also enforces: assembleDebug · testDebugUnitTest · lint · detekt") is outdated — CI runs `assembleRelease` not `assembleDebug`.

---

## 3. Static Analysis Configuration

### detekt.yml (Repo Root)

Located at `/home/user/twobits/detekt.yml` (lines 1-27):

```yaml
build:
  maxIssues: 0          # No issues allowed
  excludeCorrectable: false
config:
  validation: true
complexity:
  LongMethod:
    threshold: 60       # Functions >60 lines fail
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 8
style:
  MagicNumber:
    active: false       # Magic numbers allowed
  ReturnCount:
    max: 4              # Functions can return up to 4 values
```

**Assessment**:
- Reasonable thresholds for a production Android codebase
- `maxIssues: 0` is strict but enforces discipline
- Disabling `MagicNumber` is pragmatic for UI/graphics code
- Single config shared by all three apps and shared modules

### Gradle Integration for Shared Modules

**Finding**: Shared modules are an included build, not subprojects, creating a build configuration puzzle.

**Shared root** (.github/workflows/reusable-validate.yml mentions this indirectly; confirmed in `/home/user/twobits/shared/build.gradle.kts`):
- Aggregate tasks `detekt` and `ktlintCheck` depend on subprojects (lines: "dependsOn(subprojects.map { \"${it.path}:detekt\" })")
- Each app bridges across the included build via `gradle.includedBuild("shared").task(":...")` calls
- Example from `/home/user/twobits/apps/scrybe/build.gradle.kts`: `dependsOn(gradle.includedBuild("shared").task(":ktlintCheck"))`

**Workaround necessity**: The composite build pattern requires explicit bridging because included builds are not subprojects. The comments in shared/build.gradle.kts explain this is intentional to ensure shared modules (used by all three apps) are always linted, even though they live outside each app's directory.

### Android Lint Configuration

**Finding — Significant Gap**:
- CI runs `lintRelease` (or `lint` for Scrybe) on each app's code
- No evidence of Android Lint running against shared modules
- Comment in `/home/user/twobits/shared/debug-log-ui/build.gradle.kts` does NOT mention this as a known issue
- Shared modules include Android components (e.g., `debug-log-ui` is an Android library)
- Android Lint should ideally run against all Android libraries to catch manifest issues, deprecated API usage, etc.

This is a gap — shared modules could fail Android Lint checks that would only surface when integrated into an app's release APK build.

---

## 4. Build Scripts

### scripts/manage-changelog.py (lines 1-433)

**Purpose**: Validate and promote changelog structure.

**Robustness assessment**:
- Proper error handling with try/catch on git commands (lines 417-424)
- Validates changelog structure before any mutations (lines 154-191)
- Clear error messages explaining missing sections and ordering requirements (lines 158-179)
- Command pattern with subcommands (validate, check-updated, check-staged-unreleased-new, promote-release, has-unreleased-bullets, has-new-unreleased-since-tag)
- Duplicate subheading detection prevents merge conflicts and partial releases (lines 193-217)
- Bullet extraction logic correctly handles plain bullets, bold entries, and sub-bullets (lines 325-328)
- **Edge case**: Falls back to CHANGELOG.md seed version if no tags exist (lines 168-173), preventing 0.0.0 releases
- **Weakness**: No validation that the changelog file is encoded in UTF-8 (assumes it, line 136)

### scripts/validate-manifests.py (lines 1-97)

**Purpose**: Validate AndroidManifest.xml files.

**Robustness assessment**:
- Proper directory traversal with pruning for `.` dirs and build output (lines 23-24)
- XML parsing error handling (lines 41-45)
- Checks both XML validity and proper namespace declarations (lines 47-57)
- Clear error messages for missing xmlns:android declaration
- **Limitation**: Only validates manifest structure, not Android-specific constraints (e.g., required permissions, activities, services)

### scripts/validate-external-urls.py (lines 1-114)

**Purpose**: Verify reachability of external dependency URLs before committing.

**Robustness assessment**:
- Proper regex extraction of asset URLs from build files and JitPack dependencies (lines 48-89)
- Curl invocation with proper timeout configuration (15s connection, 20s total; lines 30-39)
- HTTP status detection (status < 400 = reachable; lines 41-45)
- **Finding**: Assumes LIBS_TOML path hardcoded to `apps/scrybe/gradle/libs.versions.toml` (line 19) — should be parameterized or located dynamically
- **Finding**: Only searches Scrybe's build files, not Shelf Snap or PriceDrop — if those apps have different external URLs, they won't be validated
- Graceful fallback when LIBS_TOML not found (lines 68-69)

### scripts/ci-gradle-retry.sh (lines 1-57)

**Purpose**: Retry Gradle builds on transient network failures.

**Robustness assessment**:
- Proper exponential backoff: `sleep_seconds=$((base_backoff_seconds * attempt))` (line 50)
- Transient error detection via grep patterns for common network failures (lines 16-18)
- Clears Gradle download cache before retry to avoid stale state (lines 21-27)
- Configurable via env vars: `SCRYBE_GRADLE_MAX_ATTEMPTS` (default 3), `SCRYBE_GRADLE_RETRY_BACKOFF_SECONDS` (default 15)
- Exit code propagation preserves underlying Gradle status (line 47)
- **Finding**: Max 3 attempts with base backoff 15s = max 90 second wait (15+30+45). For persistent network issues, this may be insufficient
- Only used by Scrybe CI, not Shelf Snap or PriceDrop

### Summary

All scripts use proper error handling and exit codes. Scripts are small and focused. Main weaknesses:
- Hard-coded paths (validate-external-urls.py)
- Limited scope to Scrybe only (validate-external-urls.py, ci-gradle-retry.sh)
- Manifest validation scope doesn't cover all apps locally

---

## 5. Missing Security Checks

### Dependency Scanning

**Finding — Not Configured**:
- No `dependabot.yml` found in `.github/`
- No GitHub Dependabot alerts enabled
- No OWASP dependency-check or similar scanning in workflows
- CI runs builds but does not scan for known vulnerabilities in transitive dependencies

### Secret Scanning

**Finding — Not Configured**:
- No GitHub secret scanning enabled (no `.github/secret_scanning.yml` or repository setting)
- `validate-external-urls.py` does retrieve URLs via curl, but only checks HTTP status, not for exposed credentials in URLs
- Release workflow hardcodes fallback keystore generation when secrets missing (.github/workflows/reusable-release.yml:338-345), but provides no validation that production secrets are actually configured

### Code Scanning / SAST

**Finding — Not Configured**:
- No CodeQL workflow (GitHub Advanced Security)
- No other SAST tooling (SonarQube, Semgrep, etc.)
- Detekt configuration is present but detekt is primarily a style/complexity tool, not a security scanner

### Artifact Signing & Verification

- Release workflow does sign APKs and AABs (lines 325-359 of reusable-release.yml)
- Signing credentials are stored in GitHub secrets
- **Finding**: No verification step — signed artifacts are uploaded but not verified before release

### Recommended Additions

1. Enable GitHub Dependabot for `gradle` and `github-actions` (if not already enabled via org settings)
2. Configure GitHub secret scanning (if not enabled via org)
3. Consider adding CodeQL analysis workflow for Kotlin/Java code
4. Add artifact signature verification in release workflow
5. Audit transitive dependencies periodically using `./gradlew dependencies`

---

## 6. Release Automation

### Version Computation

**Located in**: `.github/workflows/reusable-release.yml:151-201`

**Mechanism**:
- Finds latest tag matching app prefix (`scrybe-v*`, `shelf-snap-v*`, `pricedrop-v*`)
- Extracts base version from highest tag
- Scans commits since that tag for conventional-commit prefixes:
  - `feat:` or `feat(*)` → minor bump
  - `BREAKING CHANGE` or `*!:` → major bump
  - Default (all others) → patch bump (for all three apps)

**Robustness**:
- Handles first-ever release by seeding from CHANGELOG.md version (lines 168-173)
- Version downgrade guard prevents stale tag detection (lines 208-228)
- Re-fetches tags after checkout to catch concurrent release runs (lines 131-134, 161)
- Fails loudly if version computation falls back to stale base (lines 223-225) — explicitly addresses "the 0.0.1 incident"

### Changelog Promotion

**Located in**: `.github/workflows/reusable-release.yml:266-279`

**Mechanism**:
- Calls `manage-changelog.py promote-release` to convert `## Unreleased` section to versioned section (line 273-278)
- Outputs release notes to temp file for GitHub release body (line 278)
- Validates that `## Unreleased` contains at least one bullet before promoting (validate-changelog.py:300-302)

**Known Friction Point**:
- CLAUDE.md notes that release-mid-PR changelog merge conflicts have "caused real merge-conflict issues before"
- Current workflow has no special handling for concurrent release attempts — relies on version downgrade guard to abort

### APK/AAB Building & Signing

**Located in**: `.github/workflows/reusable-release.yml:325-393`

**Mechanism**:
- Decodes KEYSTORE base64 from secrets, validates it before any state changes (lines 237-246)
- Falls back to generating ephemeral keystore if secrets missing (lines 338-345)
- Exports env vars with dual naming conventions so all three apps' signing configs work (lines 350-358)
- Validates keystore before build via keytool import test (lines 360-381)
- Builds `assembleRelease bundleRelease` (lines 383-393)

**Strength**: Keystore validation happens BEFORE any irreversible state (changelog promotion, version commit)

### Stale Release Detection

**Located in**: `.github/workflows/reusable-release.yml:88-99`

**Mechanism**:
- Checks if main has advanced beyond the release target SHA with app-relevant commits
- If stale, skips to avoid redundant releases
- **Correctly scoped**: only checks commits affecting `${{ inputs.app_root }}/CHANGELOG.md`, `${{ inputs.app_root }}/`, and `shared/` — allows concurrent app releases

**Finding**: This allows multiple apps to release independently on the same main, preventing the "one app's release blocks another" problem.

### GitHub Release Creation

**Located in**: `.github/workflows/reusable-release.yml:406-415`

**Uses**: `softprops/action-gh-release@v2` to upload APK and AAB with release notes from changelog

### Rebuild-for-Tag Feature

**Located in**: `.github/workflows/reusable-release.yml:422-508`

**Purpose**: Recovery mechanism for orphaned tags (tag created but APK build failed before upload)

**Usage**: Dispatch `workflow_dispatch` with `rebuild_for_tag` parameter

**Finding**: Useful safety valve, though ideally would not be needed if the main job atomically created both tag and release

### Per-App Release Workflows

All three follow identical pattern:
```yaml
jobs:
  release:
    if: (dispatch with no rebuild_for_tag) || (CI passed on main)
    uses: ./.github/workflows/reusable-release.yml
    with:
      app_name: [Scrybe|Shelf Snap|PriceDrop]
      app_root: apps/[scrybe|shelf-snap|price-drop]
      tag_prefix: [scrybe-v|shelf-snap-v|pricedrop-v]
      default_bump: patch
      keystore_alias: [app-name]
      keystore_storepass: [app_name_ks_pass]
      use_retry_script: true (Scrybe only)
```

**Finding**: Keystore generation relies on hardcoded secrets naming `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — all three apps share the same secrets path, so they would need separate secrets or a shared keystore

---

## 7. Build Performance & Caching

### Gradle Dependency Caching

**GitHub Actions Setup**:
- All CI workflows use `actions/setup-java@v4` with `cache: gradle` option (.github/workflows/scrybe-ci.yml:146, shelf-snap-ci.yml:59, pricedrop-ci.yml:59)
- This enables GitHub's built-in Gradle dependency cache (caches `~/.gradle/wrapper` and `~/.gradle/caches`)

**Assessment**: Standard and effective for reducing download times across runs

### Gradle Configuration Cache

**Enabled**:
- All three apps enable Gradle configuration cache in their gradle.properties files:
  - `/apps/scrybe/gradle.properties`: `org.gradle.configuration-cache=true` with `org.gradle.configuration-cache.problems=warn`
  - `/apps/shelf-snap/gradle.properties`: Same
  - `/apps/price-drop/gradle.properties`: Same

**Impact**: Significant build time reduction (15-30% typical) by caching the task graph after the configuration phase completes

**Assessment**: Good practice, properly configured with warnings rather than errors to allow incremental adoption

### Gradle Daemon

**Status**: Explicitly disabled in CI
- All direct gradlew invocations use `--no-daemon` flag
- Reduces resource usage in ephemeral CI containers

**Assessment**: Correct for CI environment

### Docker Caching

- `docker-compose.yml` defines a `gradle-cache` volume that persists between runs in local/Docker environments
- CI does not use Docker, so this only benefits local development

**Assessment**: Good for developers using Docker

### No Other Optimizations Detected

**Missing**:
- No Gradle build cache (`org.gradle.build-cache=true`) configured — CI runners don't share build outputs across runs
- No parallel build tasks configured (`org.gradle.parallel=true`) — could speed up multi-module builds
- No module-level caching (e.g., pre-built shared modules published to a cache)

### Build Performance Impact

**Estimated CI times**:
- Scrybe CI with retry script: ~30-40 minutes on transient network failures (up to 3 attempts × 15-45 second backoff + build time)
- Shelf Snap / PriceDrop CI: ~25-30 minutes (single attempt)
- Release workflows: ~40-50 minutes (all steps serial + APK + AAB builds)

**Optimization opportunities**:
1. Enable `org.gradle.parallel=true` in gradle.properties
2. Enable Gradle build cache for shared/ modules (would require caching service or artifact repo)
3. Consider splitting test and lint into parallel jobs within each CI workflow
4. Parallelize lint and detekt runs across modules

---

## Top 5 Priorities

### 1. **UNUSED REUSABLE BUILD WORKFLOW — Quick Cleanup Win**

**Issue**: `reusable-build.yml` exists but is NOT called by any CI workflow. All three per-app CI workflows duplicate its logic inline.

**Impact**: 
- Code duplication creates maintenance burden — a bug fix or enhancement to build logic must be made in three places
- Inconsistencies emerging (e.g., Scrybe uses `lint` debug variant, Shelf Snap and PriceDrop use `lintRelease` release variant)

**Fix**:
- Call `reusable-build.yml` from all three CI workflows instead of duplicating steps
- Update scrybe-ci.yml line 123-207 to use reusable workflow with `use_retry_script: true`
- Update shelf-snap-ci.yml line 41-117 and pricedrop-ci.yml line 41-117 similarly

**Effort**: ~30 minutes
**Confidence**: High — workflow is already written and parameterized correctly

---

### 2. **INCONSISTENT LINT VARIANTS ACROSS APPS — Build Correctness**

**Issue**: 
- Scrybe CI runs `lint` (debug variant) against debug APK, but builds `assembleRelease`
- Shelf Snap and PriceDrop run `lintRelease` against release APK
- Pre-commit hook only runs lint checks for Scrybe locally

**Risk**: Android Lint catches release-specific issues (ProGuard rules, minification, etc.) that debug lint misses. Scrybe release APK may have lint violations not caught by `lint` task.

**Fix**:
- Standardize all three apps to `lintRelease` when building `assembleRelease`
- Update scrybe-ci.yml line 159 and reusable-build.yml line 20 default gradle_tasks

**Effort**: ~15 minutes
**Confidence**: High

---

### 3. **MANIFEST VALIDATION ONLY RUNS FOR SCRYBE LOCALLY — Coverage Gap**

**Issue**:
- Pre-commit hook validates Scrybe manifests only (.githooks/pre-commit:105)
- CI validates all three apps' manifests (.github/workflows/reusable-validate.yml:71)
- Shelf Snap and PriceDrop could commit invalid manifest files that CI would catch

**Risk**: Developers pushing Shelf Snap or PriceDrop changes don't validate manifests locally, losing early feedback.

**Fix**:
- Update pre-commit hook to validate manifests for all three apps:
  ```bash
  python3 "$REPO_ROOT/scripts/validate-manifests.py" --root "$SHELF_SNAP_DIR"
  python3 "$REPO_ROOT/scripts/validate-manifests.py" --root "$PRICE_DROP_DIR"
  ```

**Effort**: ~10 minutes
**Confidence**: High

---

### 4. **NO ANDROID LINT FOR SHARED MODULES — Silent Defect Risk**

**Issue**:
- Shared modules (shared/debug-log-ui, shared/design, etc.) are Android libraries but Android Lint never runs against them
- Only runs at app-level when integrating the shared library into an app's APK
- A violation could slip into shared modules and only surface when app releases

**Risk**: Delayed detection of shared module issues until release time; could require hotfix.

**Fix**:
- Add Android Lint runs for shared modules in CI — either:
  a) Add separate lint job that runs against shared/*/build.gradle.kts
  b) Add `lintRelease` to shared/build.gradle.kts as an aggregate task bridged from apps
  - Update reusable-validate.yml or add new step to scan shared/*/AndroidManifest.xml and call `./gradlew lint` on shared libraries

**Effort**: ~45 minutes (requires testing to ensure linting works on composite build)
**Confidence**: Medium — composite build architecture may complicate lint task ordering

---

### 5. **NO DEPENDENCY VULNERABILITY SCANNING — Security Gap**

**Issue**:
- No Dependabot, OWASP dependency-check, or equivalent scanning configured
- Transitive dependencies could contain known CVEs without any alerting

**Risk**: Security vulnerabilities ship undetected; compliance/audit issues.

**Fix**:
- Enable GitHub Dependabot for Gradle (if org allows)
  - Add `.github/dependabot.yml`:
    ```yaml
    version: 2
    updates:
      - package-ecosystem: "gradle"
        directory: "/"
        schedule:
          interval: "weekly"
    ```
- OR add OWASP dependency-check to CI workflow (runs `./gradlew dependencyCheck`)
- Periodically audit with `./gradlew dependencies --configuration runtimeClasspath`

**Effort**: ~20 minutes for Dependabot; ~1 hour for OWASP integration + remediation process
**Confidence**: High

