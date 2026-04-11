# Agent instructions for Scrybe

This file contains mandatory instructions for AI coding agents (Copilot, Codex, etc.) working in this repository.

## Mandatory pre-commit checks

**Before every `git commit`, you MUST run the following checks from `apps/android-whispering/` and ensure they all pass:**

```bash
cd apps/android-whispering

# 1. Validate the changelog structure
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md

# 2. Validate all AndroidManifest.xml files
python3 scripts/validate-manifests.py

# 3. Build the debug APK (catches compile errors and Hilt DI wiring issues)
./gradlew assembleDebug --no-daemon

# 4. Run all JVM unit tests
./gradlew testDebugUnitTest --no-daemon

# 5. Run Android Lint
./gradlew lint --no-daemon

# 6. Auto-fix Kotlin formatting, then verify it passes
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck --no-daemon

# 7. Run Detekt static analysis
./gradlew detekt --no-daemon
```

Or run them all together (after `cd apps/android-whispering`):

```bash
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md && \
  python3 scripts/validate-manifests.py && \
  ./gradlew assembleDebug testDebugUnitTest lint ktlintFormat ktlintCheck detekt --no-daemon
```

**Do not commit or push code that fails any of these checks.** Fix all errors and warnings before committing.

## Mandatory changelog updates for main-bound changes

Before preparing any commit, pull request, or push that is intended to land on `main`, you MUST use the repo-local skill at [`SKILL.md`](/C:/drive/dev/android/scrybe/.codex/skills/update-changelog-before-main/SKILL.md).

- Update the root [`CHANGELOG.md`](/C:/drive/dev/android/scrybe/CHANGELOG.md) `## Unreleased` section before the commit or PR is prepared.
- Keep bullets grouped under `Features`, `Improvements`, and `Fixes`.
- Do not invent version numbers in `CHANGELOG.md`; the release workflow promotes `Unreleased` into the next versioned section automatically.

## Windows / Codex Gradle invocation

If you need to pin the Android toolchain paths in PowerShell, dot-source `scripts/android-env.ps1` first and then run `gradlew.bat` directly.

Use direct Gradle commands with an explicit project cache dir, for example:

```powershell
. .\apps\android-whispering\scripts\android-env.ps1
& "$env:SCRYBE_ANDROID_GRADLEW" -p "$env:SCRYBE_ANDROID_PROJECT_ROOT" assembleDebug --project-cache-dir "$env:SCRYBE_GRADLE_PROJECT_CACHE" --no-configuration-cache --no-daemon --console=plain --info
```

## Common mistakes to avoid

- **Missing Gradle module dependency**: If you add an `@Inject`-annotated class or parameter from a module that is not listed in the target module's `build.gradle.kts` `dependencies {}` block, Hilt will fail with `error.NonExistentClass` at compile time. Always add `implementation(project(":module:name"))` for any new cross-module type you import.
- **Unused imports**: KtLint and the Kotlin compiler will flag these. Remove them.
- **Detekt violations**: `maxIssues = 0` — zero tolerance. Functions must be ≤ 60 lines; parameter lists ≤ 8 items; ≤ 4 return statements per function.

## CI pipeline

GitHub Actions runs on every push to `main` or `copilot/**` branches, and on pull requests targeting `main`. The pipeline runs changelog validation plus manifest validation before Android verification. All jobs must pass for a merge to succeed.

Android CI is intentionally consolidated into a single Gradle verification job that runs `assembleDebug`, `testDebugUnitTest`, `lint`, `ktlintCheck`, and `detekt` in one invocation to avoid repeated environment setup and duplicate build cycles. Release APK assembly happens in the separate release workflow on successful pushes to `main`.

Running the checks locally before committing avoids wasted CI minutes and merge blocks.
