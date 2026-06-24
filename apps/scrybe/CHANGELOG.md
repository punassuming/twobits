# Changelog

## Unreleased

### Features

### Improvements

* **Playback area** — removed the redundant seek slider below the waveform; tap or drag anywhere on the waveform to seek (playhead indicator visible); the marker legend (topics / sentiment) and time labels remain unchanged
* **Session metadata** — audio quality details now show as separate labelled rows (Format, Quality, File size) instead of a single cramped line; sample rate is displayed with one decimal place where needed (e.g. "44.1 kHz" instead of "44 kHz")

### Fixes

* **Audio import** — MP3 and other formats that failed with "audio format may not be supported" now import correctly; metadata is read via file descriptor (more reliable across Android versions and codecs) and falls back to sensible defaults rather than blocking the import if metadata cannot be read
* **Custom recording types** — sessions recorded with a user-defined type are now stamped with mode `CUSTOM` (was `JOURNAL`); the history badge, mode filter chips, and waveform bar now use a secondary accent color and label icon to visually distinguish custom-type sessions from plain journal recordings
* **Import waveform** — imported audio files now generate a real waveform visualisation (amplitude over time via `MediaCodec` decode) instead of an empty bar; falls back gracefully to no waveform if decoding fails

## 1.23.0 (2026-06-24)

### Features

### Improvements

* **Format picker** — audio format in Settings now uses a dialog selector with a description of each codec (AAC, MP3, MP4, OGG, WAV, WEBM) instead of a cramped segmented-button row; the selected format's description is shown inline on the settings row

### Fixes

* **Settings build fix** — restored missing `SingleChoiceSegmentedButtonRow` / `SegmentedButton` / `SegmentedButtonDefaults` imports that were accidentally dropped when converting the audio format picker to a dialog; the Appearance / theme-mode segmented row uses them and would not compile
* **Profile draft error** — if the AI draft call fails, a "Draft failed" bottom sheet now appears with the error message and a Dismiss button; previously the error state was silent and the user was left looking at the profile list with no feedback
* **Profile review sheet** — added "Edit in full editor" button that opens the `ProfileEditorDialog` pre-populated with the AI draft so users can set the icon, color, mode, and other fields before saving


## 1.22.0 (2026-06-23)

### Features

* **New profile unified sheet** — "New Profile" now opens a single bottom sheet combining manual and AI-draft creation: Name field, Prompt field, a tappable model selector showing the selected model name and description (opens a full picker dialog with all available models), "Draft with AI" primary button, and "Save profile" direct-save button
* **Animated drafting progress sheet** — while AI generates a profile, a 3-step progress sheet (Context extraction → Profile drafting → Self-critique) shows animated step indicators and a call counter; replaces the former inline spinner
* **Review draft sheet** — after AI finishes, an editable text area shows the generated prompt; a "3 calls · N tokens" chip shows token usage; a "Refine with AI" input lets the user iterate; "Save profile" commits the edited text directly
* **People screen — rich speaker cards** — each person now shows in a card with a color-coded avatar circle (initial letter), inline rename icon, session count / talk-time % / segment count stats, an animated progress bar, and direct Merge and Delete icon buttons (replacing the 3-dot menu)
* **People screen — Re-identify** — "Re-identify" button in the top-right opens a confirmation dialog for re-running speaker diarization across all sessions

### Improvements

* **AI draft model selection consolidated to creation screen** — the "Profile draft model" setting has been removed from Settings → AI Config; model selection now lives exclusively in the New Profile sheet where it's used, persisting the last selection across drafts
* Shared design: `CollapsibleProviderRow` added to `ProviderCredentialCard.kt` — a new composable for credential rows that collapse to a Connected or Not Configured badge (no changes to existing `ProviderCredentialCard` callers)
* `ProfileSuggestion` now carries `tokensUsed` populated from OpenAI response usage; the Review draft sheet displays the total token count
* `PersonDao` gains `segmentCountForPerson` and `talkRatioForPerson` queries for the People screen stat row

### Fixes



## 1.21.0 (2026-06-23)

### Features

### Improvements

* Settings sections now use the shared gray uppercase section label (`AppSectionLabel`) rendered above the card — consistent with the design system spec and the other apps

### Fixes

* Profiles: profile card name and description text is now smaller (`titleSmall` / `labelSmall`) so all content fits on narrow screens without overflow
* Profiles: "New Profile" and "AI Draft" buttons no longer truncate their labels (e.g. "New Pro…") on narrow screens — reduced button content padding so the icon + full label fit
* AI credential rows (shared): status badge no longer wraps to two lines when the provider title is long — the badge stays on one line and the title ellipsizes instead




## 1.20.0 (2026-06-22)

### Features

### Improvements

* shared design module gains a reusable per-provider credential card (`ProviderCredentialCard`) and `ProTierCard` — adopted by PriceDrop and Shelf Snap; no change to Scrybe behaviour
* README: added a "Custom recording types" guide (system prompt authoring, `{{transcript}}` placeholder, persistence); expanded Quick Start provider table with BYOK/Pro/local key-setup steps

**AI Profile Draft dialog** — tightened layout and removed redundant seed fields:
* "Seed name" and "Seed description" fields removed — the AI generates a name and description from the request; users refine via the refinement section after the first draft
* dialog title scaled down from `headlineSmall` to `titleLarge` for better proportion

**Live transcript panel** — the recording screen now shows a contextual panel during capture:
* pulsing microphone indicator while recording ("Transcript will appear when recording stops")
* spinner + "Saving and transcribing…" during the stop/save phase
* static message when auto-transcription is disabled in Settings

**Playback marker legend** — color-coded dot strips are now self-describing:
* a compact legend row appears below the dot strips when there is data to show (topics and/or sentiment)
* orange dot = Topics, green = Positive, grey = Neutral, red = Negative

### Fixes

* custom recording type name now displays correctly in the history badge — previously all custom-typed sessions showed "Journal" because `customTypeId` was not mapped into `RecordingSession` and the badge only read `RecordingMode`
* audio import now shows an actionable error ("Could not read file — format may not be supported") instead of a raw hex code when `setDataSource` fails
* imported recordings now prompt for a date before import, defaulting to the file's last-modified timestamp; the chosen date is used as the session's `createdAt` so history sort order is correct
* fix build: add `@OptIn(ExperimentalMaterial3Api::class)` to `ImportTimestampDialog` (DatePicker/DatePickerDialog APIs still experimental in the Compose BOM in use)





## 1.19.0 (2026-06-22)

### Features

### Improvements

**Profile use count** — track and display how often each profile is used:
* "Used N×" caption shown on each profile card after at least one successful transform
* use count persists in the database (Room migration 14→15) and increments on every successful transform run

**Playback speed pill** — variable-speed audio playback:
* speed pill appears to the left of transport controls and cycles through 0.5×, 0.75×, 1×, 1.25×, 1.5×, 2× on each tap
* pill background switches to `primaryContainer` when speed is not 1× for at-a-glance visibility
* `AudioPlayer.setPlaybackSpeed()` implemented via `MediaPlayer.PlaybackParams` (API 23+, safe on minSdk 26)

### Fixes






## 1.18.0 (2026-06-21)

### Features

### Improvements

* `scrybe/core/network` deleted; `OkHttpClient` and `Json` providers merged into `shared/network`; `core:transcription` and `core:transforms` now depend on `com.twobits.core:network` (resolved via composite-build substitution)

### Fixes

* shared/design Compose BOM updated to 2024.12.01 (was hardcoded 2024.06.00)
* shared scripts (manage-changelog.py, validate-manifests.py, ci-gradle-retry.sh) moved from apps/scrybe/scripts/ to repo-level scripts/ so all three apps reference a single copy
* CI and release workflows consolidated into reusable-build.yml and reusable-release.yml; per-app workflows are now thin callers (~1 400 lines of copy-paste reduced to ~600)
* scrybe-ci.yml build job inlined (was calling reusable-build.yml which doesn't exist on main yet; will re-wire once reusable-build.yml lands)
* shared/settings.gradle.kts now declares the version catalog so shared modules can use libs.* references
* shared/network, shared/common, shared/billing, shared/api-keys build.gradle.kts now reference the shared version catalog (libs.*) instead of hardcoded version strings







## 1.17.0 (2026-06-19)

### Features

### Improvements

* migrated to shared `gradle/libs.versions.toml` version catalog across all three apps; upgraded Compose BOM to 2024.12.01, coreKtx to 1.15.0, lifecycleRuntimeKtx to 2.8.7, and navigationCompose to 2.8.5
* release workflow now uses `git rebase --autostash` so the changelog changes written by `promote-release` are preserved across the rebase instead of aborting it

### Fixes








## 1.16.0 (2026-06-19)

### Features

**Pro** — standalone subscription screen:
* tier comparison: Try it / Pro / BYOK side-by-side
* plan picker: annual ($1.99/mo) or monthly ($2.49/mo)
* usage dashboard when Pro is active (transcription minutes, transforms, sessions)
* why Pro highlights and BYOK explanation

**AI Config** — call budget tracker:
* "API calls per session" card below the credential dock showing 1 call for transcription and 1–2 for transforms
* dot indicators (primary / secondary color) and a plain-language note per call type

**Profile AI draft** — model picker and agentic refinement:
* segmented model picker in the AI Draft dialog: GPT-5 mini, GPT-5 nano, GPT-5, GPT-5.4 (persists to preferences)
* "Refine with AI" section appears after a draft is generated — send a follow-up instruction to reshape the prompt without starting over

### Improvements

* purchase/restore logic now runs through a shared `PurchaseDelegate` in the shared billing module, removing duplicated billing orchestration across the apps
* removed the empty `TranscriptionWorkerPlaceholder` stub from the workers module

### Fixes

* Pro plan picker now passes the selected plan (annual / monthly) to the purchase flow — previously always initiated a monthly purchase regardless of selection
* "Manage subscription" on the active Pro card now opens the Google Play subscriptions page instead of doing nothing
* the "Orphaned" recording badge in File Manager is no longer rendered as a tappable chip









## 1.15.0 (2026-06-17)

### Features

**App Icon** — launcher refresh:
* updated the Android launcher icon to better reflect the app's intent

### Improvements

**Build system** — infrastructure and dependency upgrades:
* upgraded Kotlin to 2.0.21 and KSP to 2.0.21-1.0.25
* migrated to the Kotlin 2.0 Compose compiler plugin; deprecated `composeOptions` and `kotlinCompilerExtensionVersion` removed from all modules
* renamed `:core:common` module to `:core:base` to prevent naming collisions...
* added `x86_64` to ABI splits to support running the app on standard Android emulators
* sherpa-onnx is now downloaded via native Java I/O in `settings.gradle.kts` (Gradle initialization phase) rather than via curl, removing the curl system dependency while guaranteeing the AAR is present before any dependency resolution occurs

**Shared billing** — `BillingManager` and `SubscriptionRepository` expose `getAppUserId()` returning the RevenueCat app user ID, used by Shelf Snap to authenticate Worker proxy calls

**License** — dual-licensing setup:
* added standard GPLv3 license to both Scrybe and Shelf Snap apps to establish open source rights while preserving commercial/Pro distribution capability

### Fixes

* fixed Kotlin DSL "minus" operator error in `:service:recording` by correcting the Version Catalog accessor for `play-services-location` to `libs.play.services.location`
* fixed CI failure ("Could not find com.k2fsa:sherpa-onnx-android:1.13.0") — Gradle dependency resolution is a configuration-phase operation that runs before any task executes, so a task-level `dependsOn` cannot supply the AAR in time; the download is now performed unconditionally during Gradle initialization in `settings.gradle.kts`
* changelog enforcement now requires new `## Unreleased` bullets, not just a file touch — a cleanup-only edit no longer satisfies the pre-commit hook or CI check










## 1.14.0 (2026-06-15)

### Features

### Improvements

**Recording** — back navigation and less-obtrusive status pill:
* pressing back (or the back arrow) while recording now shows the sessions list — the recording continues in the background via the foreground service
* returning to the recording view via the banner pill (or tapping "open") restores the full recording controls
* active-recording indicator replaced with a compact floating pill (pulsing red dot + elapsed time) instead of the full-width card that covered the top of every screen

### Fixes

* location tagging now works on Android 13+ — the API 33 `Geocoder.getFromLocation` callback is asynchronous but was being read before it fired, always returning null; fixed by suspending until the callback completes
* geocoder `onError` callback now resumes the coroutine with null instead of letting it hang until the outer timeout, so coordinates are still saved to the session even when reverse geocoding fails; corrected to use `Geocoder.GeocodeListener` (nested interface) instead of the non-existent `android.location.GeocodeListener`
* location capture timeout is now split: 5 s for the location fix, 3 s for geocoding — a slow or failing geocoder no longer causes the session to lose its coordinates
* location capture now falls back to `PRIORITY_HIGH_ACCURACY` when the balanced-power request returns no fix (e.g. cold start with no cached location)
* tapping the recording pill while already on the sessions list (minimized recording) now correctly restores the recording controls — previously the `launchSingleTop` navigate was a no-op and the lifecycle effect did not refire
* start FAB is hidden while a recording is minimized — previously it remained visible and tapping it could send a second ACTION_START to the foreground service while a recording was already in progress










## 1.13.0 (2026-06-13)

### Features

**Custom recording types** — extend the mode picker with your own recording categories:
* tap "+ New type" at the bottom of the mode picker to create a named type with an optional default transform profile
* custom types appear in the mode picker alongside the built-in modes
* recordings captured with a custom type store the type ID; if a default profile is set, it auto-applies after transcription when auto-transcribe is on

**People management** — new screen accessible from Settings → Intelligence:
* lists all speaker profiles with session counts
* rename any person inline
* merge two people — all speaker assignments move to the target, source is removed
* delete a person with confirmation

### Improvements

**AI configuration** — model picker readability:
* cost label moved below the model subtitle rather than trailing the row — fixes extreme text wrapping (model names broken to one word per line) on the narrow AlertDialog layout
* cost label only shown when non-blank, so the Profile Draft model picker keeps its two-line rows clean

**What's New dialog** — structured release notes:
* multi-bullet entries now display as individual "• item" lines instead of a single dot-separated paragraph, making release notes easier to scan

**File Manager** — import audio from device storage:
* new "+" button in the File Manager toolbar opens the system file picker filtered to audio files
* selected file is copied into Scrybe's recordings folder and registered as a new recording ready for transcription
* orphaned recordings already present in the recordings folder continue to appear automatically with an Import button to register them

**Session playback** — waveform and indicator layout:
* waveform canvas height increased from 80 dp to 100 dp for a more prominent display
* sentiment dots (positive/negative/neutral) moved to a dedicated 14 dp strip below the waveform — no longer overlap the waveform bars
* topic/intent dots moved to a dedicated 14 dp strip above the waveform — no longer overlap the waveform bars
* seek bar track reduced from ~4 dp to 2 dp for a cleaner appearance; thumb size unchanged

**History** — alphabetical sort option:
* filter dialog sort section now includes "Name A→Z" to sort sessions by title

**Transcript editing** — pre-formatted with paragraph breaks:
* edit dialog now opens with the same sentence-level paragraph breaks and speaker labels that the read-only view shows, giving a clean starting point for edits

### Fixes

* File Manager imports now read duration, sample rate, bitrate, and channel count from the file using MediaMetadataRetriever — previously all four values were stored as zero, causing recordings to display as 0 sec with no cost or insight data
* File Manager imports now preserve the correct file extension for MP3 (audio/mpeg) and WAV (audio/wav) files — previously both were saved as .m4a, causing transcription to label them as audio/mp4
* AudioFormat enum extended with MP3 and WAV entries; exhaustive when expressions in AndroidMediaRecorder updated to handle both (mapped to AAC/MPEG-4 fallbacks — these formats are import-only and never used for live recording)
* delete person confirmation dialog title had Unicode curly-quote string delimiters (U+201C/D) instead of ASCII `"` — replaced so the file parses correctly
* deleting a person now clears their ID from all speaker segments before removing the person row — previously sessions kept stale personId references pointing to a nonexistent profile
* custom recording type ID no longer cleared when a stop/pause/resume command arrives — only ACTION_START updates the pending custom type, so auto-transform runs with the correct profile after stop
* back navigation no longer blocked during an active recording — the foreground service continues regardless of which screen is visible
* folder groups now remain visible while searching in folder mode; empty folders are hidden from results instead of collapsing to a flat list
* location tagging toggle moved from Intelligence to Recording section — it controls recording behaviour, not AI processing










## 1.12.0 (2026-06-11)

### Features

### Improvements

**AI configuration** — unified model picker modal:
* "Transform model" row now opens a modal showing model name, supporting text, and cost — replaces the inline radio list
* "Profile draft model" modal upgraded to the same style showing name and description
* both pickers use a scrollable `AlertDialog` with radio buttons and consistent layout

**Diarization** — smarter speaker detection:
* removed the hard 0.8s gap gate — short gaps no longer prevent the model from switching speakers
* model now looks for conversational cues (questions, replies, discourse markers) to detect turns within shorter gaps
* added "Split" button per speaker in the speaker management sheet to manually split a segment at a given timestamp (MM:SS)
* added "Re-run identification" button at the bottom of the speaker sheet to re-run diarization on the existing transcript
* talk time and percentage displayed per speaker row (e.g., "2m 14s · 43%")

**Search** — capture screen visibility fix:
* search bar moved above the session list so it appears immediately when the search icon is tapped, regardless of scroll position
* search now also matches transcript preview text
* "no results" empty state shown when search returns zero sessions

**Search** — folder context in history results:
* session cards in History search results now show a folder chip (folder icon + name) so users know which folder each result belongs to

### Fixes

* fixed compiler error in speaker management sheet caused by nested lambda destructuring










## 1.11.0 (2026-06-11)

### Features

### Improvements

**Settings** — design alignment:
* Profiles card moved directly below the Pro subscription card
* new Intelligence section groups AI configuration nav row and location tagging toggle
* location tagging toggle removed from Recording Behavior (now in Intelligence)
* AI Config subtitle updated to include "local models" in the feature list
* Profiles card description updated to mention "AI transforms + destinations"

**Capture** — design alignment:
* task nudge banner now uses primary container colors with a trailing arrow icon
* folder groupings no longer render while a search query is active; flat list shown instead

**Session detail** — design alignment:
* tabs replaced with underline-style indicator: 2 dp primary bottom border on active tab, no filled background
* seek bar added below the waveform for precise scrubbing: primary-colored track, glow-ring thumb
* transcript tab shows speaker color pills (colored 8 dp squares + labels) above the transcript when multiple speakers are detected

### Fixes










## 1.10.0 (2026-06-11)

### Features

### Improvements

**What's New dialog** — structured release notes:
* update popup shows bold item titles with plain descriptions instead of raw flat bullets
* markdown code ticks and bold markers no longer leak into parsed release notes

**Navigation** — return to single-screen home:
* bottom navigation bar removed; Capture is the home screen again
* Settings opens from the top-bar gear, Profiles from Settings

**Settings** — reorganized menu:
* AI configuration card moved to the top, right after the Pro subscription card
* duplicate Auto-transcribe toggle removed; the Intelligence card is dissolved into Recording Behavior
* Recording Defaults renamed to Recording, Recording Automation to Recording Behavior
* Location tagging now lives with the other recording behaviors; Appearance moved below the recording sections

**Diarization** — debug mode for speaker identification:
* new "Diarization debug" toggle in AI configuration → Analysis
* session screen shows a debug card with raw speaker segments, timestamps, gaps, and the model's raw response
* verbose logging under the "Diarization" tag covers word timestamps, the LLM exchange, and merge decisions

### Fixes

* copying a transcript now copies what is shown on screen — paragraph breaks and speaker labels included
* diarization debug logs and persisted debug records now only written when the Diarization debug toggle is on — transcript content no longer stored or logged unconditionally
* model selection rows clamp long subtitles to two lines so the cost label stays aligned










## 1.9.0 (2026-06-08)

### Improvements

**Settings** — privacy policy link in About section:
* new "Privacy policy" row opens the policy page in the browser

**AI configuration** — redesigned screen with shared design components:
* credentials panel now shows Save, Clear, and Test buttons side-by-side in the BYOK key field
* masked API key subtitle displayed in monospace when a key is set
* Pro/BYOK/Local source tabs redesigned as color-coded pills
* cloud transcription BYOK mode shows Whisper 1 / Whisper Large model picker with per-minute cost labels
* Gemma 3 1B and 4B GGUF models replace the previous Gemma 2 MediaPipe models; import a .gguf file from HuggingFace
* Whisper Medium model added to local speech-to-text options

### Fixes

* CI now builds assembleRelease so R8 minification runs on every PR, catching ProGuard stripping issues before they reach the release workflow










## 1.8.3 (2026-06-06)

### Fixes

* fix R8 release build — add `-dontwarn` rules for commons-compress optional codec back-ends (XZ/LZMA via `org.tukaani.xz`, Zstandard via `com.github.luben.zstd`, Brotli via `org.brotli.dec`) that are absent from the bundled runtime; fix invalid `INSTANCE <fields>;` wildcard in serializer keep rule










## 1.8.2 (2026-06-06)

### Improvements

**ProGuard** — broader rules for serialization and networking:
* covered kotlinx-serialization serializer companions, commons-compress SPI factories, and generic-signature retention for Retrofit — prevents potential R8 stripping of runtime-required classes

### Fixes

* fix release APK staging — glob changed from `app-release*.apk` to `app-*release.apk` so ABI-split filenames (`app-arm64-v8a-release.apk`) are matched correctly










## 1.8.1 (2026-06-06)

### Improvements

**GitHub Actions** — release workflow trigger restriction:
* workflow no longer fires on PR CI completions — `branches: [main]` filter added to `workflow_run` trigger so it only activates when CI runs against `main`

### Fixes

* fix R8 release build — add `-dontwarn` rules for protobuf annotation types referenced by MediaPipe LLM inference library but absent from its bundled protobuf-lite runtime










## 1.8.0 (2026-06-06)

### Improvements

**GitHub Actions** — CI trigger optimization:
* CI no longer fires duplicate runs — `push` trigger now restricted to `main` only; feature branches trigger CI exclusively via the `pull_request` event










## 1.7.0 (2026-06-05)

### Features

**Session Playback** — PlaybackCard visual redesign:
* speaker colour palette updated to design tokens — signal blue `#89C7FF`, glow green `#88D7A8`, ember `#FFB695`, purple `#C6A0F6`
* centred transport controls (skip‑10s / play‑pause / skip‑10s) with `Arrangement.Center`
* waveform card height reduced to 80dp; `ScrybeSectionHeader` removed from playback

**Capture** — RecordingActiveView content area:
* recent-session mini-list replaces the empty spacer between the waveform card and stop controls
* each row shows mode badge, session title, duration, and status chip
* tapping a row navigates to that session while the recording continues in the background

### Improvements

**GitHub Actions** — release workflow validation:
* fail release workflow before commit/tag if keystore secret is invalid — early `Validate keystore secret` step catches bad base64 before any irreversible state is created; add `rebuild_for_tag` workflow_dispatch input to build and upload an APK for an existing tag after fixing secrets










## 1.6.2 (2026-06-05)

### Improvements

**GitHub Actions** — keystore validation:
* fail release workflow before commit/tag if keystore secret is invalid — early `Validate keystore secret` step catches bad base64 before any irreversible state is created; add `rebuild_for_tag` workflow_dispatch input to build and upload an APK for an existing tag after fixing secrets










## 1.6.1 (2026-06-04)

### Improvements

**GitHub Actions** — duplicate release prevention:
* add duplicate release prevention — `has-new-unreleased-since-tag` subcommand in `manage-changelog.py` compares current `## Unreleased` bullets against the last tag; both release workflows use this to skip releases when all bullets are already in a versioned section










## 1.6.0 (2026-06-04)

### Improvements

**GitHub Actions** — consolidated CI/CD workflows:
* shared `reusable-validate.yml` for changelog and manifest validation; rename `android-ci.yml` → `scrybe-ci.yml` and `release.yml` → `scrybe-release.yml`; standardise signing secret names to `SIGNING_*` across both apps; add `workflow_dispatch` trigger to release workflows

**Documentation** — unified authoritative guides:
* merge `CLAUDE.md` into `AGENTS.md` as the single authoritative agent instruction file; update `README.md` to describe the TwoBits monorepo with both apps and the worker; fix stale `android-whispering` path references in `CONTRIBUTING.md`

**Build system** — standardised changelog location:
* move Scrybe changelog from repo root to `apps/scrybe/CHANGELOG.md` (matching Shelf Snap's `apps/shelf-snap/CHANGELOG.md`); update all CI workflow, pre-commit hook, manage-changelog.py, AGENTS.md, copilot-instructions.md, and codex skill references to new path; add per-app changelog gate in pre-commit hook covering both apps; fix `app/build.gradle.kts` changelog asset path accordingly

**Documentation** — per-app README files:
* create `apps/scrybe/README.md` with full feature table, recording modes, AI provider tiers, architecture diagram, module map, tech stack, and CI docs; expand root `README.md` into a TwoBits project overview covering design philosophy, privacy commitments, shared infrastructure, and monorepo layout; update `apps/shelf-snap/README.md` CI section to reference current workflow names










## 1.5.0 (2026-06-04)

### Improvements

**GitHub Actions** — unified CI/CD setup:
* consolidate CI/CD workflows — shared `reusable-validate.yml` for changelog and manifest validation; rename `android-ci.yml` → `scrybe-ci.yml` and `release.yml` → `scrybe-release.yml`; standardise signing secret names to `SIGNING_*` across both apps; add `workflow_dispatch` trigger to release workflows

**Documentation** — monorepo migration and developer guidelines:
* unify documentation — merge `CLAUDE.md` into `AGENTS.md` as the single authoritative agent instruction file; update `README.md` to describe the TwoBits monorepo with both apps and the worker; fix stale `android-whispering` path references in `CONTRIBUTING.md`










## 1.4.0 (2026-06-03)

### Features

**AI configuration** — vision model selector in Shelf Snap:
* add vision model selector for BYOK users in Shelf Snap — free-tier users can choose from GPT-4o, GPT-4o mini, GPT-5.4, GPT-5.4 mini, or GPT-4.1 mini for item photo analysis; selection persists in DataStore; Pro users continue to use the worker default

### Improvements

**Managed API Proxy** — backend validation:
* harden worker model validation — reject chat completions requests for any model not in the pricing table with HTTP 422 instead of silently falling back to gpt-5-mini pricing; prevents unexpected charges for newly added or expensive models

**Managed API Proxy** — spend tracking serialization:
* serialize worker spend tracking with Durable Objects — replace KV read-modify-write spend accounting with a SpendTracker Durable Object that atomically reserves budget before forwarding to OpenAI and settles to actual cost afterward; eliminates the race condition where concurrent requests could each read the same KV total and bypass the monthly spend cap










## 1.3.0 (2026-06-03)

### Improvements

**Managed API Proxy** — vision pricing documentation:
* annotate worker vision support — gpt-4o and gpt-4o-mini entries in the Cloudflare Worker pricing table now carry explicit vision notes; image tokens are counted inside prompt_tokens by OpenAI so no separate billing path is needed










## 1.2.0 (2026-06-03)

### Features

**Marketing** — TwoBits landing site:
* add twobits GitHub Pages marketing site — four-page static site in docs/ (index.html, scrybe.html, shelf-snap.html, privacy.html) copied pixel-faithfully from the Claude Design handoff; shared site.css with full design token system (DM Sans, dark palette, signal/ember/brand color scheme); pages workflow deploys to GitHub Pages on push to main; all inter-page links use clean URL-friendly filenames; covers landing, Scrybe product + Play Store listing, Shelf-Snap product + Play Store listing, Privacy data tables + FAQ

**Billing** — interceptor sheet:
* add ProGate composable to core:design — ModalBottomSheet paywall interceptor with two paths: “Go Pro” (purchase) and “Use your own API key” (navigate to settings); drop it anywhere an AI feature needs to be gated behind Pro or a BYOK key

### Improvements

**Managed API Proxy** — user spend caps:
* upgrade managed API proxy — rate-limited Cloudflare Worker now enforces a $2.00/month per-user spend cap tracked in KV; full GPT-5 and GPT-4.1 model pricing table (gpt-5-nano/mini/5/5.1/5.4/5.4-mini, gpt-4.1-nano/mini, gpt-4o/mini, whisper-1) synchronized with app model enums; vision calls via /v1/chat/completions billed correctly through prompt_tokens (image tiles counted server-side by OpenAI); streaming responses use include_usage injection to track token cost without buffering; spend keys auto-expire after 35 days

**GitHub Actions** — release trigger validation:
* fix Scrybe release stale check — release.yml now only considers itself stale when Scrybe-relevant files changed on main after the triggering commit; a concurrent shelf-snap version-bump push to main no longer suppresses a valid Scrybe release; push step retries with rebase to handle the concurrent-push race condition

**GitHub Actions** — CI validation alignment:
* align shelf-snap CI validation with Scrybe — shelf-snap-build.yml now runs changelog and validate jobs before building (validate-manifests.py --root apps/shelf-snap, manage-changelog.py validate + check-updated against apps/shelf-snap/CHANGELOG.md); shelf-snap-tag-release.yml replaces inline Python changelog promotion with manage-changelog.py has-unreleased-bullets + promote-release (skips release when no bullets, matching Scrybe's pattern); validate-manifests.py gains a --root argument so both apps reuse the same script; android-ci.yml updated to pass --root apps/scrybe explicitly; CLAUDE.md documents the shelf-snap changelog requirement

**GitHub Actions** — trigger paths and artifact collection:
* unify CI artifact output and triggers — android-ci.yml now uploads the Scrybe debug APK as a downloadable artifact and triggers on claude/** and copilot/** feature branches; shelf-snap-build.yml restructured to match Scrybe's single-Gradle-invocation pattern (lint + test + assembleDebug in one pass), drops redundant release APK build from CI (covered by shelf-snap-tag-release.yml on main), and reduces permissions to contents:read; all four shelf-snap workflows upgraded from setup-android@v3 to @v4 with explicit platform and build-tools package selection

**Build system** — cross-module AndroidX property support:
* fix composite build AndroidX property — add shared/gradle.properties with `android.useAndroidX=true` so the shared library modules (billing, design, etc.) resolve RevenueCat and Compose dependencies correctly; add pipefail to shelf-snap CI Gradle steps so build failures propagate correctly

**Build system** — SettingsScreen compilation error fix:
* fix SettingsScreen compile errors — add missing imports for CircularProgressIndicator, Spacer, and width used in the Pro subscription button; remove duplicate AutoAwesome import

**Build system** — AGP version pinning:
* align shelf-snap AGP to 8.7.3 — composite builds require a single AGP version across all included builds; shelf-snap was on 8.4.0 which conflicts with shared/ modules pinned to 8.7.3

**Build system** — KtLint compliance fix:
* fix BillingProviderModule KtLint violation — multiline BillingConfig(...) constructor call must start on a new line after the `=` operator per the multiline-expression-wrapping rule

**Theme** — shared typography and shapes system:
* migrate both app themes to shared design tokens — scrybe and shelf-snap now use TwoBitsTypography (DM Sans) and TwoBitsShapes from core:design; local Type.kt and Shape.kt become single-line aliases so existing symbol references continue to compile

**Theme** — shelf-snap dark mode:
* add ThemeMode support to shelf-snap — ShelfSnapTheme now accepts ThemeMode (SYSTEM/LIGHT/DARK) from core:design, matching scrybe's existing dark-mode architecture; wiring a settings toggle reads ThemeMode from DataStore

**GitHub Actions** — shared dependencies build triggers:
* both apps now rebuild when shared/** changes — android-ci.yml and shelf-snap-build.yml path filters now include shared/** alongside their respective app paths; shelf-snap tag-release also triggers on shared changes

**Build system** — standardized versionCode formula:
* align shelf-snap versionCode formula to scrybe — tag-release workflow now computes versionCode as major×1 000 000 + minor×1 000 + patch (e.g. 1.2.3 → 1002003) matching scrybe's formula; Play Store requires only monotonic increases so the jump from 2 is valid

**Billing** — Pro subscription integration:
* add Pro subscription tier — users can upgrade to Scrybe Pro ($1.99/month) via Google Play for managed OpenAI API access without requiring a personal API key; subscription status is surfaced at the top of the Settings screen

**Billing** — RevenueCat SDK wiring:
* add RevenueCat billing integration — core:billing module wraps RevenueCat Purchases SDK with a SubscriptionRepository and BillingManager providing subscription tier as a StateFlow; purchase, restore, and refresh flows are coroutine-based

**Build system** — core dispatchers and results module:
* add core:common shared module — Result<T> sealed interface, ReleaseNotesParser, AppDispatchers qualifier and enum, and a Hilt DispatchersModule providing IO and Default coroutine dispatchers under com.twobits.common

**Build system** — credentials manager shared module:
* add core:api-keys shared module — ApiKeyProvider interface, KeystoreApiKeyProvider (DataStore-backed, keyed by ProviderType), ApiKeyValidator, ApiKeyRouter (routes BYOK vs. managed Pro keys via api.twobits.app), ProUserIdProvider interface, and a Hilt ApiKeysModule under com.twobits.apikeys

**Build system** — networked operations client module:
* add core:network shared module — OkHttpClientFactory (configurable logging, AI-tuned timeouts), HttpErrorMapper for user-friendly HTTP error strings, and a Hilt NetworkModule providing a singleton OkHttpClient under com.twobits.network

**Build system** — design library core:
* add core:design shared module — DM Sans variable-font family, TwoBitsTypography (full Material 3 type scale), TwoBitsShapes (8/12/18/24/28dp radius scale), ThemeMode enum (SYSTEM/LIGHT/DARK), and shared Compose components: ApiKeyField, SubscriptionBanner, SettingsRow, ErrorCard, LoadingOverlay under com.twobits.design

**Rebrand** — monorepo unified marketing layout:
* rebrand monorepo to Two Bits — Cloudflare worker renamed to twobits-proxy, custom domain api.twobits.app configured in wrangler.toml, worker setup docs updated with deployment URL

**Build system** — monorepo code migration:
* migrate to monorepo — Shelf Snap app moved into apps/shelf-snap alongside apps/scrybe; all CI workflows unified under .github/workflows with per-app path filters; CLAUDE.md updated for monorepo layout

**Build system** — directory renaming:
* rename app directory from apps/android-whispering to apps/scrybe for clarity within the monorepo

**GitHub Actions** — automated Play Store assets build:
* release workflow now builds both APK and AAB (App Bundle) artifacts — AAB is attached to GitHub Releases for direct Google Play upload

**Settings** — billing banner display:
* settings screen surfaces Pro subscription card at the top with upgrade, restore, and error dismissal actions

### Fixes

**Billing** — monorepo composite billing consolidation:
* consolidate billing into shared core/billing module — BillingManager, SubscriptionRepository, SubscriptionTier, BillingConfig, and PurchaseCancelledException live once in core/billing under com.twobits.billing; both apps reference it via Gradle composite build (includeBuild); per-app Hilt BillingProviderModule supplies the RevenueCat key

**Billing** — Purchases SDK upgrade regression:
* fix BillingManager compile error — replace removed purchaseWith() with RevenueCat 8.x purchase(PurchaseParams, PurchaseCallback) API
