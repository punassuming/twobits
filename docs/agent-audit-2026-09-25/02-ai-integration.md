# AI Integration Architecture Audit — TwoBits Monorepo

**Date**: 2026-09-25  
**Scope**: Audit of AI integration patterns across Scrybe, Shelf Snap, and PriceDrop, including shared/local-ai and cloud provider integration.

## 1. Local On-Device Inference

**Shared infrastructure** (excellent pattern):
- **LiteRtLmEngine** (`shared/local-ai/src/main/kotlin/com/twobits/localai/LiteRtLmEngine.kt:71-283`): LiteRT-LM wrapper for text and vision inference; includes `generate()` / `generateWithImage()`, built-in timeout handling (90s default), heartbeat callbacks for progress tracking, and a convenience `withLocalLlmEngine()` one-shot wrapper.
- **LocalInferenceGate** (`shared/local-ai/src/main/kotlin/com/twobits/localai/LocalInferenceGate.kt:24-61`): Process-wide mutual exclusion (one model at a time, across engines). Intentionally shared gate for both LiteRT-LM and Whisper — prevents multi-gigabyte models from resident simultaneously. 180s timeout guards against leaked gates.
- **LocalInferenceMemoryGuard** (`shared/local-ai/src/main/kotlin/com/twobits/localai/LocalInferenceMemoryGuard.kt:53-104`): Pre-flight check before loading; scales memory allowance with context-window size (KV cache is linear in token count); uses rough headroom factors (1.1× for text, 1.3× for vision) to guard the OOM-kill case where native load crashes without exception.

**Scrybe-specific speech-to-text** (implementation detail, not duplicated):
- **WhisperEngine** (`apps/scrybe/core/local-ai/src/main/kotlin/dev/scrybe/core/localai/WhisperEngine.kt:25-196`): sherpa-onnx offline Whisper wrapper. Chunks audio into 28s windows (encoder has 30s fixed context) and concatenates results; abandoned decode calls remain resident on a detached scope (cannot interrupt JNI) but release is deferred until complete. Uses 180s timeout per chunk.
- **AudioDecoder** (`apps/scrybe/core/local-ai/src/main/kotlin/dev/scrybe/core/localai/AudioDecoder.kt:26-151`): Decodes audio files (MediaCodec) to PCM float arrays at source sample rate; handles stereo-to-mono downmix for Whisper; 180s timeout guards against codec deadlock.

**Call site consistency** — pattern is consistent across all three apps:
- **Scrybe**: WhisperTranscriptionProvider calls WhisperEngine via LocalInferenceGate.withGate(); local transforms (OpenAiAutoRenameService, InsightServiceFacade, etc.) call LiteRtLmEngine.withLocalLlmEngine().
- **Shelf Snap**: LocalVisionService calls LiteRtLmEngine.withLocalLlmEngine() with visionBackend set for generateWithImage(); LocalListingService uses text inference for fallback listing generation.
- **PriceDrop**: LocalAskSession holds one engine open across a multi-turn conversation (not load-use-close), calls engine.generate() repeatedly; unique per-session ownership pattern.

**No significant duplication**, but three observations:
1. PriceDrop's multi-turn session ownership is unique and untested for long-lived idle use (LocalAskSession:17-29).
2. Each app's downscaling logic for images is duplicated (VisionAnalysisService.encodeImageToBase64 ~ LocalVisionService.downscaleForLocalInference); both pre-check dimensions and only downscale if needed.
3. Error recovery when local models crash/timeout differs: Scrybe retries in SessionTransformCoordinator, Shelf Snap and PriceDrop fall back to cloud (if available) or surface error directly.

## 2. Cloud AI Integration

**HTTP client patterns are consistent across all three apps** but each builds its own wrappers:

- **Scrybe**: OpenAiTranscriptionProvider (`apps/scrybe/core/transcription/src/main/kotlin/dev/scrybe/core/transcription/OpenAiTranscriptionProvider.kt:25-104`):
  - Audio chunking in OpenAiAudioChunker; retries failed chunks (configurable MAX_CHUNK_ATTEMPTS with exponential backoff).
  - Timeout config: 30s connect, 60s read (hardcoded in OkHttpClient).
  - Error handling: ChunkApiException with isRetriable flag; caught and retried; failures always logged to DebugLog.
  - Also handles gpt-4o instructions via `prompt` field to preserve spoken languages (line 50-51).

- **Shelf Snap**: VisionAnalysisService (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/remote/VisionAnalysisService.kt:33-403`) and ListingGenerationService (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/remote/ListingGenerationService.kt:25-100`):
  - VisionAnalysisService: Timeout config: 30s connect, 60s read. Routes to Responses API (gpt-5-family) or Chat Completions (older). Image downscaling cap: 2048px (matches OpenAI's "high" detail preprocessing).
  - ListingGenerationService: Timeout config: 20s connect, 40s read (different from vision). Falls back to existing copy on any error (line 77).
  - Both add X-TwoBits-App/X-TwoBits-Op headers for routing/tracing.

- **PriceDrop**: PriceDropApiClient (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/remote/PriceDropApiClient.kt:40-868`):
  - Centralized post() method (line 794-847) logs all calls to DebugLogStore.
  - Timeout config: uses OkHttpClient builder defaults (not overridden per-op).
  - Error handling: friendlyError() maps HTTP codes to user-facing messages (401 invalid key, 429 rate limit, 500+ unavailable).
  - Supports both Pro (Worker proxy at api.twobits.app) and BYOK (direct OpenAI) routing based on ProviderMode.

**Retry patterns diverge**:
- Scrybe: retries at chunk level only; gives up after MAX_CHUNK_ATTEMPTS.
- Shelf Snap: no built-in retry; relies on error suppression (returns existing data on failure).
- PriceDrop: no retry logic; all errors bubble up to caller.

**Timeout inconsistencies**:
- Scrybe transcription: 30/60s (connect/read)
- Shelf Snap vision: 30/60s (connect/read)
- Shelf Snap listing: 20/40s (connect/read) — tighter than vision
- PriceDrop: not overridden; uses OkHttpClient defaults

This variation suggests per-app tuning rather than shared patterns, creating risk if a timeout value needs adjustment (would require changes in three places).

## 3. Provider Abstraction

**Shared abstraction exists but only covers transcription**:

- **ProviderType** (`shared/api-keys/src/main/kotlin/com/twobits/apikeys/ProviderType.kt:3-7`): Three values: OPENAI, LOCAL, MANAGED_PRO. Used by Scrybe's TranscriptionProvider interface to select which implementation to use.
- **Scrybe's model**: TranscriptionProvider interface (apps/scrybe/core/transcription/src/main/kotlin/dev/scrybe/core/transcription/TranscriptionProvider.kt:6-13) — implementations: OpenAiTranscriptionProvider (OPENAI), WhisperTranscriptionProvider (LOCAL), no MANAGED_PRO yet.

**PriceDrop reinvents the pattern locally** with richer semantics:

- **ProviderMode** (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/PriceDropProvider.kt:138-150`): Four values: OFF, BYOK, PRO, LOCAL. More expressive than ProviderType.
- **PriceDropProvider** (line 10-141): Enum of all providers (OPENAI, WEB_SEARCH, SHOPPING, SERPER, RAINFOREST, FIRECRAWL), each with byokBaseUrl, display names, signup hints, cost estimates, and capabilities.
- **AiFeature** (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/AiFeature.kt:33-104`): Feature-oriented abstraction (SEARCH, PRICE_CHECK, ASK, DROPS, COUPON), each listing its providers and model options. Decouples "which feature" from "which provider."
- **ProviderSettingsStore**: Central access point for mode (getMode), key (getKey), and feature-specific config.

**No shared abstraction for vision/chat beyond specific classes**. Shelf Snap's VisionAnalysisService routes through the TwoBits Worker (Pro) or OpenAI directly (BYOK), but the router is hardcoded in the method signature (baseUrl/authHeader parameters, line 80-84), not a pluggable enum.

**Risk**: Three different "provider abstraction" patterns:
1. Scrybe: ProviderType + TranscriptionProvider interface.
2. PriceDrop: ProviderMode + PriceDropProvider + AiFeature.
3. Shelf Snap: Hardcoded baseUrl/authHeader routing in method signatures.

None of these patterns are compatible or shareable. A new AI feature in any app must reimplement the pattern locally.

## 4. Model Management

**Shared text-LLM model catalog** (excellent shared abstraction):

- **LocalLlmModel** (`shared/local-models/src/main/kotlin/com/twobits/core/localmodels/LocalLlmModel.kt:51-160`): Centralized enum of 5 text models (Gemma 4 E2B/E4B, Qwen 3 0.6B/1.7B, SmolLM2 360M), each with display name, download URL, HF page, size label, SHA-256, and max context tokens. Vision capability flag gates which models Shelf Snap offers (only Gemma 4 E2B/E4B marked visionCapable=true).
- **LocalModelSpec** (`shared/local-models/src/main/kotlin/com/twobits/core/localmodels/LocalModelSpec.kt`): Trait shared by LocalLlmModel and LocalWhisperModel for consistent handling.
- **TwoBitsLocalModels** (`shared/local-models/src/main/kotlin/com/twobits/core/localmodels/TwoBitsLocalModels.kt:12-24`): Query helpers (specsFor, specsForTask).

**Per-app model managers** (duplicated pattern):

- **Scrybe** LocalModelManager (`apps/scrybe/core/local-ai/src/main/kotlin/dev/scrybe/core/localai/LocalModelManager.kt`): Combines LocalLlmModel + LocalWhisperModel into a single catalog exposed to the app's UI.
- **Shelf Snap** LocalModelManager (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/local/LocalModelManager.kt`): Exposes LocalLlmModel only (no speech-to-text).
- **PriceDrop** LocalModelManager (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/local/LocalModelManager.kt`): Exposes LocalLlmModel only.

**Download workers** (identical pattern, three times):

- Scrybe: ModelDownloadWorker (`apps/scrybe/core/local-ai/src/main/kotlin/dev/scrybe/core/localai/ModelDownloadWorker.kt`)
- Shelf Snap: ModelDownloadWorker (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/work/ModelDownloadWorker.kt`)
- PriceDrop: ModelDownloadWorker (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/work/ModelDownloadWorker.kt`)

All three inherit from CoroutineWorker, use ModelDownloader from shared/local-ai, and follow the same download→verify→store pattern. **This is a clear candidate for consolidation into a shared module**, but each app's current LocalModelManager integration would need refactoring.

**Download infrastructure** (shared but minimal):

- **ModelDownloader** (`shared/local-ai/src/main/kotlin/com/twobits/localai/ModelDownloader.kt`): Handles HTTP download, SHA-256 verification, and disk storage. Used by all three apps' ModelDownloadWorker.
- **LlmModelDownloadCoordinator** (`shared/local-ai/src/main/kotlin/com/twobits/localai/LlmModelDownloadCoordinator.kt`): Coordinates concurrent downloads; no parallelization across models (enqueues them serially into a WorkManager queue to avoid overwhelming the device).

**Risk**: LocalModelManager instantiation and model selection UI varies per app; migrating to a true shared model manager would require untangling app-specific catalog composition (Scrybe adds Whisper, Shelf Snap filters to vision-capable, etc.).

## 5. Cost Controls

**Scrybe**: CallBudgetCard in AIConfigScreen (`apps/scrybe/feature/settings/src/main/kotlin/dev/scrybe/feature/settings/AIConfigScreen.kt`):
- UI component displays weighted cost estimates for each feature (Transcription, Speakers, Insights, Transforms).
- Weights are hardcoded in the screen (1-2 per feature); no centralized cost database.
- Also shows estimated cost per session in SessionDetailScreen (estimatedTranscriptionCostUsd).

**PriceDrop**: AiFeature with callEstimate + callWeight (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/AiFeature.kt:33-104`):
- Each AiFeature (SEARCH, PRICE_CHECK, ASK, DROPS, COUPON) has callEstimate string (human-readable) and callWeight integer (for budget UI).
- callEstimate examples: "1 call per enabled provider · 2 calls for URL paste" (SEARCH), "1 call per check" (PRICE_CHECK).
- Centralized in the enum; visible in both AI Config screen and potentially future budget tracking.

**Shelf Snap**: No cost estimation visible. VisionAnalysisService and ListingGenerationService make blind OpenAI calls; no warning or budget display.

**Consistency issue**: Three different approaches (Scrybe has CallBudgetCard UI with weights, PriceDrop has feature-level estimates, Shelf Snap has none). Only PriceDrop's per-call descriptions are maintainable as API pricing changes; Scrybe's fixed weights and Shelf Snap's silence will become outdated.

## 6. Error Handling & Fallback

**Cloud AI failures**:

- **Scrybe transcription**: OpenAiTranscriptionProvider retries ChunkApiException if isRetriable flag is set (likely HTTP 5xx, 429, or timeouts); after MAX_CHUNK_ATTEMPTS, throws and bubbles to the UI caller.
- **Shelf Snap vision**: VisionAnalysisService catches any IOException and maps HTTP codes to friendlyError strings (401→"invalid key", 429→"rate limited", 500s→"unavailable"). Returns DraftItemResult with error field populated; UI shows error banner without crashing.
- **Shelf Snap listing**: ListingGenerationService returns existing ListingCopy unchanged on any error (line 90-92); silent fallback preserves data.
- **PriceDrop chat**: PriceDropApiClient.chat() throws IOException on failure (line 114-115 guard against invalid feature state); caller (AskViewModel) catches and displays error to user.

**Local inference failures**:

- **Scrybe**: WhisperEngine timeout (180s per chunk) throws LocalTranscriptionTimeoutException; WhisperTranscriptionProvider logs and surfaces. AudioDecoder timeout (180s) throws AudioDecodeTimeoutException. SessionTransformCoordinator catches and retries on some failures.
- **Shelf Snap LocalVisionService**: Catches all Throwables and falls back to cloud (line 142-163). Records failure to DebugLog with exception class and stackTrace. Returns DraftItemResult with error message: "On-device vision analysis failed. Try Pro or BYOK instead."
- **PriceDrop LocalAskSession**: Catches and throws (line 112-127); caller (AskViewModel) handles fallback to cloud if local is configured but fails.

**Memory/resource failures**:

- **LocalInferenceMemoryGuard.requireHeadroom()**: Throws InsufficientMemoryException (caught upstream as RuntimeException) with human-readable message: "Not enough free memory to run {model}... Close other apps or pick a smaller model."
- **LocalInferenceGate**: After 180s wait, throws LocalEngineBusyException: "Another on-device task has held the model for over {minutes} minutes. Reopen the app and try again."
- Both are surfaced as in-app error banners; app does not crash.

**Consistency issue**: Error messages are app-specific and hardcoded per service. Three different approaches to the same underlying problem:
1. Scrybe: Retries at request level; throws on exhaustion.
2. Shelf Snap: Silent fallback (vision) or error banner (listing).
3. PriceDrop: Throws and relies on caller's UI layer to catch.

No shared error recovery strategy or fallback orchestration across providers.

## 7. Prompt/System-Instruction Management

**System prompts are hardcoded inline per service**; no central prompt registry:

- **Shelf Snap VisionAnalysisService**: SYSTEM_PROMPT (line 318-334) — JSON schema instruction for item analysis. USER_PROMPT (line 336-337) — "Please analyse...". Both static const.
- **Shelf Snap ListingGenerationService**: buildListingSystemPrompt(platform) constructs per-platform copy instructions inline (likely in unshown buildListingSystemPrompt, since file limit cuts off).
- **PriceDrop AskViewModel**: SYSTEM_PROMPT (constant, not shown in snippet but called at line 109) — shopping assistant system message.
- **Scrybe**: Multiple transforms (OpenAiAutoRenameService, DiarizationServiceFacade, etc.) each have inline prompts passed to LiteRtLmEngine.acquire() or the cloud provider.

**User input handling**:

- **Shelf Snap vision**: Photos are base64-encoded; no text user input besides the system prompt's fixed prompt field. Schema is JSON; LLM output is parsed, not re-prompted.
- **PriceDrop Ask**: User messages are part of the history array (line 163-178 in chat()). System prompt is prepended, but user text is never interpolated into it — it stays in the message array. Safe.
- **Scrybe transforms**: System prompts are hardcoded; user input (session text) is passed as a separate message, not interpolated. Safe.
- **Shelf Snap listing**: User message is constructed from item data (brand, category, current copy). No direct user text interpolated into prompts. Safe.

**Risk assessment**: No prompt injection vulnerabilities spotted. All three apps keep user input in message arrays or base64 blobs, not in prompt string interpolation. However, centralization would make prompt updates and A/B testing easier. Currently, changing "the system prompt for vision analysis" requires changes in both VisionAnalysisService and LocalVisionService (redundant in codebases).

**Suggestion**: Prompts should be moved to a shared `shared/ai-prompts/` module with versioned instruction sets, accessible via enum (e.g., AiPrompt.VISION_ANALYSIS, AiPrompt.SHOPPING_ASSIST) to avoid drift between apps and enable unified testing/iteration.

## 8. Opportunities

**High-confidence consolidation candidates**:

1. **ModelDownloadWorker pattern** (3 copies, nearly identical):
   - Scrybe: `apps/scrybe/core/local-ai/src/main/kotlin/dev/scrybe/core/localai/ModelDownloadWorker.kt`
   - Shelf Snap: `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/work/ModelDownloadWorker.kt`
   - PriceDrop: `apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/work/ModelDownloadWorker.kt`
   - **Action**: Move to `shared/local-ai/src/main/kotlin/com/twobits/localai/SharedModelDownloadWorker.kt`, expose a factory function that takes a LocalModelSpec list. Each app passes its catalog (LocalLlmModel + LocalWhisperModel for Scrybe, LocalLlmModel for others) and reuses.

2. **System prompt consolidation** (duplicated across services):
   - VisionAnalysisService.SYSTEM_PROMPT ≈ LocalVisionService uses same via reference (good), but hardcoded inline.
   - PriceDrop Ask SYSTEM_PROMPT, Scrybe transform prompts all scattered.
   - **Action**: Create `shared/ai-prompts/` (or add to existing shared module) with:
     ```kotlin
     enum class AiPrompt(val text: String, val version: String) {
         VISION_ANALYSIS("...", "1.0"),
         SHOPPING_LISTING("...", "1.0"),
         ASK_SHOPPING_ASSISTANT("...", "1.0"),
         // etc.
     }
     ```
   - Update all callers to reference AiPrompt.VISION_ANALYSIS.text instead of inline constants. Enables versioning, A/B testing, and centralized updates.

3. **HTTP client timeout configuration**:
   - Currently scattered: Scrybe 30/60s, Shelf Snap 30/60s (vision) vs 20/40s (listing), PriceDrop uses defaults.
   - **Action**: Create a shared HttpConfig data class with named presets (transcription, vision, websearch, etc.), passed to OkHttpClient builders. Single source of truth for timeouts.

4. **Provider abstraction unification**:
   - Scrybe uses ProviderType + TranscriptionProvider interface (works well for one feature).
   - PriceDrop uses ProviderMode + PriceDropProvider + AiFeature (rich but app-specific).
   - Shelf Snap hardcodes baseUrl/authHeader in method signatures.
   - **Action**: Create `shared/ai-provider/` with a generalized abstraction:
     ```kotlin
     interface AiProvider {
         val key: String
         val displayName: String
         val byokBaseUrl: String
         suspend fun <T> call(endpoint: String, body: Any, responseType: Class<T>): T
     }
     enum class AiFeatureRouting { OFF, LOCAL, BYOK, PRO }
     ```
   - Allows each app to plug in its own PriceDropProvider enum while sharing the routing logic.

5. **Image downscaling logic duplication**:
   - VisionAnalysisService.encodeImageToBase64 (cap: 2048px, iterative power-of-2 downsampling).
   - LocalVisionService.downscaleForLocalInference (cap: 1024px, same algorithm).
   - **Action**: Extract to `shared/image-processing/ImageScaler.kt` with configurable cap. Both services call ImageScaler.downscaleIfNeeded(file, maxDim=2048).

6. **Error handling/fallback orchestration**:
   - Currently each service handles its own errors (retry, silence, throw).
   - No coordination when local fails (should fall back to cloud), cloud fails (should offer retry).
   - **Action**: Create `shared/ai-fault-tolerance/` with:
     ```kotlin
     interface AiFallback {
         suspend fun <T> withFallback(primary: suspend () -> T, secondary: suspend () -> T?): T
     }
     ```
   - Allows each feature to chain primary (local) → secondary (cloud) without reimplementing retry/fallback logic.

**Medium-confidence optimizations**:

7. **DebugLog instrumentation standardization**: All apps log AI calls (DebugLogEntry with op, endpoint, success, durationMs, responseSnippet) but inconsistently. Standardize field names and ensure all call sites follow the pattern.

8. **Model manager abstraction**: All three apps' LocalModelManagers are identical. Move composition logic (combining text + vision models, filtering by task) to shared/local-ai and expose a builder API each app configures.

## Top 5 Priorities

1. **Create `shared/ai-prompts/` module** (effort: 1 day; impact: high)
   - Centralize all SYSTEM_PROMPT constants from VisionAnalysisService, PriceDrop Ask, Scrybe transforms into versioned enum.
   - Updates to prompt copy will no longer require changes in multiple services; enables safe A/B testing.
   - File: new `shared/ai-prompts/src/main/kotlin/com/twobits/aiprompts/AiPrompt.kt` + update all call sites (6-8 files).

2. **Consolidate ModelDownloadWorker** (effort: 2 days; impact: medium)
   - Move Scrybe/Shelf Snap/PriceDrop's ModelDownloadWorker implementations to `shared/local-ai/` as SharedModelDownloadWorker.
   - Expose factory that each app configures with its own model catalog.
   - Eliminates 3× boilerplate; future model management changes only need changes in one place.
   - Files: new `shared/local-ai/.../SharedModelDownloadWorker.kt`, delete from each app's `work/` dir, update DI configs (3 files).

3. **Unify HTTP timeout configuration** (effort: 1 day; impact: medium)
   - Create `shared/network/HttpConfig.kt` with named presets (TRANSCRIPTION, VISION_ANALYSIS, WEB_SEARCH).
   - Pass to OkHttpClient builders in all three apps instead of hardcoding per-service timeouts.
   - Single audit point if timeout tuning is needed; currently requires grep + 3 file edits.
   - Files: new `shared/network/src/main/kotlin/.../HttpConfig.kt`, update OkHttpClient setup in 3 apps.

4. **Design unified provider/feature abstraction** (effort: 3-4 days; impact: high, enables future work)
   - Do NOT merge PriceDrop's ProviderMode into Scrybe/Shelf Snap yet (too disruptive).
   - Instead: Create `shared/ai-provider/` with minimal interface (key, displayName, byokBaseUrl, routing logic).
   - Each app's ProviderType/PriceDropProvider can continue to exist, but all routing through shared abstraction.
   - Enables Shelf Snap to adopt PriceDrop's cost estimation UI and fallback patterns without rewriting.
   - Files: new `shared/ai-provider/src/main/kotlin/.../AiProvider*.kt`, update routing in 3 apps (6-8 files).

5. **Add image downscaling to shared utilities** (effort: half day; impact: low/medium)
   - Extract VisionAnalysisService.encodeImageToBase64's downsampling into `shared/image-processing/ImageScaler.kt`.
   - Both VisionAnalysisService (2048px cap) and LocalVisionService (1024px cap) call the same logic with different caps.
   - Future vision models added to any app reuse the same proven logic.
   - Files: new `shared/image-processing/.../ImageScaler.kt`, update 2 existing services.
