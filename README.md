# Scrybe

[![Android CI](https://github.com/punassuming/scrybe/actions/workflows/android-ci.yml/badge.svg)](https://github.com/punassuming/scrybe/actions/workflows/android-ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.25-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/target%20sdk-35%20(Android%2015)-brightgreen.svg)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Scrybe is an Android application for recording audio, transcribing it with AI, and transforming the resulting text into structured notes. Think of it as a voice-to-document pipeline that lives entirely on your phone.

---

## Features

| Feature | Description |
|---------|-------------|
| 🎙️ **Recording** | One-tap foreground recording with a persistent notification; configurable format, sample rate (up to 48 kHz), bitrate, and channel count |
| 📝 **Transcription** | AI-powered speech-to-text via the OpenAI Whisper API; optional auto-transcribe on save; deduplication prevents redundant re-transcriptions |
| ✨ **Transformation** | Post-process transcripts with LLM prompts (clean-up, summarise, extract action items); three built-in profiles, unlimited custom ones |
| 🔊 **Playback** | In-session audio playback with real-time waveform visualizer and draggable seek control |
| 📂 **History** | Browse, search, rename, and delete every past session |
| 🔀 **Profiles** | Create and manage reusable transformation profiles with custom system prompts and a `{{transcript}}` template variable |
| 📤 **Export** | Export sessions as Markdown, plain text, or JSON; files saved to on-device storage |
| 🔗 **Sharing** | Share the original audio file or the latest transcript with any app via the standard Android share sheet |
| 🔑 **API validation** | Live OpenAI API key validation with a real-time connection status indicator (valid / validating / invalid) |
| 🎨 **Themes** | System-default, light, and dark mode |
| 📣 **Release notes** | Automatic "What's New" popup on first launch after an update, with a categorised history available in Settings |
| ⚙️ **Settings** | Configure OpenAI API key (with live validation), default provider, recording quality, auto-transcribe, theme, and view usage statistics |

---

## Architecture

Scrybe follows the [Now in Android](https://github.com/android/nowinandroid) multi-module architecture pattern.

```
┌─────────────────────────────────────────────────────────────────┐
│  :app  (entry point – MainActivity, navigation, Hilt setup)     │
├─────────────────────────────────────────────────────────────────┤
│  :feature:*  (Compose screens + ViewModels)                     │
│   capture │ history │ session-detail │ profiles │ settings      │
├─────────────────────────────────────────────────────────────────┤
│  :service:recording  (foreground service + notification)        │
│  :workers            (WorkManager jobs – deferred transcription)│
├─────────────────────────────────────────────────────────────────┤
│  :core:*  (business logic, data access, provider abstractions)  │
│   audio │ transcription │ transforms │ export                   │
│   database │ datastore │ network │ model │ common               │
└─────────────────────────────────────────────────────────────────┘
```

### Data flow

#### Capture (write path)

```
Microphone
  │
  ▼
AudioRecorder  ──────────────────►  RecordedAudio (file path, duration)
  │                                         │
  │                                         ▼
  │                              TranscriptionOrchestrator
  │                                (routes by ProviderType;
  │                                 deduplicates concurrent requests)
  │                                         │
  │                                         ▼
  │                              TranscriptionProvider
  │                               (OpenAI Whisper API)
  │                                         │
  │                                         ▼
  │                              TransformationPipeline
  │                               (LLM with system prompt)
  │                                         │
  │                           ┌─────────────┴─────────────┐
  │                           ▼                           ▼
  │                  ExportCoordinator            Android share sheet
  │               (Markdown / Text / JSON)     (audio file or transcript)
  │
  ▼
Room Database  ◄───── DAO ◄──── RecordingSessionEntity
DataStore            ◄──── TranscriptEntity
                     ◄──── TransformProfileEntity
```

#### Playback (read path)

```
Room Database  ──────────────────►  RecordingSessionEntity
                                    TranscriptEntity
                                         │
                                         ▼
                                   AudioPlayer
                              (waveform visualizer,
                               seek control, position flow)
```

---

## Module map

| Module | Layer | Responsibility |
|--------|-------|---------------|
| `:app` | Application | Single activity, navigation graph, Hilt bootstrap, "What's New" popup |
| `:feature:capture` | Feature | Recording UI – start / stop, animated waveform visualizer, live elapsed time |
| `:feature:history` | Feature | Searchable, filterable list of past sessions; rename and delete |
| `:feature:session-detail` | Feature | Full session view – audio playback, waveform seek, transcripts, transforms, sharing, and export |
| `:feature:profiles` | Feature | Create / edit / delete transformation profiles; mark a default |
| `:feature:settings` | Feature | API key with live validation, theme, recording quality defaults, auto-transcribe, usage statistics, release notes |
| `:service:recording` | Service | `RecordingForegroundService` + persistent notification |
| `:workers` | Workers | WorkManager-based deferred tasks (background transcription) |
| `:core:audio` | Core | `AudioRecorder` / `AndroidMediaRecorder`; `AudioPlayer` with waveform and position flow |
| `:core:transcription` | Core | `TranscriptionProvider`, `TranscriptionOrchestrator` (dedup), OpenAI Whisper implementation |
| `:core:transforms` | Core | `TransformationProvider`, `TransformationPipeline`, three built-in `DefaultProfiles` |
| `:core:export` | Core | `ExportCoordinator`, Markdown / Text / JSON exporters |
| `:core:database` | Core | Room database, entities, DAOs |
| `:core:datastore` | Core | DataStore preferences (API keys, theme, recording defaults) |
| `:core:network` | Core | `OkHttpClient`, `Retrofit`, JSON serialisation config, `OpenAiApiKeyValidator` |
| `:core:model` | Core | Shared domain models and enums |
| `:core:common` | Core | Coroutine dispatcher qualifiers, `Result` extensions, release-notes parser |

---

## Tech stack

| Technology | Version | Role |
|------------|---------|------|
| Kotlin | 1.9.25 | Language |
| Jetpack Compose | BOM 2024.12.01 | UI framework |
| Hilt | 2.51.1 | Dependency injection |
| Room | 2.6.1 | Local database |
| DataStore | 1.1.1 | Preferences storage |
| OkHttp | 4.12.0 | HTTP client |
| Retrofit | 2.11.0 | REST abstraction |
| Kotlinx Serialization | 1.6.3 | JSON serialisation |
| Kotlinx Coroutines | 1.9.0 | Async / Flow |
| KSP | 1.9.25-1.0.20 | Annotation processing |
| Detekt | 1.23.7 | Static analysis |
| KtLint | 12.1.1 | Code formatting |
| Android Gradle Plugin | 8.7.3 | Build tooling |
| Min SDK | 26 (Android 8.0) | Device support |
| Target SDK | 35 (Android 15) | Target platform |

---

## Quick start

See [CONTRIBUTING.md](CONTRIBUTING.md) for full environment setup and developer onboarding.

### Prerequisites

* **JDK 17** (Temurin recommended)
* **Android Studio Ladybug** or later (or any IDE with Android plugin support)
* An **OpenAI API key** to enable transcription

### Build & run

```bash
# Clone the repository
git clone https://github.com/punassuming/scrybe.git
cd scrybe/apps/android-whispering

# Build a debug APK
./gradlew assembleDebug

# Install on a connected device / emulator
./gradlew installDebug
```

If you are on Windows and want to pin the Android toolchain paths explicitly, dot-source the environment bootstrap once and then run Gradle directly:

```powershell
cd C:\drive\dev\android\scrybe
. .\apps\android-whispering\scripts\android-env.ps1
& "$env:SCRYBE_ANDROID_GRADLEW" -p "$env:SCRYBE_ANDROID_PROJECT_ROOT" assembleDebug --project-cache-dir "$env:SCRYBE_GRADLE_PROJECT_CACHE" --no-configuration-cache --console=plain --info
& "$env:SCRYBE_ANDROID_GRADLEW" -p "$env:SCRYBE_ANDROID_PROJECT_ROOT" :service:recording:lintDebug --project-cache-dir "$env:SCRYBE_GRADLE_PROJECT_CACHE" --no-configuration-cache --console=plain --info
```

The bootstrap script keeps Gradle state under the repo root, exports the checked-in wrapper path as `SCRYBE_ANDROID_GRADLEW`, and keeps `--info` enabled so long-running builds stay chatty on stdout.

If you want a single command entrypoint instead of remembering Gradle and ADB commands, use the repo helper from the repo root:

```powershell
.\scripts\android.ps1 build
.\scripts\android.ps1 install
.\scripts\android.ps1 run
.\scripts\android.ps1 lint
.\scripts\android.ps1 test
.\scripts\android.ps1 emulator -Avd scrybe-api35
.\scripts\android.ps1 verify
```

The helper wraps `android-env.ps1`, uses the checked-in Gradle wrapper, keeps output on stdout with `--console=plain --info`, and also exposes ADB / emulator shortcuts such as `devices`, `emulators`, `logcat`, `stop-app`, and raw `gradle` passthrough.

After first launch, open **Settings**, enter your OpenAI API key, and tap **Save** — the app validates the key against the OpenAI API and shows a live connection status before storing it.

### Containerized development

If you do not want to install the Android toolchain locally, a Docker-based development environment is available at the repository root:

```bash
docker compose build android-dev
docker compose run --rm android-dev ./gradlew assembleDebug
docker compose run --rm android-dev ./gradlew testDebugUnitTest
```

Container notes:

* Includes Gradle 8.9 on JDK 17 plus the Android SDK platform tools, API 35, and build-tools 35.0.0.
* If your network blocks Gradle wrapper downloads, you can substitute `gradle` for `./gradlew` inside the container because the matching Gradle version is already installed.
* Device and emulator workflows still require host-side ADB / emulator access.

---

## CI / CD

### Continuous integration

GitHub Actions runs four jobs on every push to `main` or any `copilot/**` branch, and on every pull request targeting `main`:

| Job | Command |
|-----|---------|
| Changelog | `python3 apps/android-whispering/scripts/manage-changelog.py validate --changelog CHANGELOG.md` plus diff enforcement for `main`-bound changes |
| Validate | `python3 scripts/validate-manifests.py` |
| Build | `./gradlew assembleDebug` |
| Lint | `./gradlew lint` |
| Unit Tests | `./gradlew testDebugUnitTest` |

### Release automation

Every push to `main` triggers the release workflow:

1. Changes headed to `main` are expected to update the root `CHANGELOG.md` `## Unreleased` section before merge.
2. The next semantic version is computed automatically from [conventional commits](https://www.conventionalcommits.org/) since the last tag (`feat:` → minor, `fix:` / `chore:` / etc. → patch, `BREAKING CHANGE` → major).
3. The release workflow promotes `## Unreleased` into the new versioned release section and uses that promoted section as the GitHub Release body.
4. A git tag and a GitHub Release are created directly — **no separate release PR is opened**.
5. A release APK is built and attached to the GitHub Release.

The built-in `GITHUB_TOKEN` is used; no additional secrets are required.

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
