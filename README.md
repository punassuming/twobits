# Scrybe

[![Android CI](https://github.com/punassuming/scrybe/actions/workflows/android-ci.yml/badge.svg)](https://github.com/punassuming/scrybe/actions/workflows/android-ci.yml)

Scrybe is an Android application for recording audio, transcribing it with AI, and transforming the resulting text into structured notes. Think of it as a voice-to-document pipeline that lives entirely on your phone.

---

## Features

| Feature | Description |
|---------|-------------|
| 🎙️ **Recording** | One-tap foreground recording with a persistent notification |
| 📝 **Transcription** | AI-powered speech-to-text via the OpenAI Whisper API |
| ✨ **Transformation** | Post-process transcripts with LLM prompts (clean-up, summarise, extract action items) |
| 📂 **History** | Browse, search, and revisit every past session |
| 🔀 **Profiles** | Create reusable transformation profiles with custom system prompts |
| 📤 **Export** | Export sessions as Markdown, plain text, or JSON |
| ⚙️ **Settings** | Configure your OpenAI API key, default provider, and auto-transcribe behaviour |

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

```
Microphone
  │
  ▼
AudioRecorder  ──────────────────►  RecordedAudio (file path, duration)
  │                                         │
  │                                         ▼
  │                              TranscriptionOrchestrator
  │                                (routes by ProviderType)
  │                                         │
  │                                         ▼
  │                              TranscriptionProvider
  │                               (OpenAI Whisper API)
  │                                         │
  │                                         ▼
  │                              TransformationPipeline
  │                               (LLM with system prompt)
  │                                         │
  │                                         ▼
  │                              ExportCoordinator
  │                           (Markdown / Text / JSON)
  │
  ▼
Room Database  ◄───── DAO ◄──── RecordingSessionEntity
DataStore            ◄──── TranscriptEntity
                     ◄──── TransformProfileEntity
```

---

## Module map

| Module | Layer | Responsibility |
|--------|-------|---------------|
| `:app` | Application | Single activity, navigation graph, Hilt bootstrap |
| `:feature:capture` | Feature | Recording UI – start / stop, live status |
| `:feature:history` | Feature | Paginated list of past sessions |
| `:feature:session-detail` | Feature | Full session view – transcripts, transforms, export |
| `:feature:profiles` | Feature | Create/edit transformation profiles |
| `:feature:settings` | Feature | API key, provider, auto-transcribe preferences |
| `:service:recording` | Service | `RecordingForegroundService` + notification |
| `:workers` | Workers | WorkManager-based deferred tasks |
| `:core:audio` | Core | `AudioRecorder` interface + `AndroidMediaRecorder` |
| `:core:transcription` | Core | `TranscriptionProvider`, `TranscriptionOrchestrator`, OpenAI implementation |
| `:core:transforms` | Core | `TransformationProvider`, `TransformationPipeline`, default profiles |
| `:core:export` | Core | `ExportCoordinator`, Markdown / Text / JSON exporters |
| `:core:database` | Core | Room database, entities, DAOs |
| `:core:datastore` | Core | DataStore preferences (API keys, settings) |
| `:core:network` | Core | `OkHttpClient`, `Retrofit`, JSON serialisation config |
| `:core:model` | Core | Shared domain models and enums |
| `:core:common` | Core | Coroutine dispatcher qualifiers, `Result` extensions |

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

After first launch, go to **Settings → API Key** and enter your OpenAI API key.

---

## CI / CD

GitHub Actions runs four jobs on every push to `main` or any `copilot/**` branch, and on every pull request targeting `main`:

| Job | Command |
|-----|---------|
| Validate | `python3 scripts/validate-manifests.py` |
| Build | `./gradlew assembleDebug` |
| Lint | `./gradlew lint` |
| Unit Tests | `./gradlew testDebugUnitTest` |

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.