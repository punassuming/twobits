# Scrybe

[![Scrybe CI](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml)

Scrybe is an Android application for recording audio, transcribing it with AI, and transforming the resulting text into structured notes. Think of it as a voice-to-document pipeline that lives entirely on your phone.

---

## Features

| Feature | Description |
|---------|-------------|
| **Recording** | One-tap foreground recording with a persistent notification; configurable format, sample rate (up to 48 kHz), bitrate, and channel count |
| **Transcription** | AI-powered speech-to-text via the OpenAI Whisper API; optional auto-transcribe on save; deduplication prevents redundant re-transcriptions |
| **Transformation** | Post-process transcripts with LLM prompts (clean-up, summarise, extract action items); three built-in profiles, unlimited custom ones |
| **Playback** | In-session audio playback with real-time waveform visualizer and draggable seek control |
| **History** | Browse, search, rename, and delete every past session |
| **Profiles** | Create and manage reusable transformation profiles with custom system prompts and a `{{transcript}}` template variable |
| **Export** | Export sessions as Markdown, plain text, or JSON; files saved to on-device storage |
| **Sharing** | Share the original audio file or the latest transcript with any app via the standard Android share sheet |
| **API validation** | Live OpenAI API key validation with a real-time connection status indicator (valid / validating / invalid) |
| **Themes** | System-default, light, and dark mode |
| **Release notes** | Automatic "What's New" popup on first launch after an update, with a categorised history available in Settings |
| **Settings** | Configure OpenAI API key (with live validation), default provider, recording quality, auto-transcribe, theme, and view usage statistics |

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

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for full environment setup and developer onboarding.

### Prerequisites

* **JDK 17** (Temurin recommended)
* **Android Studio Ladybug** or later (or any IDE with Android plugin support)
* An **OpenAI API key** to enable transcription

### Build & run

```bash
cd apps/scrybe

# Build a debug APK
./gradlew assembleDebug

# Install on a connected device / emulator
./gradlew installDebug
```

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

| Workflow | Trigger | What it does |
|----------|---------|-------------|
| `scrybe-ci.yml` | Push to `main`/`copilot/**`/`claude/**`, PRs to `main` | Changelog + manifest validation → assembleDebug, testDebugUnitTest, lint, ktlintCheck, detekt |
| `scrybe-release.yml` | Successful `scrybe-ci.yml` on `main` | Computes next version, promotes changelog, bumps version, creates tag + GitHub Release with signed APK/AAB |

Release automation uses [conventional commits](https://www.conventionalcommits.org/): `feat:` bumps minor, `fix:`/`chore:` bump patch, `BREAKING CHANGE` bumps major. The `## Unreleased` section of `CHANGELOG.md` is promoted automatically — no manual version bumping.

Signing secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. If absent, a one-off keystore is generated so the APK remains installable.
