# Agent instructions for Scrybe

This file contains mandatory instructions for AI coding agents (Copilot, Codex, etc.) working in this repository.

## Mandatory pre-commit checks

**Before every `git commit`, you MUST run the following checks from `apps/android-whispering/` and ensure they all pass:**

```bash
cd apps/android-whispering

# 1. Validate all AndroidManifest.xml files
python3 scripts/validate-manifests.py

# 2. Build the debug APK (catches compile errors and Hilt DI wiring issues)
./gradlew assembleDebug --no-daemon

# 3. Run all JVM unit tests
./gradlew testDebugUnitTest --no-daemon

# 4. Run Android Lint
./gradlew lint --no-daemon

# 5. Auto-fix Kotlin formatting, then verify it passes
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck --no-daemon

# 6. Run Detekt static analysis
./gradlew detekt --no-daemon
```

Or run them all together (after `cd apps/android-whispering`):

```bash
python3 scripts/validate-manifests.py && \
  ./gradlew assembleDebug testDebugUnitTest lint ktlintFormat ktlintCheck detekt --no-daemon
```

**Do not commit or push code that fails any of these checks.** Fix all errors and warnings before committing.

## Common mistakes to avoid

- **Missing Gradle module dependency**: If you add an `@Inject`-annotated class or parameter from a module that is not listed in the target module's `build.gradle.kts` `dependencies {}` block, Hilt will fail with `error.NonExistentClass` at compile time. Always add `implementation(project(":module:name"))` for any new cross-module type you import.
- **Unused imports**: KtLint and the Kotlin compiler will flag these. Remove them.
- **Detekt violations**: `maxIssues = 0` — zero tolerance. Functions must be ≤ 60 lines; parameter lists ≤ 8 items; ≤ 4 return statements per function.

## CI pipeline

GitHub Actions runs on every push to `main` or `copilot/**` branches, and on pull requests targeting `main`. The pipeline runs validate → build, lint, and test in parallel. All jobs must pass for a merge to succeed.

Running the checks locally before committing avoids wasted CI minutes and merge blocks.
