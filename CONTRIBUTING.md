# Contributing to Scrybe

Thank you for your interest in contributing! This guide covers everything you need to go from zero to a working development environment, understand the codebase, make changes, and get them merged.

---

## Table of contents

1. [Prerequisites](#1-prerequisites)
2. [Repository layout](#2-repository-layout)
3. [Setting up your environment](#3-setting-up-your-environment)
4. [Build, lint, and test](#4-build-lint-and-test)
5. [Project architecture](#5-project-architecture)
6. [Adding a new transcription provider](#6-adding-a-new-transcription-provider)
7. [Adding a new transformation provider](#7-adding-a-new-transformation-provider)
8. [Adding a new feature module](#8-adding-a-new-feature-module)
9. [Code style](#9-code-style)
10. [Testing guidelines](#10-testing-guidelines)
11. [CI/CD pipeline](#11-cicd-pipeline)
12. [Pull request checklist](#12-pull-request-checklist)

---

## 1. Prerequisites

| Tool | Minimum version | Notes |
|------|----------------|-------|
| JDK | 17 | [Temurin](https://adoptium.net/) recommended; must be on `PATH` |
| Android Studio | Ladybug (2024.2) | Or any IDE with Android & Kotlin support |
| Android SDK | API 26–35 | Install via Android Studio SDK Manager |
| Python | 3.x | Required for the manifest-validation script |
| Git | Any recent | – |
| Docker | Recent Engine/Desktop (optional) | Use instead of a local Android SDK/JDK install |

You do **not** need an OpenAI API key to build or run unit tests, but you will need one to exercise the transcription feature on a device or emulator.

---

## 2. Repository layout

```
scrybe/
├── README.md
├── CONTRIBUTING.md
└── apps/
    └── android-whispering/          ← Gradle root project
        ├── app/                      ← :app module (entry point)
        ├── core/
        │   ├── audio/                ← :core:audio
        │   ├── common/               ← :core:common
        │   ├── database/             ← :core:database
        │   ├── datastore/            ← :core:datastore
        │   ├── export/               ← :core:export
        │   ├── model/                ← :core:model
        │   ├── network/              ← :core:network
        │   ├── transcription/        ← :core:transcription
        │   └── transforms/           ← :core:transforms
        ├── feature/
        │   ├── capture/              ← :feature:capture
        │   ├── history/              ← :feature:history
        │   ├── profiles/             ← :feature:profiles
        │   ├── session-detail/       ← :feature:session-detail
        │   └── settings/             ← :feature:settings
        ├── service/
        │   └── recording/            ← :service:recording
        ├── workers/                  ← :workers
        ├── scripts/
        │   └── validate-manifests.py ← CI manifest validation
        ├── gradle/
        │   └── libs.versions.toml    ← Version catalog
        ├── detekt.yml                ← Static analysis config
        ├── build.gradle.kts          ← Root build script
        └── settings.gradle.kts       ← Module declarations
```

All Android source lives under `apps/android-whispering/`. New modules must be declared in `settings.gradle.kts`.

---

## 3. Setting up your environment

```bash
# 1. Clone
git clone https://github.com/punassuming/scrybe.git
cd scrybe

# 2. Open in Android Studio
#    File → Open → select apps/android-whispering/

# 3. Let Gradle sync finish (first sync downloads ~200 MB of dependencies)

# 4. (Optional) Run the project on a device or emulator via Android Studio
#    or from the command line:
cd apps/android-whispering
./gradlew installDebug
```

### Docker development environment (optional)

If you prefer not to install the Android SDK and JDK locally, you can use the repository's Docker environment from the repository root:

```bash
# Build the image
docker compose build android-dev

# Open a shell inside the Android project
docker compose run --rm android-dev

# Or run project commands directly
docker compose run --rm android-dev ./gradlew assembleDebug
docker compose run --rm android-dev ./gradlew lint
docker compose run --rm android-dev ./gradlew testDebugUnitTest
docker compose run --rm android-dev python3 scripts/validate-manifests.py
```

The `android-dev` service mounts the repository into `/workspace`, uses `apps/android-whispering/` as its working directory, and persists the Gradle cache in a named Docker volume. The image provides Gradle 8.9 on JDK 17 together with the Android SDK platform tools, API 35, and build-tools 35.0.0.

If your environment blocks Gradle wrapper downloads, you can replace `./gradlew` with `gradle` inside the container because the matching Gradle version is preinstalled.

> **Note:** The container is intended for builds, linting, unit tests, and CLI-based development. Commands that require a connected device or emulator (for example `installDebug` or `connectedDebugAndroidTest`) still require host-side Android Studio / emulator setup and ADB access.

### JAVA_HOME

Gradle uses the JDK that `JAVA_HOME` points to. You can override per-project in `gradle.properties`:

```properties
org.gradle.java.home=/path/to/jdk17
```

---

## 4. Build, lint, and test

All commands are run from `apps/android-whispering/`.

| Task | Command | Notes |
|------|---------|-------|
| Build debug APK | `./gradlew assembleDebug` | Output: `app/build/outputs/apk/debug/` |
| Build release APK | `./gradlew assembleRelease` | Requires signing config |
| Install on device | `./gradlew installDebug` | Device/emulator must be connected |
| Unit tests | `./gradlew testDebugUnitTest` | JVM-only, no device needed |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | Requires connected device/emulator |
| Android Lint | `./gradlew lint` | Report: `app/build/reports/lint-results-debug.html` |
| Detekt | `./gradlew detekt` | Static analysis |
| KtLint check | `./gradlew ktlintCheck` | Formatting check |
| KtLint format | `./gradlew ktlintFormat` | Auto-fix formatting |
| Full check | `./gradlew check` | Runs lint + tests + detekt + ktlint |
| Manifest validation | `python3 scripts/validate-manifests.py` | Validates all `AndroidManifest.xml` files |

> **Tip:** Append `--no-daemon` to any Gradle command when running in CI or constrained environments to avoid background daemon overhead.

---

## 5. Project architecture

Scrybe uses a **multi-module clean architecture** approach:

```
:feature:*   →   :core:*   →   External APIs / Device hardware
```

* **Feature modules** contain Compose screens and their ViewModels. They depend on `:core:*` modules but never on each other.
* **Core modules** contain interfaces, data models, and implementations. They may depend on other `:core:*` modules but never on `:feature:*`.
* **:app** wires everything together: Hilt DI graph, navigation, and the application entry point.

### Key provider abstractions

#### `TranscriptionProvider` (`:core:transcription`)

```kotlin
interface TranscriptionProvider {
    val providerType: ProviderType
    suspend fun transcribe(audioFile: File, options: TranscriptionOptions): Result<TranscriptResult>
}
```

Registered in Hilt via `@IntoMap` + `@ProviderTypeKey`. The `TranscriptionOrchestrator` routes calls to the correct implementation by `ProviderType` and prevents concurrent duplicate transcriptions for the same session.

#### `TransformationProvider` (`:core:transforms`)

```kotlin
interface TransformationProvider {
    val providerType: ProviderType
    suspend fun transform(input: TransformInput): Result<TransformResult>
}
```

The `TransformationPipeline` singleton routes `transform()` calls to the registered provider. Three built-in profiles are defined in `DefaultProfiles`: *Cleanup Dictation*, *Summarize*, and *Action Items*.

#### `AudioRecorder` (`:core:audio`)

```kotlin
interface AudioRecorder {
    val isRecording: Flow<Boolean>
    suspend fun startRecording(config: RecordingConfig): Result<Unit>
    suspend fun stopRecording(): Result<RecordedAudio>
    fun cancelRecording()
}
```

The default implementation `AndroidMediaRecorder` uses the platform `MediaRecorder` API and emits recording state via a `StateFlow`.

### Dependency injection

Hilt is used throughout. All `@Module` classes live in `di/` sub-packages within each `:core:*` module and are installed in `SingletonComponent`.

### Navigation

`ScrybeNavHost` (in `:app`) declares five destinations:

| Route | Screen |
|-------|--------|
| `capture` | `CaptureScreen` (start destination) |
| `history` | `HistoryScreen` |
| `session_detail/{sessionId}` | `SessionDetailScreen` |
| `profiles` | `ProfilesScreen` |
| `settings` | `SettingsScreen` |

---

## 6. Adding a new transcription provider

This walkthrough adds a hypothetical `LocalTranscriptionProvider` that runs inference on-device.

### Step 1 – Implement the interface

Create `LocalTranscriptionProvider.kt` inside `:core:transcription`:

```kotlin
package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import java.io.File
import javax.inject.Inject

class LocalTranscriptionProvider @Inject constructor(
    // inject any on-device model dependency here
) : TranscriptionProvider {

    override val providerType: ProviderType = ProviderType.LOCAL

    override suspend fun transcribe(
        audioFile: File,
        options: TranscriptionOptions,
    ): Result<TranscriptResult> = runCatching {
        // TODO: run on-device model
        TranscriptResult(text = "...", language = null, durationSeconds = null)
    }
}
```

### Step 2 – Register with Hilt

Add a binding in `TranscriptionModule.kt`:

```kotlin
@Binds
@IntoMap
@ProviderTypeKey(ProviderType.LOCAL)
abstract fun bindsLocalProvider(impl: LocalTranscriptionProvider): TranscriptionProvider
```

### Step 3 – Expose `ProviderType.LOCAL` in the UI

`ProviderType` is declared in `:core:model`. The enum already contains `LOCAL`; no change is needed there. Update `SettingsScreen` / `SettingsViewModel` if you want to surface a selector for the new provider.

### Step 4 – Test

Add a unit test in `:core:transcription/src/test/` that mocks your model dependency and verifies the `Result<TranscriptResult>` contract.

---

## 7. Adding a new transformation provider

The pattern mirrors the transcription provider above.

1. Create a class that implements `TransformationProvider` in `:core:transforms`.
2. Annotate it with `@Inject` and register it with `@IntoMap` / `@ProviderTypeKey` in a Hilt module.
3. The `TransformationPipeline` will automatically pick it up.

---

## 8. Adding a new feature module

### Step 1 – Create the module directory

```
apps/android-whispering/feature/my-feature/
    build.gradle.kts
    src/main/
        AndroidManifest.xml
        kotlin/dev/scrybe/feature/myfeature/
            MyFeatureScreen.kt
            MyFeatureViewModel.kt
```

### Step 2 – Write `build.gradle.kts`

Model it on an existing feature module (e.g., `:feature:capture`):

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.scrybe.feature.myfeature"
    compileSdk = 35
    // ...
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    // add any :core:* dependencies you need
}
```

### Step 3 – Declare in `settings.gradle.kts`

```kotlin
include(":feature:my-feature")
```

### Step 4 – Add to `:app`

In `app/build.gradle.kts`:

```kotlin
implementation(project(":feature:my-feature"))
```

### Step 5 – Wire navigation

Add a new route in `ScrybeNavHost.kt` and a corresponding `Screen` destination.

---

## 9. Code style

The project enforces style automatically via **KtLint** and **Detekt**.

| Rule | Tool | Enforcement |
|------|------|-------------|
| Kotlin formatting | KtLint | CI blocks merges on failures |
| Static analysis | Detekt | `maxIssues = 0` – zero tolerance |
| Long methods | Detekt | Threshold: 60 lines |
| Long parameter lists | Detekt | Threshold: 8 params |
| Magic numbers | Detekt | Disabled |
| Return count | Detekt | Max 4 returns per function |

Run `./gradlew ktlintFormat` to auto-fix formatting issues before committing.

Configuration file: `apps/android-whispering/detekt.yml`.

---

## 10. Testing guidelines

* **Unit tests** go in `src/test/kotlin/` inside the relevant module. Use JUnit 4.
* **Instrumented tests** go in `src/androidTest/kotlin/`. Use AndroidX Test + Espresso / Compose testing.
* **Coroutines** – use `kotlinx-coroutines-test` and `StandardTestDispatcher` for deterministic testing.
* **ViewModels** – inject a `TestCoroutineDispatcher` via the `@Dispatcher` qualifier defined in `:core:common`.
* Test files must be named `*Test.kt` (unit) or `*AndroidTest.kt` (instrumented).

Example skeleton for a ViewModel test:

```kotlin
class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        viewModel = CaptureViewModel(/* fake dependencies */)
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.uiState.value).isInstanceOf(CaptureUiState.Idle::class.java)
    }
}
```

---

## 11. CI/CD pipeline

### Continuous integration

GitHub Actions runs on every push to `main` or `copilot/**` branches, and on pull requests targeting `main`.

```
validate ──► build
         └─► lint
         └─► test
```

| Job | What it does |
|-----|-------------|
| **Validate** | Runs `scripts/validate-manifests.py` – checks that all `AndroidManifest.xml` files are valid XML with correct namespace declarations |
| **Build** | Assembles a debug APK with JDK 17 |
| **Lint** | Runs Android Lint and reports issues |
| **Unit Tests** | Runs all JVM unit tests |

All jobs use `ubuntu-latest` and Gradle caching to keep build times short. The `validate` job must pass before any other job starts.

### Release automation

Every push to `main` triggers `.github/workflows/release.yml`:

1. The next semantic version is computed from [conventional commits](https://www.conventionalcommits.org/) since the last tag:
   - `feat:` commits → **minor** bump
   - `fix:`, `chore:`, and other prefixes → **patch** bump (default)
   - `BREAKING CHANGE` footer → **major** bump
2. A git tag and a GitHub Release are created directly — no separate release PR is opened.
3. The release APK is built with `./gradlew assembleRelease` and attached to the GitHub Release.

The workflow uses only the built-in `GITHUB_TOKEN`; no additional repository secrets are required for releasing.

---

## 12. Pull request checklist

Before requesting a review, make sure:

- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew lint` reports no new issues
- [ ] `./gradlew ktlintCheck` passes (or run `./gradlew ktlintFormat` to auto-fix)
- [ ] `./gradlew detekt` passes
- [ ] `python3 scripts/validate-manifests.py` exits 0 (if you changed any manifest)
- [ ] New public interfaces / classes have KDoc comments
- [ ] Any new Hilt module is installed in the correct component (`SingletonComponent` for app-scoped dependencies)
- [ ] New feature modules are declared in `settings.gradle.kts` and added as a dependency in `:app`
