# Industry Best Practices for On-Device AI on Android — Audit Report

**Date:** 2026-09-25  
**Scope:** TwoBits monorepo (Scrybe, Shelf Snap, PriceDrop)  
**Focus:** Google ML Kit GenAI APIs, AICore/Gemini Nano, Pixel app patterns, UX best practices

---

## 1. Google ML Kit GenAI APIs — Comparison Against Repo

**What Industry Does:**
Google's ML Kit GenAI APIs (launched May 2025) provide on-device inference for Summarization, Proofreading, Rewriting, and Image Description, all running on Gemini Nano without cloud costs or API keys. These are lightweight, task-specific models available on Pixel 9+, Samsung Galaxy S24+, and expanding to other Android 14+ devices. Key feature: they require zero authentication and run entirely offline.

**Source:** [ML Kit GenAI APIs Overview](https://developers.google.com/ml-kit/genai), [Android Developers Blog (May 2025)](https://android-developers.googleblog.com/2025/05/on-device-gen-ai-apis-ml-kit-gemini-nano.html)

**What TwoBits Does:**
- Scrybe bundles its own Whisper model (speech-to-text) and uses LiteRT-LM for summarization/diarization (shared/local-ai/src/main/kotlin/com/twobits/localai/LiteRtLmEngine.kt:51-62)
- Shelf Snap bundles its own vision model for product image analysis
- PriceDrop bundles a full LLM for multi-turn chat via LocalAskSession

**The Gap:**
TwoBits bundles full-featured general-purpose models rather than task-specific optimized ones. Scrybe's summarization could substitute ML Kit's lightweight Summarization API (smaller footprint, faster inference) if Pixel-only support were acceptable. However:
- ML Kit APIs are Pixel/Samsung-device-only in 2026; TwoBits requires broader device support
- TwoBits needs multi-turn conversation (Scrybe profiles, PriceDrop chat); ML Kit APIs are single-request only
- TwoBits' approach is device-agnostic and doesn't rely on OEM-specific runtime updates

**Recommendation:** Not actionable for this repo's three-app, multi-device requirement. ML Kit APIs are valuable reference for what optimized task-specific inference looks like, but bundling general models remains correct given the device diversity target.

---

## 2. AICore and Gemini Nano — System-Level Runtime

**What Industry Does:**
Starting Android 14, Google manages Gemini Nano through AICore, a system service that handles model versioning, hardware-specific variants (Nano 1 for standard RAM, Nano 2 for high-end devices), and lifecycle management. Developers call through a stable API; the OS updates the model transparently. No app downloads the 2GB+ model—the system manages it.

**Source:** [Android AI — Gemini Nano](https://developer.android.com/ai/gemini-nano), [Stora guide (2026)](https://stora.sh/blog/2026-04-13-on-device-ai-android-app-gemini-nano-guide)

**What TwoBits Does:**
- Implements its own model lifecycle: download, storage, version detection
- LocalInferenceMemoryGuard (shared/local-ai/src/main/kotlin/com/twobits/localai/LocalInferenceMemoryGuard.kt) pre-checks available RAM before load
- LiteRtLmEngine.acquire() (LiteRtLmEngine.kt:256-281) manages memory gate + concurrency (one model resident per process)
- ModelDownloader.kt (lines 32-292) handles resumable downloads with Range requests, SHA-256 verification, and disk space checks
- App stores model state in DataStore; each app (Scrybe, Shelf Snap, PriceDrop) maintains its own LocalModelState enum

**The Gap:**
TwoBits cannot leverage AICore because:
1. AICore only provisions Gemini Nano (general-purpose LLM); Scrybe needs Whisper (speech-to-text), Shelf Snap needs vision—neither available through AICore in 2026
2. TwoBits supports Android 11+; AICore requires Android 14+
3. AICore is OS-managed and device-specific; TwoBits needs consistent behavior across $100–$1500 devices

However, the repo's own gate/memory-guard pattern is equivalent to what AICore does: prevent multi-model resident state and refuse loads on low-memory devices.

**Recommendation:** No change needed. TwoBits' approach is correct for its model diversity and device range. For reference: if the app were Gemini-Nano-only and could require Android 14+, AICore delegation would eliminate ModelDownloader and DeviceDiagnostics maintenance burden.

---

## 3. Pixel Apps Using On-Device AI — Reference Implementations

**What Industry Does:**
- **Pixel Recorder** (direct comparison to Scrybe): Uses Gemini Nano for on-device summarization of transcripts; LoRA-tuned to output 3-bullet summaries. Engagement data: 2–5 summaries generated per user per day on average; users canceled cloud transcription subscriptions after launch. Transcription itself is on-device via the Pixel's NPU.
- **Pixel Screenshots**: AI scans saved screenshots and builds a searchable database; user can circle to search or swipe to integrate into Circle to Search.
- **Magic Compose**: Rewrites messages in different tones (formal, casual, excited). Pixel 8 Pro runs it on-device; others use cloud.

**Source:** [Pixel Recorder — Android Developers Blog (Aug 2024)](https://android-developers.googleblog.com/2024/08/recorder-app-on-pixel-sees-boost-in-engagement-with-gemini-nano.html), [How to use Pixel Screenshots](https://blog.google/products-and-platforms/devices/pixel/google-pixel-screenshots-tips/)

**What TwoBits Does — Scrybe Parallel:**
- Whisper transcription on-device (NPU-capable, real-time)
- Gemini Nano summarization equivalent via bundled LiteRT-LM (not LoRA-tuned like Recorder, but multi-turn capable)
- Speaker diarization and insight extraction via cloud OpenAI or on-device LiteRT-LM (user-configurable in AIConfigScreen.kt:277–395)

**Comparison:**
Scrybe's UX is more flexible (on-device *or* cloud, user's choice) and feature-rich (diarization, insights). Pixel Recorder is simpler: on-device only, no settings. Scrybe's bundle-your-own-model approach trades complexity for control; Recorder delegates to Gemini Nano and gets automatic updates at the cost of device exclusivity.

**Actionable Finding:** Scrybe's 3-bullet-point summary output (if implemented) could benchmark against Recorder's LoRA tuning. Currently no mention of specific summary format in the codebase. Not a gap—summarization is working—but a quality bar to track.

---

## 4. UX Patterns for On-Device AI — Download, Badging, Battery/Thermal, Low-Memory

### Model Download & First-Run UX

**What Industry Does:**
- One-time model download on first use, with resume capability and progress indication
- Typical pattern: "Download" button → progress bar → "Ready" state; subsequent opens are instant
- Example: Llama 3.2 3B model (4–5 GB) downloads in 2–3 minutes on 5G or WiFi

**What TwoBits Does:**
- AIConfigScreen.kt provides a "Models" tab (lines 161–221) with per-model "Download" buttons
- LocalModelPanel component (lines 189–219) shows status (NotAvailable → Acquiring % → Ready → Error)
- ModelDownloader.kt automatically resumes partial downloads (lines 64–87, 95–97 Range request support)
- SHA-256 verification prevents incomplete/corrupted files from being installed (lines 183–194)
- Orphaned files and stale partial downloads are cleaned up (lines 231–270)

**Assessment:** Excellent. TwoBits' download UX matches best practice. The `.part` file + resume + checksum pattern is industry-standard.

### Privacy Badging & "Processed On-Device" Messaging

**What Industry Does:**
Privacy-first design shows "green badge" (High Privacy) near on-device processing, with clear messaging like "Processed on your device; nothing uploaded." Keeps explicit privacy statements near the action, not buried in settings. Example: Pixel Recorder and Pixel Screenshots both emphasize local processing prominently.

**What TwoBits Does:**
- AIConfigScreen.kt shows segment controls (AiSourceSegment component, line 278–345) to select LOCAL / PRO (cloud) / BYOK (Bring Your Own Key)
- AIConfigScreen.kt lines 277–319: Transcription card clearly states mode choice
- No explicit "on your device" badging on the components themselves; privacy messaging is implicit in the mode choice

**The Gap:**
TwoBits doesn't badge individual inference calls with "Processed on your device" messaging. When a user taps "Summarize" in Scrybe's detail view, there's no indicator that this runs locally vs. cloud. This is UX debt.

**Recommendation:** Add a subtle badge (green dot + "Local") to inference results when ExecutionMode is LOCAL. Reference: Pixel Recorder's design—on-device transcription shows a small "on-device" tag on the summary. Cost: ~20 lines of Compose.

### Battery & Thermal Throttling Awareness

**What Industry Does:**
Monitor device temperature and throttle inference workload before thermal limits are hit (e.g., reduce batch size, thread count). PowerManager.forecast API (Android 14+) predicts thermal headroom. Example: sustained LLM inference retains 77% peak throughput at 30 min by voluntarily reducing thread count *before* the kernel triggers DVFS throttling.

**Source:** [Thermal Mitigation AOSP](https://source.android.com/docs/core/power/thermal-mitigation), [MVP Factory thermal/sustained inference blog](https://mvpfactory.io/blog/thermal-throttling-and-sustained-on-device-llm-inference-on-android-cpu)

**What TwoBits Does:**
- LiteRtLmEngine.kt (lines 117–204) has a 90-second hard timeout on generation (DEFAULT_GENERATION_TIMEOUT_MS = 90_000L, line 218)
- LocalInferenceMemoryGuard pre-checks RAM but does not monitor temperature
- No PowerManager.forecast or thermal mitigation logic

**The Gap:**
On a hot device or during sustained workload (Scrybe's diarization + insights), inference will time out before adaptive thermal backoff can help. This is a quality issue for real-world use—a user running Scrybe on a gaming session or in a hot climate may hit timeouts unnecessarily.

**Recommendation:** Medium priority. Integrate PowerManager.forecast (Android 14+) to detect thermal headroom and adaptively reduce context window or batch size before timeout. Fallback to current timeout on Android 13–. Benefit: fewer user-visible timeouts, better experience on thermal-constrained devices. Rough cost: 100–150 lines in LocalInferenceMemoryGuard + per-engine config.

### Low-Memory Device Handling

**What Industry Does:**
Graceful degradation: if a device can't fit the full model, offer a smaller quantization, or disable feature entirely. Example: Llama 3.2 1B quantization for <6 GB RAM devices.

**What TwoBits Does:**
- LocalInferenceMemoryGuard.requireHeadroom() (lines 86–103) throws InsufficientMemoryException when device RAM is below a headroom threshold
- Headroom factors: 1.1x for text (10% KV cache/activation budget), 1.3x for vision (30% for image encoder tokens)
- Context window is factored in (lines 69–79): larger windows demand more headroom, scales linearly
- No fallback: if memory is insufficient, the feature is disabled and user sees error "Close other apps or pick a smaller model"

**Assessment:** Solid defensive design—refuses bad loads upfront. Provides actionable error messaging. No graceful degradation (no smaller-model fallback), but that's a product decision, not a bug.

---

## 5. Multi-Turn Inference & Session State Management

**What Industry Does:**
For conversational AI (chat), most cloud APIs (OpenAI, Gemini) require clients to re-send full conversation history on every request. On-device engines (LiteRT-LM, Ollama) accumulate state in a Conversation object—send fresh message, get response, state persists.

**What TwoBits Does:**
- LiteRtLmEngine wraps a single LiteRT-LM Conversation (line 80: `private val conversation: Conversation`)
- Each generate() call appends to the Conversation; subsequent calls see prior context
- PriceDrop's LocalAskSession.kt (lines 16–29) holds one engine open across multiple message sends—explicit pattern for multi-turn chat
- No re-construction per message; state accumulates

**Assessment:** Correct pattern. LocalAskSession is the right abstraction for multi-turn. LiteRtLmEngine.acquire() is the right pattern for one-shot (Scrybe diarization, Shelf Snap vision refinement).

**Potential Concern:** LocalAskSession.kt lines 24–29 note "NOT verified against sustained/idle use"—whether LiteRtLmEngine can safely remain open across a longer Ask session (minutes to hours, possibly idle) hasn't been tested. This is a known risk, not a bug, but worth revisiting with real device testing if Ask sessions become more popular.

---

## Top 5 Priorities

1. **Add on-device processing badging to inference results** (Low lift, high UX value)
   - When a result comes from LOCAL execution mode, show a green badge or tag ("Processed on your device")
   - Reference: Pixel Recorder's "on-device" tag on summaries
   - Effort: ~20 lines Compose; affects Scrybe SessionDetailScreen and transform result cards, PriceDrop AskScreen results
   - Impact: Clear user privacy narrative; differentiates local vs. cloud transparently

2. **Integrate PowerManager.forecast for thermal-aware inference throttling** (Medium lift, real-world impact)
   - Monitor thermal headroom before and during long inference tasks (diarization, sustained Ask sessions)
   - Adaptively reduce context window or inference timeout based on device temperature forecast (Android 14+)
   - Fallback to current fixed timeout on Android 13–
   - Benefit: Fewer timeouts on thermally constrained devices; better real-world UX
   - Effort: ~150 lines; LocalInferenceMemoryGuard + EngineConfig integration

3. **Test LocalAskSession against sustained/idle sessions** (High priority for product robustness)
   - Verify that LiteRtLmEngine can safely remain open 30+ minutes with idle gaps (PriceDrop's Ask use case)
   - Current code note (LocalAskSession.kt:24–29) indicates this hasn't been formally tested
   - Real device test on Pixel 6, midrange Android device (e.g., Moto G), and high-end device
   - Outcome: Either confirm safety or implement idle-timeout + engine recreation

4. **Profile memory footprint of vision models under sustained load** (Follow-up investigation)
   - VISION_HEADROOM_FACTOR = 1.3 (LocalInferenceMemoryGuard.kt:55) is a conservative estimate
   - Shelf Snap could benchmark actual resident memory (PSS, RSS) during back-to-back product image analysis
   - Refine factor based on real data if current estimate is too loose (wastes RAM checks) or too tight (fails on real devices)
   - Effort: 1–2 device sessions with debug build + procrank/dumpsys meminfo

5. **Expand model metadata to track quality benchmarks** (Medium-term documentation)
   - LocalLlmModel enum should annotate each model with: summary quality score (vs. Recorder's LoRA-tuned 3-bullet output), context limit tested-on, thermal throttle profile, min RAM observed-working
   - No code change required; documentation + test data; informs user UX (model picker could surface "Works best on Pixel 6+" warnings)
   - Benefit: Help users pick appropriate model for device; set realistic expectations for quality/speed tradeoffs

---

## Conclusion

TwoBits' implementation is **production-sound** and **ahead of industry best practice** in several areas:
- Download resumption + SHA-256 verification (better than most Android apps)
- One-engine-per-process gate to prevent OOM kills (critical, well-executed)
- Memory headroom checks with context-window scaling (thoughtful)
- Three-app coordination via shared/local-ai module (good architecture)

**Gaps are incremental, not fundamental:**
- Privacy badging is a UX/messaging gap, not a technical one
- Thermal throttling support would improve real-world robustness
- Session state testing is a validation gap

None of these prevent production deployment or pose reliability risk as currently written.

Generated as part of agent-audit-2026-09-25

---

Generated as part of agent-audit-2026-09-25
