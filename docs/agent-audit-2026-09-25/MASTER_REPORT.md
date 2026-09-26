# TwoBits Monorepo — Cross-Cutting Audit Report

**Date:** 2026-09-25
**Method:** 12 parallel Haiku research agents, each auditing one dimension of the repo (security, AI integration, UX, UI styling, industry best practices, API key management, CI/CD, agentic scaffolding, debugging capability, and a separate unimplemented-capability sweep per app) — followed by 12 independent Haiku *validation* agents, one per audit, that re-checked every concrete claim against the actual source, git history, and (for the industry-practices audit) external documentation.
**Raw findings:** `docs/agent-audit-2026-09-25/*.md` (originals) and `*.VALIDATED.md` (fact-checked versions this report is built from).

---

## 0. On the audit process itself

The validation pass was not a formality. Across the 12 audits, roughly **1 in 5 concrete claims required a correction**, and in **three separate audits the single most prominent/CRITICAL finding was specifically the one that turned out wrong**:

- **API key management**: the original headline finding — *"the 'shared once' credential promise is broken (CRITICAL)"* — was backwards. `SharedCredentialClient.mirror()` is real and is correctly called from all three apps' `SettingsViewModel`s. The infrastructure works as designed.
- **UX**: the top recommendation — *"Shelf Snap's vision analysis is 100% cloud, add local processing"* — was backwards. Shelf Snap already has a working on-device vision path (`LocalVisionService`, LOCAL/BYOK/PRO modes); the real gap is that onboarding never tells the user it exists.
- **Shelf Snap unimplemented-capabilities**: the headline finding — *"three settings toggles are UI-only with zero backend implementation"* — was wrong for all three toggles. The auditor had only grepped the settings UI, not `ItemRepository.analysePhotos()` where they're actually read and applied.

There was also one fabricated API name (`PowerManager.forecast`, which doesn't exist — the real API is `PowerManager.getThermalHeadroom()`) caught in the industry-best-practices audit.

**Implication for how to read this report:** every finding below has survived independent re-verification against the actual codebase (file:line re-read) or, for external claims, a second research pass. Nothing here is taken on a single Haiku pass's word. Two audits (debugging capability, PriceDrop TODOs) came back with **zero** corrections needed — the accuracy floor varies a lot by task shape (narrow, mechanical fact-checks like "does this commit exist" validate cleanly; broad claims like "is this 100% cloud" are where models most often overreach).

---

## 1. Priority task list

Ranked by (confirmed impact) × (low effort / low risk to ship), grouped into three tiers. File:line citations are in the linked sections below.

### Tier 1 — Do next (small, high-confidence, real impact)

| # | Task | Why | Effort |
|---|---|---|---|
| 1 | **Fix Scrybe's release CI to run `lintRelease`, not debug `lint`** | Scrybe's release APK ships with Android Lint checks the other two apps' release builds actually run — a real coverage gap, one-line CI fix | Trivial |
| 2 | **Add a header-redacting (or remove) OkHttp BODY-level logging interceptor** | `shared/network/OkHttpClientFactory.kt` logs full `Authorization` headers (API keys) to logcat whenever `BuildConfig.DEBUG` is true | Low |
| 3 | **Encrypt `shared/api-keys/KeystoreApiKeyProvider`** | Stores API keys in plaintext DataStore despite its name; `shared/secure-store/CredentialCrypto` already implements the correct AndroidKeystore AES-256/GCM pattern one module over — copy it | Low–Medium |
| 4 | **Fix Scrybe's silent AI-insight fallback on token exhaustion** | `OpenAiInsightService` still silently defaults to neutral sentiment/empty topics when `gpt-5-mini` exhausts its reasoning-token budget (`status="incomplete"`); `OpenAiDiarizationService` had the identical bug and was already fixed to throw instead — port the same fix | Low |
| 5 | **Enable GitHub Dependabot for the Gradle ecosystem** | Zero dependency-vulnerability scanning exists anywhere in the repo today | Trivial |
| 6 | **Advertise Shelf Snap's local vision mode in onboarding** | The feature (on-device Gemma vision analysis) already exists and works; onboarding copy never mentions it, so almost no one finds it | Low |
| 7 | **Fix `.githooks/pre-commit` to validate all three apps' manifests, not just Scrybe's** | Shelf Snap/PriceDrop manifest errors are currently only caught in CI, never locally | Low |
| 8 | **Regenerate the Scrybe module table in `AGENTS.md`** | Lists 15 modules but is missing 6 real ones (`:core:backup`, `:core:local-ai`, `:feature:tasks`, `:feature:file-manager`, `:service:recording`, `:workers`), has one phantom entry (`:feature:history`), and misattributes a shared module (`:core:network`) as Scrybe-native — this actively misleads future agents (including future instances of this one) | Low |
| 9 | **Test `LocalAskSession` (PriceDrop) against sustained/idle use on a real device** | Flagged independently by *two* separate audits (industry-best-practices and PriceDrop TODOs) — the code itself documents this as untested; cheap to validate, could prevent a real production crash | Low (device time only) |
| 10 | **Decide: implement or delete `PromotionProvider` (PriceDrop)** | Fully-defined interface, zero implementations, zero production callers — pure dead code today | Low |

### Tier 2 — Worth scheduling soon (real, medium effort)

| # | Task | Why |
|---|---|---|
| 11 | **Consolidate the three near-identical `ModelDownloadWorker` implementations** into `shared/local-ai` | Confirmed genuinely triplicated (download → SHA-256 verify → store), same pattern, three copies |
| 12 | **Add a lightweight breadcrumb/event log** (navigation transitions, service lifecycle, explicit checkpoints) to the shared Debug Log system | The single highest-ROI observability gap: the current system is AI-call-scoped only — 3 of the last 4 real bugs in this repo's own history (navigation cancellation, concurrency races, UI state issues) would **not** have been caught by it without a user manually describing symptoms and an engineer reading source |
| 13 | **Fix on-device (`LocalVisionService`) multi-photo support**, or at minimum surface the limitation in the UI when a user has multi-photo analysis enabled but is on LOCAL mode | Currently silently analyzes only the first photo regardless of the setting |
| 14 | **Add a crash-reporting backstop (Crashlytics or similar) for API level < 30** | `ApplicationExitInfo`-based crash capture is unavailable before Android 11; there's currently no fallback for that device population |
| 15 | **Resolve `reusable-build.yml`** — either wire it into the three per-app CI workflows (removing ~300 lines of duplicated inline build steps) or delete it | Confirmed to exist and be completely unreferenced; as-is it's dead weight that could also mislead a future agent into thinking it's active |
| 16 | **Add "Processed on your device" badging to local-inference results** | Validated industry pattern (Pixel Recorder and others do this); users currently have no way to tell, per-result, whether a given AI call ran locally or in the cloud |

### Tier 3 — Real, but needs a product/design decision before engineering starts

| # | Task | Why |
|---|---|---|
| 17 | **Resolve PriceDrop's color palette divergence** (coral red/green/amber vs. Scrybe & Shelf Snap's blue/teal/orange) | This is a genuine brand-identity fork, not a bug — confirmed via actual hex values in all three `Color.kt` files. Needs a design decision (intentional differentiation vs. drift) before any code changes |
| 18 | **Unify the three apps' AI-provider abstraction** (Scrybe's `ProviderType`+interface, PriceDrop's `ProviderMode`+`AiFeature`, Shelf Snap's hardcoded baseUrl/authHeader params) into one shared shape | Real fragmentation, confirmed; but each app's current shape fits its own needs reasonably well today, so this is an investment call, not an urgent fix |
| 19 | **Add SQLCipher (or equivalent) encryption to all three apps' Room databases** | The most severe confirmed finding in the whole audit (transcripts, listing photos, price history all sit in plaintext SQLite) — but it's also the highest-effort item here, since it needs a real migration plan for existing installs, not just new-install code. Sequenced last only because of that migration cost, not because it matters less — it should be scoped seriously, not skipped |

---

## 2. Findings by area (verified only)

### Security — 01-security.md
- **CRITICAL, confirmed:** Room databases in all three apps (`Room.databaseBuilder()`) are plain unencrypted SQLite. No SQLCipher, no `setEncryptionKey()`.
- **CRITICAL, confirmed:** `shared/api-keys/KeystoreApiKeyProvider.kt` stores API keys in plain `androidx.datastore.preferences` despite its name — no AndroidKeystore, no `EncryptedSharedPreferences`. Contrast: `shared/secure-store/CredentialCrypto.kt` **does** correctly implement AndroidKeystore-backed AES-256/GCM — the fix pattern already exists in-repo.
- **HIGH, confirmed:** `shared/network/OkHttpClientFactory.kt:10-11` sets `HttpLoggingInterceptor.Level.BODY` whenever `BuildConfig.DEBUG` is true, which logs full `Authorization` headers to logcat.
- **MEDIUM, confirmed:** No certificate pinning anywhere.
- **LOW, corrected:** the unused-permission claim was backwards — `ACCESS_COARSE_LOCATION` **is** used (`LocationProvider.kt`); `READ_CALENDAR` is the actually-unused one.
- Everything else checked out clean: backup crypto (PBKDF2 + AES-256/GCM, 210k iterations, proper key wipe), WorkManager passphrase discipline, signature-gated cross-app IPC, no hardcoded secrets, sound ProGuard/R8 config.

### AI integration — 02-ai-integration.md
- Confirmed: three different provider-abstraction shapes across the apps (see Tier 3 above).
- Confirmed: `ModelDownloadWorker` triplicated near-identically across all three apps; shared `ModelDownloader.kt` SHA-256 verification is real and correctly implemented.
- Corrected: Scrybe's HTTP timeout is the shared factory default (30s connect / 20min read), **not** a custom 30/60s as originally claimed. Shelf Snap's own timeouts (vision 30/60s, listing 20/40s) were accurate as reported.
- Confirmed clean: `LocalInferenceGate`, `LocalInferenceMemoryGuard`, `LiteRtLmEngine` are well-designed, genuinely shared, no consolidation needed. System prompts are safe from injection across all three apps — user input is never interpolated into system-prompt text.

### User experience — 03-ux.md
- **Corrected (backwards):** Shelf Snap vision analysis is hybrid (LOCAL/BYOK/PRO), not 100% cloud — see Tier 1 #6.
- Confirmed: Scrybe's onboarding has no privacy/local-vs-cloud disclosure (Settings does provide the LOCAL/BYOK/PRO choice, but users have to find it themselves).
- Confirmed: Scrybe's TaskInbox has no way to manually create a task (tasks only come from AI extraction) — needs an empty-state CTA pointing at extraction.
- **Corrected (false):** Shelf Snap cross-listing *does* show a success snackbar and a "view live listing" link — original claim of no confirmation was wrong.
- Confirmed: Shelf Snap's `VisionAnalysisErrorTest.kt`-backed error mapping is good and worth replicating in Scrybe/PriceDrop, which currently have thinner error messaging.

### UI styling & standardization — 04-ui-styling.md
- Confirmed: Scrybe defines its own text-only `EmptyState()` (`FileManagerScreen.kt:202`) instead of the richer shared `AppEmptyState`.
- Confirmed: no spacing/dimension token library exists anywhere in `shared/design`; dp values are hand-typed throughout (verified: 2/4/8/10/14dp scattered in one settings file alone).
- Confirmed via real hex values: PriceDrop's palette (coral red `#C0382A`/`#FF8066`, green `#1E8E5A`/`#88D7A8`, amber `#9A6A00`/`#FFD580`) is a completely different color family from Scrybe/Shelf Snap's shared blue/teal/orange (`#005B99`, `#1A7F8A`, `#B85C38`) — see Tier 3.
- Corrected: `AppSectionCard` is used 19 times in Scrybe (not 14 as first reported), and genuinely 0 times in the other two apps.
- Confirmed: 68 `AlertDialog`/`ModalBottomSheet` usages in Scrybe alone, no shared dialog/sheet pattern library.
- Confirmed clean: all three apps share the same Compose BOM version and correctly reference shared `TwoBitsTypography`/`TwoBitsShapes` tokens — no Material2 backcompat debt anywhere.

### Industry best practices (Pixel/Android on-device AI) — 05-industry-best-practices.md
- Overall verdict, confirmed: this repo's on-device AI plumbing (resumable/verified downloads, one-engine-per-process gate, memory-headroom checks that scale with context window) is **ahead of** typical Android app practice, not behind it. Gaps found are incremental.
- **Corrected — fabricated API:** the original report's "`PowerManager.forecast` (Android 14+)" does not exist. The real API is `PowerManager.getThermalHeadroom(int forecastSeconds)`.
- Confirmed real gap: no "processed on your device" badging on individual inference results (industry-standard pattern, e.g. Pixel Recorder) — see Tier 2 #16.
- Confirmed real gap: no thermal-throttling awareness — the 90-second hard generation timeout doesn't adapt to device temperature; `getThermalHeadroom()` could allow voluntary backoff before the OS forces it.
- Confirmed (also independently found in the PriceDrop TODO audit): `LocalAskSession`'s sustained/idle-use safety is explicitly untested — see Tier 1 #9.
- Not actionable: ML Kit GenAI APIs and AICore were both confirmed real and accurately described, but neither fits this repo's requirements (multi-turn conversation, broader device support than Pixel-9-first rollout, non-Gemini models like Whisper) — correctly assessed as not worth adopting.

### API key management — 06-api-key-management.md
- **Corrected (the headline finding was wrong):** the "shared once" cross-app credential mirroring **is** fully wired — all three apps' `SettingsViewModel`s call the real `SharedCredentialClient.mirror()` on save. A user with all three apps only has to enter their OpenAI key once.
- Confirmed: Scrybe's OpenAI-only credential scope is an intentional architectural boundary (transcription-only app), not a bug.
- Confirmed: the three apps' credential-entry UI components (`ProviderCredentialItem`, `CollapsibleProviderRow`, `AiCredentialsDock`) are real, and two of the three are already properly shared via `shared/design` — duplication here is moderate, not "massive" as first claimed.
- Real, small finding: the `COUPON` credential type is reserved in the provider enum but never implemented or exposed anywhere — decide to build it or remove the placeholder.

### CI/CD, linting, workflows — 07-cicd-linting-workflows.md
- Confirmed: `.github/workflows/reusable-build.yml` exists and is completely unreferenced by any workflow — all three per-app CI workflows duplicate its build logic inline instead.
- Confirmed, HIGH risk: Scrybe's CI runs debug `lint`, Shelf Snap/PriceDrop run `lintRelease` — inconsistent coverage for a task that specifically exists to catch release-build issues.
- Confirmed: pre-commit hook only validates Scrybe's `AndroidManifest.xml` locally; CI validates all three. Asymmetric local feedback.
- Confirmed: no Android Lint task ever runs against `shared/` library modules, only against apps at integration time.
- Confirmed: zero dependency-vulnerability scanning, secret scanning, or SAST anywhere in the pipeline.
- Confirmed clean / working well: Gradle configuration cache enabled in all three apps; release automation (semantic versioning from conventional commits, stale-release detection, automated changelog promotion) is genuinely sophisticated and correctly implemented.

### Agentic scaffolding (AGENTS.md / CLAUDE.md / .claude/) — 08-agentic-scaffolding.md
- Confirmed (corrected count): the Scrybe module table in `AGENTS.md` lists 15 modules (not 11), is missing 6 real ones, has one phantom entry, and misattributes a shared module as Scrybe-native — see Tier 1 #8.
- Confirmed: `apps/scrybe/settings.gradle.kts:15-53`'s Sherpa-ONNX pre-download bootstrap is real and correctly described, but undocumented anywhere as a "don't refactor this without understanding why" gotcha for future agents.
- Confirmed: `/project:android-ui` is entirely `pwsh -File`-based (Windows/PowerShell) with no platform gate or Linux alternative, despite being presented as a general workflow.
- Confirmed: changelog rules are independently duplicated across three files (`AGENTS.md`, `.claude/commands/update-changelog.md`, `.agents/skills/update-changelog/SKILL.md`) — currently in sync, but a real drift risk if any one is edited alone. Same pattern found for the Android UI workflow docs.
- Confirmed: `.agents/` (Codex-specific skills) exists but isn't mentioned in either `CLAUDE.md` or `AGENTS.md`.
- Confirmed clean: all 8 documented CI-failure gotcha patterns, the pre-commit hook behavior description, and the detekt rule thresholds in `AGENTS.md` are all still accurate against the live repo.

### Debugging capability — 09-debugging-capability.md
- All numeric/architectural claims confirmed exactly: 150-entry cap, 1MB file cap, 16KB per-entry native-trace cap, oldest-first rotation (`DebugLogStore.kt:606/612/613`); `ApplicationExitInfo` correctly gated at API 30+; cross-process file-locking (commit `82d54f8`) is sound; no automatic network transmission anywhere in the debug-log code, only user-initiated `Intent.ACTION_SEND` export.
- Five specific git commits cited by the original audit (`82d54f8`, `5d3e9b4`, `32dc950`, `7db6806`, `90b6a85`) were all independently confirmed to exist and accurately match their described bug fixes.
- **Key verdict, confirmed:** the system is AI-call-scoped only (transcribe/diarize/ask/vision/web-search/model-download op types) — it logs **no** navigation, general lifecycle, UI-state, or background-service events. Of this repo's own last four significant real bugs, three (navigation cancellation, concurrency races, UI state corruption) would not have been surfaced by this system on their own.
- Top recommendation, confirmed sound: add lightweight breadcrumb/checkpoint logging — see Tier 2 #12.

### Scrybe — unimplemented capabilities — 10-scrybe-todos.md
- Confirmed, real and currently live: `OpenAiInsightService` silently falls back to neutral sentiment/empty topics via `.ifBlank` when the `gpt-5-mini` model exhausts its reasoning-token budget (`status="incomplete"`) — see Tier 1 #4.
- **Corrected:** the audio-chunking size threshold is 20MB, not 25MB as first reported; supported chunking formats are M4A/MP4/AAC/WEBM, not M4A-only.
- **Corrected (false):** the diarization "silent single-speaker fallback" and "silent JSON-parse fallback" bugs the audit flagged have **already been fixed** in the current code — both paths now throw a proper `IOException` with diagnostic detail instead of silently defaulting. The Insights service (finding above) is the one place this class of bug still exists.
- Minor, low-priority, by design: a couple of informational-only UI chips (`onClick = {}`) for orphaned-file and token-count display — not bugs, just non-interactive by design.

### Shelf Snap — unimplemented capabilities — 11-shelfsnap-todos.md
- **Corrected (the headline finding was wrong):** all three settings toggles the original audit called "UI-only, zero backend implementation" (`multiPhotoAnalysis`, `aiConditionDetection`, `autoPriceEstimate`) are in fact fully wired into `ItemRepository.analysePhotos()` and actively affect behavior.
- Confirmed, real: `LocalVisionService` (on-device path) only ever sends a single photo regardless of the multi-photo setting — the setting works correctly for cloud modes (PRO/BYOK) but silently doesn't apply on LOCAL mode.
- Confirmed, real: on-device vision analysis is explicitly labeled EXPERIMENTAL in the UI ("accuracy is unverified on this device") — a genuine data-quality risk users should be aware they're taking on.

### PriceDrop — unimplemented capabilities — 12-pricedrop-todos.md
- All three findings confirmed with zero corrections needed:
  1. `LocalAskSession`'s engine lifetime under sustained/idle use is explicitly documented in code as untested on a real device (cross-referenced by the industry-practices audit too — see Tier 1 #9).
  2. Coupon aggregation is explicitly flagged `"broad coupon aggregation remains experimental"` in its own feature metadata.
  3. `PromotionProvider` is a fully-defined interface with zero implementations and zero production callers anywhere in the codebase — confirmed via full-module grep, not assumption.

---

## 3. What this audit deliberately did not cover

This was a static/code-review pass by design — nothing here was validated against a running build, a real device, or actual user telemetry (this environment has no Android SDK or device). Several Tier-1/Tier-2 items above (`LocalAskSession` sustained-use safety, the memory-headroom factor calibration, on-device vision accuracy) are specifically "needs real-device verification" findings, not things that can be closed by code review alone.
