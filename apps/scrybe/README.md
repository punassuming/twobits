# Scrybe — Voice to Document

[![Scrybe CI](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/target%20sdk-35%20(Android%2015)-brightgreen.svg)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](../../LICENSE)

Record any conversation and get back a clean transcript, summary, action items, or whatever structure you need — shaped by the recording mode you choose. A voice-to-document pipeline that lives entirely on your phone.

---

## Recording modes

Seven built-in modes, each shaping how the AI processes your transcript. Create unlimited custom modes with your own system prompts and a `{{transcript}}` template variable.

| Mode | AI output |
|------|-----------|
| **Meeting** | Action items with assignees, decisions, and structured summary |
| **Idea** | Organized brainstorm — themes, connections, next steps |
| **Tasks** | Extracted to-do list with assignee and due-date fields |
| **Conversation** | Clean dialogue transcript with speaker attribution |
| **Story** | Narrative structure — arc, characters, beats |
| **Interview** | Q&A format with questions and answers separated |
| **Journal** | Personal reflection with key moments and themes |

---

## AI provider options

| Option | How it works |
|--------|-------------|
| **BYOK** | Paste your OpenAI API key in Settings. Audio goes directly from your phone to OpenAI. You pay provider rates — typically pennies per recording. |
| **Scrybe Pro ($1.99/mo)** | Requests route through the TwoBits managed proxy (`api.twobits.app`). Your OpenAI key never touches your device. $2.00/month per-user spend cap enforced server-side. |
| **Fully local** | Whisper tiny/base/small via Sherpa-ONNX for transcription; Gemma 2 2B via MediaPipe for transforms. Zero network calls. Download models once (150 MB – 2.6 GB), then everything runs on-device. |

Swap providers anytime in Settings → API Configuration.

---

## Features

| Feature | Description |
|---------|-------------|
| **Recording** | One-tap foreground recording with persistent notification; configurable format, sample rate (up to 48 kHz), bitrate, and channel count |
| **Transcription** | OpenAI Whisper (cloud) or on-device Whisper tiny/base/small via Sherpa-ONNX; optional auto-transcribe on save; deduplication prevents redundant re-transcriptions |
| **Batch transcription** | Long recordings chunked (≤16 min each, under Whisper's 25 MB limit); each chunk saves immediately; resume from failure without re-uploading |
| **Speaker diarization** | Distinct voices attributed via LLM pass; color-coded waveform bars and inline transcript labels; assign speakers to named person profiles |
| **Transformation** | Post-process transcripts with LLM prompts (clean-up, summarise, extract action items, translate); three built-in profiles, unlimited custom ones |
| **Smart Analyze** | One tap runs title suggestion, tag suggestion, and recording-type classification in parallel; AI also clusters recordings into folders |
| **Task inbox** | AI extracts action items with assignee and due date; global inbox aggregates tasks across all sessions with Today / Week / Mine filters; manual entry too |
| **AI semantic search** | Find recordings by meaning, not keywords; OpenAI ranks recordings by relevance to a natural-language description; plus keyword search, tag filters, and folder navigation |
| **Playback** | In-session audio playback with real-time waveform visualizer and draggable seek control |
| **History** | Browse, search, rename, and delete every past session |
| **Profiles** | Create and manage reusable transformation profiles; mark a default |
| **Export** | Markdown, plain text, or JSON; Obsidian vault export with YAML frontmatter; share audio or transcript via Android share sheet |
| **Insight visualizations** | Sentiment timeline, topic markers (5–15 per session), and speech-density heatmap overlay on the playback waveform; toggle on in Settings → AI Features |
| **API validation** | Live OpenAI API key validation with real-time status indicator |
| **Themes** | System-default, light, and dark mode |
| **Release notes** | Automatic "What's New" popup on first launch after update; categorised history in Settings |

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

### Data flow — capture (write path)

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
  │                          ┌──────────────┴──────────────┐
  │                          ▼                             ▼
  │                 OpenAI Whisper API           On-device Whisper
  │                  (cloud, batched)          (Sherpa-ONNX, local)
  │                          └──────────────┬──────────────┘
  │                                         ▼
  │                              TransformationPipeline
  │                               (LLM with system prompt;
  │                                cloud GPT or local Gemma 2 2B)
  │                                         │
  │                           ┌─────────────┴─────────────┐
  │                           ▼                           ▼
  │                  ExportCoordinator            Android share sheet
  │               (Markdown / Text / JSON /    (audio file or transcript)
  │                Obsidian YAML frontmatter)
  │
  ▼
Room Database  ◄───── DAO ◄──── RecordingSessionEntity
DataStore            ◄──── TranscriptEntity
                     ◄──── TransformProfileEntity
```

### Data flow — playback (read path)

```
Room Database  ──────────────────►  RecordingSessionEntity
                                    TranscriptEntity
                                         │
                                         ▼
                                   AudioPlayer
                              (waveform visualizer + sentiment
                               timeline + topic markers + seek)
```

---

## Module map

| Module | Layer | Responsibility |
|--------|-------|---------------|
| `:app` | Application | Single activity, navigation graph, Hilt bootstrap, "What's New" popup |
| `:feature:capture` | Feature | Recording UI — start/stop, animated waveform visualizer, live elapsed time, mode selection |
| `:feature:history` | Feature | Searchable, filterable list of past sessions; rename and delete; AI semantic search |
| `:feature:session-detail` | Feature | Full session view — audio playback, waveform seek, transcripts, transforms, task inbox, sharing, and export |
| `:feature:profiles` | Feature | Create / edit / delete transformation profiles; mark a default |
| `:feature:settings` | Feature | API key (live validation), provider selection, theme, recording quality defaults, auto-transcribe, usage statistics, release notes |
| `:service:recording` | Service | `RecordingForegroundService` + persistent notification |
| `:workers` | Workers | WorkManager-based deferred tasks (background transcription, batch chunking) |
| `:core:audio` | Core | `AudioRecorder` / `AndroidMediaRecorder`; `AudioPlayer` with waveform, position flow, and insight visualizations |
| `:core:transcription` | Core | `TranscriptionProvider`, `TranscriptionOrchestrator` (dedup + batching), OpenAI Whisper and Sherpa-ONNX implementations |
| `:core:transforms` | Core | `TransformationProvider`, `TransformationPipeline`, three built-in `DefaultProfiles`, Gemma 2 2B local provider |
| `:core:export` | Core | `ExportCoordinator`, Markdown / Text / JSON / Obsidian exporters |
| `:core:database` | Core | Room database, entities, DAOs |
| `:core:datastore` | Core | DataStore preferences (API keys, theme, recording defaults, model selection) |
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
| Sherpa-ONNX | — | On-device Whisper transcription |
| MediaPipe | — | On-device Gemma 2 2B transforms |
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
* **Android Studio Ladybug** or later
* An **OpenAI API key** — or use fully-local mode (no key required; download on-device models in Settings)

### Build & run

```bash
cd apps/scrybe

# Build a debug APK
./gradlew assembleDebug

# Install on a connected device / emulator
./gradlew installDebug
```

After first launch, open **Settings → API Configuration** and either paste your OpenAI API key or enable on-device mode to download Whisper and Gemma models.

### Containerized development

A Docker-based environment is available at the repo root for builds without a local Android toolchain:

```bash
docker compose build android-dev
docker compose run --rm android-dev ./gradlew assembleDebug
docker compose run --rm android-dev ./gradlew testDebugUnitTest
```

The container includes Gradle 8.9 on JDK 17, Android SDK platform tools, API 35, and build-tools 35.0.0.

---

## CI / CD

| Workflow | Trigger | What it does |
|----------|---------|-------------|
| `scrybe-ci.yml` | Push to `main`/`copilot/**`/`claude/**`, PRs to `main` | Changelog + manifest validation → assembleDebug, testDebugUnitTest, lint, ktlintCheck, detekt |
| `scrybe-release.yml` | Successful `scrybe-ci.yml` on `main` | Computes next version, promotes `CHANGELOG.md`, bumps `build.gradle.kts`, creates tag + GitHub Release with signed APK/AAB |

Release automation uses [conventional commits](https://www.conventionalcommits.org/): `feat:` bumps minor, `fix:`/`chore:` bump patch, `BREAKING CHANGE` bumps major. The `## Unreleased` section of `CHANGELOG.md` is promoted automatically.

Signing secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. If absent, a one-off keystore is generated so the APK remains installable.
