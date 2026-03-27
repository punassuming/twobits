# GitHub Copilot instructions for Scrybe

## Pre-commit checks (mandatory)

Before every `git commit`, run the following from the repository root and ensure **all checks pass**. Do not commit code that fails any of these.

```bash
cd apps/android-whispering

# Validate AndroidManifest.xml files
python3 scripts/validate-manifests.py

# Build (catches compile errors, Hilt DI wiring, missing dependencies)
./gradlew assembleDebug --no-daemon

# Unit tests
./gradlew testDebugUnitTest --no-daemon

# Android Lint
./gradlew lint --no-daemon

# Kotlin formatting — auto-fix first, then verify
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck --no-daemon

# Static analysis
./gradlew detekt --no-daemon
```

One-liner (after `cd apps/android-whispering`):

```bash
python3 scripts/validate-manifests.py && \
  ./gradlew assembleDebug testDebugUnitTest lint ktlintFormat ktlintCheck detekt --no-daemon
```

## Common pitfalls

- **Missing Gradle dependency**: Adding an `@Inject` parameter whose type comes from another Gradle module without adding `implementation(project(":that:module"))` to `build.gradle.kts` causes Hilt to report `error.NonExistentClass` and the build to fail.
- **KtLint**: Run `./gradlew ktlintFormat` to auto-fix formatting before checking in.
- **Detekt**: Zero-tolerance (`maxIssues = 0`). Keep functions ≤ 60 lines, parameter lists ≤ 8, return count ≤ 4.

## Architecture

- Android project root: `apps/android-whispering/`
- 17 Gradle modules: `:app`, `:core:{common,model,database,datastore,audio,network,transcription,transforms,export}`, `:feature:{capture,history,session-detail,profiles,settings}`, `:service:recording`, `:workers`
- DI: Hilt 2.51.1 with `@HiltViewModel`, `@Singleton`, `@IntoMap` multibinding
- Kotlin 1.9.25, AGP 8.7.3, minSdk 26, targetSdk 35, Jetpack Compose

Refer to `CONTRIBUTING.md` for full development setup, architecture details, and the PR checklist.
