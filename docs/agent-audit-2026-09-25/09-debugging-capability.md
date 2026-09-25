# Debugging & Observability Capability Audit — TwoBits Monorepo

**Date:** 2026-09-25  
**Scope:** Shared debug-log system (shared/debug-log, shared/debug-log-ui) and logging across three Android apps (Scrybe, Shelf Snap, PriceDrop)

---

## 1. DebugLogStore & DebugLogEntry Architecture

### DebugLogEntry Structure
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:49–74`

Three entry types:
- `CRASH` — uncaught exceptions + process exits (low-memory, ANR, native crash, signals)
- `AI_CALL` — local/cloud inference, transcription, model downloads
- `SERVICE_CALL` — web search, page reads, market research, etc.

Each entry captures: timestamp, type, op (operation name), endpoint, model, requestSummary, success flag, HTTP status (if applicable), responseSnippet, duration, and for crashes: thread name, exception type, message, and full stack trace.

### Persistence & Size Management
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:514–614`

- **Storage:** JSON file (`debug_log.json`) in app's private files directory
- **Rotation:** Rolling window of **150 entries maximum** (line 606)
- **Byte budget:** **1 MB max file size** (line 613); when exceeded, oldest entries are dropped first while keeping the newest entry
- **Per-entry trace cap:** Native crash traces capped at **16 KB** (line 612)
- **Read-modify-write strategy:** On every write, the entire file is decoded, entry appended, trimmed, re-encoded, written to temp file, then atomically renamed over original (lines 574–580)

### Cross-Process Safety
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:538–567`

- **Multi-thread guard:** `synchronized(lock)` on a monitor object
- **Cross-process guard:** File-based `FileLock` acquired on `debug_log.lock`
- **Atomic writes:** Temp-file-rename pattern prevents torn writes (a process death mid-write leaves old file intact)
- **Bug history:** Commit 82d54f8 fixed a double-write bug where exceptions in the block were re-executing the block; acquisition now returns null/Closeable, separating it from the caller's block invocation

**Status:** Sound. Fixed hazards around concurrent writes and partial/corrupted state from process death mid-write.

### Crash Detection & Annotation
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:237–334`

- **Unmatched `-start` marker detection** (lines 128–131): App launch loads the log, finds any dangling start marker (entry with `startMarker=true` and no matching completion), surfaces it as a crash warning to user
- **Process exit reason capture** (lines 263–302): On API 30+, reads `ApplicationExitInfo.getHistoricalProcessExitReasons()` once per launch; records the reason (CRASH_NATIVE, CRASH, ANR, LOW_MEMORY, SIGNALED, etc.) plus optional native tombstone/trace data
- **Crash memory** (lines 433–471): Remembers which model/op crashed (stored in SharedPreferences), annotates the next attempt with "this model ended the process on a previous run", clears memory on success
- **Stale marker handling** (line 142, 220–234): When user dismisses crash warning, a completion entry is written with `success=false` to prevent re-detection on future launches

**Status:** Sophisticated. Detects both Kotlin crashes (via uncaught exception handler) and native crashes (via OS exit info). Pairs stage markers (`-start`, `-engine-loaded`) across three-stage operations to pinpoint which stage crashed.

---

## 2. DebugLogUI: User-Facing Capabilities

### Screen & Filtering
**Files:**
- `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogScreen.kt:60–185`
- `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogViewModel.kt`

Users can:
1. **View** all entries as a reverse-chronological list (newest first)
2. **Filter** by type: All / Crashes / AI calls / Services
3. **Share** the entire log as plain text (via Intent.ACTION_SEND) for remote diagnosis
4. **Clear** all entries (with confirmation dialog)
5. **Sort implicitly** by timestamp (newest first in UI)

No text search, no op-level filtering, no export-to-file—only share-via-text.

### Entry Card Layout
**File:** `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogScreen.kt:188–272`

**Crash cards** show:
- Exception type (short name, e.g., "NullPointerException" from "java.lang.NullPointerException")
- Timestamp + thread name
- Message (if present)
- Full stack trace

**AI/Service call cards** show:
- Op name + status badge (STARTED / OK / FAILED) + HTTP status if applicable
- Timestamp + endpoint + model name + duration
- Request summary (e.g., "file=recording.wav")
- Response snippet (e.g., "1200 chars" or error message)
- Stack trace (if failure)

### Export/Share
**File:** `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogScreen.kt:286–327`

Share text includes:
- **Header:** Device fingerprint (OS, API, model, free RAM, etc.) recorded once per launch
- **Previous exit reason:** "Previous run ended: CRASH_NATIVE (native abort), JNI DETECTED ERROR...", etc.
- **All entries** (not filtered view) as plain text, one per paragraph
- Each entry shows: timestamp, outcome (STARTED/OK/FAILED), op, endpoint, model, HTTP status, duration, request/response details, and stack trace

**Quality:** Device info and exit reason lifted to header for immediate diagnosis context. Full details included, not abbreviated.

### Crash Warning Dialog
**File:** `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/CrashWarningViewModel.kt`

Each app implements its own dialog wording (e.g., Scrybe: "on-device transcription", Price Drop: "Ask", Shelf Snap: "analysis"), backed by shared ViewModel. Dialog offers "Dismiss" button, which clears the stale marker memory and writes a completion entry to the log.

---

## 3. Logging Coverage Across Apps

### Operation Types & Sites
Across Scrybe, Shelf Snap, and Price Drop, the following distinct ops are logged:

**Scrybe:**
- `transcribe` / `transcribe-start` — local Whisper transcription (file size, char count, chunk timings when debug enabled)
- `diarize` / `diarize-start` / `diarize-engine-loaded` / `diarize-audio` / `diarize-assign` — speaker attribution (local or cloud)
- `transform` / `transform-start` / `transform-engine-loaded` — text transformation (local LLM)
- `ask` / `ask-start` / `ask-engine-loaded` — conversational prompts (local or cloud)
- `history` — summarization of conversation history
- Model download/import — Whisper/Gemma local model acquisition

**Shelf Snap:**
- `vision-analyze` / `vision-analyze-start` / `vision-engine-loaded` — local/cloud image analysis (photo file size, char output)
- `listing-refine` / `listing-refine-start` / `listing-refine-engine-loaded` — local item refinement
- `market-research-synthesize` — market research (local or cloud)
- `web-search` — web search integration
- `jina-read` / `firecrawl-read` / `{provider}-read` — page reading services
- Model download — Gemma local model acquisition

**Price Drop:**
- `ask` / `ask-start` / `ask-engine-loaded` — local/cloud LLM queries
- `price` — price check/refresh
- `product-search` — product discovery
- `extract-product` — extraction from page content
- `barcode` — barcode scanning
- `chat` — chat messages
- Model download — Gemma local model acquisition

**Common (all apps):**
- `app-launch` — device fingerprint (Android API level, device model, RAM, CPU info, LiteRT availability)

### Service Endpoints
- **On-device:** "on-device" (for local Whisper, Gemma inference)
- **Cloud AI:** "/v1/audio/transcriptions" (OpenAI Whisper), "/v1/responses" (OpenAI Chat)
- **Page readers:** "jina-reader", "firecrawl-reader", "jina", "rainforest", "{provider}-reader"
- **Internal:** "worker-managed-search", "device-info"

### Coverage Assessment
**AI-call scoped:** Logging is **narrowly focused on AI inference, model downloads, and web service calls**. Each AI operation records:
- Whether it started (`-start` marker before risky native calls)
- Whether it succeeded/failed
- Duration, HTTP status, input/output summary
- Full error stack trace on failure
- Native crash reason via OS exit info

**NOT logged:**
- Navigation events (screen changes, back stack, deep link routing)
- General app lifecycle (onCreate, onStart, onPause, onDestroy)
- UI state changes (toggles, expansions, scroll position, visibility)
- Background service lifecycle (foreground service start/stop, sync events)
- ANRs or hangs (unless caught as uncaught exception or OS records REASON_ANR)
- Silent data loss (e.g., a query that returned 0 results when >0 expected)
- Race conditions / concurrency bugs (unless they trigger a crash)
- Assertion failures or contract violations that don't throw

**Example blind spots:** 
- Transcription footer stuck visible mid-stream (real bug fixed in commit 32dc950)
- Navigation-cancelled in-flight work (fixed in commits 5d3e9b4, 32dc950)
- Concurrent map mutations creating stale UI state (fixed in 32dc950)
- R8 obfuscation stripping JNI getters (caught only because native crash was logged, commit 7db6806)

---

## 4. Crash & Exit Capture

### Kotlin Uncaught Exception Handler
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:245–250`

Registered once at app launch (`install()`):
```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    runCatching { write(crashEntry(thread, throwable)) }
    previousHandler?.uncaughtException(thread, throwable)
}
```

Captures:
- Thread name
- Exception class (full name: `java.lang.NullPointerException`)
- Message
- Full stack trace via `PrintWriter`

Falls back to previous handler (system default, crash reporting SDK if installed, etc.) after recording.

**Gap:** Does not catch native (JNI) crashes—the process dies in native code before any Kotlin exception handler runs.

### ApplicationExitInfo (API 30+)
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:263–334`

Invoked once per app launch via `recordPreviousExitReasonIfNew()`:

```kotlin
val exit = context.getSystemService(ActivityManager::class.java)
    ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
    ?.firstOrNull()
```

Records:
- **Reason enum** (CRASH_NATIVE, CRASH, ANR, LOW_MEMORY, SIGNALED, EXCESSIVE_RESOURCE_USAGE, etc.)
- **Reason description** from OS
- **Timestamp** (used for deduplication)
- **Process importance** and PSS (private set size) in MB
- **Exit trace** (native tombstone/stack trace, if available) — read up to 16 KB via `exit.traceInputStream`

De-duplicated by timestamp stored in SharedPreferences (`KEY_LAST_RECORDED_EXIT_TIMESTAMP`).

**Limitations:**
- Only available on API 30+ (Android 11+); on API 26–29, no native crash data is recorded
- OS only makes trace available if the OEM captures one; some devices/OEM skins do not
- The historical entry is written **on the next launch** (after-the-fact), not at crash time
- Low-memory kills and ANRs are captured only if the OS generates a reason; some OEMs may not

### Crash Pair Detection
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:148–157, 149–156`

When launching, the store checks whether the newest entry is a dangling start marker across stages:
- Stage 1: `op-start` (before model load)
- Stage 2: `op-engine-loaded` (after model loaded, before inference)
- Stage 3: `op` (completion/error)

A dangling marker at **any stage** is detected; the detection compares via `baseOp()` (stripping stage suffix) so a crash recorded at `-engine-loaded` matches a retry starting at `-start`. The operator/model pair is then remembered in SharedPreferences.

**Bug history:** Commit 90b6a85 fixed a bug where only `-start` was marked; crashes during generation (after `-engine-loaded`) were invisible.

---

## 5. Real Bug-Catching Verdict

Git history documents three classes of real bugs found and fixed; assessment of Debug Log's role:

### Bug Class 1: Native JNI Crashes During Model Load
**Example:** Commit 7db6806 — "Keep litertlm's JNI-facing getters through R8"

**What happened:** Debug Log export from a user captured:
```
Previous run ended: CRASH_NATIVE (native abort or segfault)
JNI DETECTED ERROR IN APPLICATION: mid == null in call to CallIntMethodV
  from long com.google.ai.edge.litertlm.LiteRtLmJni.nativeCreateConversation(...)
```

**Root cause:** R8 obfuscation stripped JNI-callable getter methods because they had no call sites in Java code; native code called them directly via reflection, invisible to the static analyzer.

**Would Debug Log catch this alone?** **YES**—The native crash reason + JNI error message would be recorded in `ApplicationExitInfo` (API 30+ only). **Without a user export describing symptoms**, the trace alone would suggest "R8 is stripping code" only if an engineer read the JNI error text and knew what to look for. The crash warning dialog alerts the user to a crash; the exported log provides the JNI message text. Very high-value capture.

**Post-fix logging:** Now logs three-stage `nativeCreateConversation-start`, `nativeCreateConversation-engine-loaded`, `nativeCreateConversation` entries so the crash pinpoints which stage.

### Bug Class 2: Crashes During Generation, Not Just Load
**Example:** Commit 90b6a85 — "Detect a crash during generation, not only one during model load"

**What happened:** A user reported "made it far and died at inference on qwen" — model loaded successfully but crashed mid-generation.

**Root cause:** Start markers (`-start`, `-engine-loaded`) at different stages were not all marked as start markers. A death after `-engine-loaded` was written left that entry as the last one, with no corresponding completion. Without a matching completion, the stale-marker detection did not recognize it as unfinished—**no crash warning**.

**Would Debug Log catch this alone?** **Only partially.** The crash reason from `ApplicationExitInfo` would be recorded, and an engineer reviewing the log would see that a model was in flight (visible from the `-engine-loaded` entry with no successor). **But the crash warning would not fire**, and the user would not be alerted at next launch that something failed. The only way to discover it was to read the export manually and infer the problem.

**Post-fix logging:** All three stages now write start markers; a dangling `-engine-loaded` or `op-engine-loaded` is detected as a crash.

### Bug Class 3: Race Conditions & UI State Corruption
**Example:** Commit 32dc950 — "Fix concurrency race and navigation-survival gaps"

**Sub-bugs:**
1. `TranscriptionCancellationController.register()/unregister()` mutated maps and then separately published a snapshot. Two concurrent calls (e.g., auto-transcribe finishing, manual retry finishing) could publish snapshots out of order, permanently overwriting a correct empty snapshot with a stale non-empty one—progress footer stuck visible forever.

2. `MarketResearchProgressTracker` et al. launched work on `viewModelScope`, so navigating away cancelled the underlying call even though the display state survived in a singleton. User would back out, back out again, retry—but each attempt would get hard-cancelled by navigation.

**Would Debug Log catch this alone?** **NO.** Neither bug causes a crash or a failed AI call log entry. The footer stays visible (bug 1) and transcriptions keep failing with `CancellationException` (bug 2), but the cause is a logic/concurrency error, not an AI inference failure. **An AI call log entry would show the cancellation exception in the stack trace**, but only for bug 2, not bug 1. And without the user manually testing the sequence ("retry transcription, navigate away, navigate back, see footer still visible"), the log would not surface the issue on its own.

**Post-fix logging:** No change to logging—fixes were in code, not in the log.

### Bug Class 4: Navigation-Related Cancellations
**Example:** Commit 5d3e9b4 — "Survive navigation for manual retry transcription"

**What happened:** User tapped "Retry", backed out to previous screen immediately, tapped "Retry" again. Each retry showed `CancellationException` in the AI call log with no apparent reason—the call itself was not failing, it was being cancelled by the ViewModel's scope being destroyed.

**Would Debug Log catch this alone?** **Partially.** The `CancellationException` would show in the stack trace. An engineer reading the export would see a pattern: "Retry transcription → CancellationException → Retry transcription → CancellationException". The correlation with "backing out" would require the user to describe their actions. **The log does not timestamp navigation events, so the cause is not obvious without user testimony.**

**Post-fix logging:** No change to logging. Fix was to run the actual transcription on a process-scoped coroutine instead of the ViewModel's scope.

---

### Summary Verdict

**Strong for:** Native crashes (JNI, native model load/generation), uncaught Kotlin exceptions, process exits due to low memory/ANRs

**Weak for:** Logic bugs (race conditions, state corruption), UI glitches without crashes, cancellations without crash (only visible as `CancellationException` in stack trace with no reason context), silent failures (data loss, wrong results, no output)

**Requires user description for:** Intermittent issues, timing-dependent bugs, navigation-related cancellations

**Cannot catch without application-level breadcrumb logging:** Navigation events, background service lifecycle, UI state transitions

**Single biggest gap:** No generic "breadcrumb" log for non-AI app events. All three fixed bugs (except the pure native crash) required either (1) a user describing the sequence of actions, or (2) an engineer reading the export and inferring cause from `CancellationException` + stack trace context. The Debug Log is a **post-mortem tool for crashes and AI inference failures**, not a **system-wide behavior tracer**.

---

## 6. Gaps in Observability

### Missing Generic Observability
1. **No breadcrumb log for general app events** — navigation, screen lifecycle, background service events, user interactions. Every bug except the pure JNI crash required manual user description or inference from cancellation-exception patterns.
2. **No third-party crash reporting integration** — no Firebase Crashlytics, Sentry, or similar. The app relies entirely on the Debug Log for unhandled-exception capture.
3. **No API-level-gated fallback** — On API 26–29 (devices below Android 11), `ApplicationExitInfo` is unavailable, so native crashes are only caught if the app has already recorded a dangling start marker. A native crash that occurs before any AI operation starts (e.g., in library initialization) will have no `ApplicationExitInfo` record and no app-recorded start marker to detect it.
4. **No ANR/hang detection** — Only crashes that trigger `Thread.setDefaultUncaughtExceptionHandler` or are recorded by the OS as exit reason. Hangs/freezes are invisible unless the OS records REASON_ANR.
5. **No assertion/contract logging** — No way to log "expected condition X but got Y" without raising an exception. Assertion-style failures (wrong count, missing item, unexpected state) are silent unless they escalate to a crash.

### Specific Blind Spots (from real bugs)
1. **Navigation cancellations** — A `CancellationException` is logged, but the log has no timestamp for the navigation event that caused it, so cause-and-effect is invisible without user testimony.
2. **Concurrency races in UI state** — Mutations and snapshot-publishes that race each other (commit 32dc950) produce no log entries; the bug is pure logic, not I/O or networking.
3. **Silent data loss** — A query that returns 0 results when >0 are expected, or a file that fails to save, with no exception thrown.
4. **Progress UI glitches** — Footer stuck visible (real bug, commit 32dc950), progress bars frozen, but only if it's caused by a task that's logged in the debug log. A UI-state bug orthogonal to AI calls is invisible.

### What Existing Logging *Can* Detect
- Native crashes with JNI error messages or tombstones (API 30+)
- Kotlin uncaught exceptions in any thread
- Network/service call failures (timeouts, HTTP 5xx, etc.)
- Model load/inference failures (out of memory, incompatible checksum, corrupted model file)
- Explicit exception logging in catch blocks (when developer calls `debugLogStore.record(throwable)`)

### What It Cannot Detect
- Crashes/exits on API 26–29 unless a start marker was already written
- Hangs, ANRs, or deadlocks (unless OS records REASON_ANR, only available API 30+)
- Logic/concurrency bugs that don't crash
- Navigation glitches
- UI state corruption
- Silent data loss or wrong results
- Performance regressions (unless duration exceeded a logged threshold, which is not currently done)

---

## 7. Export & Sharing Flow

### User Flow
**File:** `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogScreen.kt:80–92`

1. Open Debug Log screen (in-app settings)
2. Tap the Share icon (top-right)
3. System chooser shows ("Share debug log"):
   - Email apps
   - Messages
   - Cloud storage apps (Google Drive, OneDrive, etc.)
   - Chat apps
4. Choose destination, send

**Exported text includes:**
- Line 1: "Debug log · {N} entries, newest first"
- Device fingerprint (if recorded): "Device: Android {API}, {device model}, {RAM available}, CPU info, LiteRT status"
- Previous exit reason: "Previous run ended: CRASH_NATIVE (native abort or segfault), JNI error, PSS {MB}"
- All entries (newest first, **not filtered**):
  - Timestamp, outcome, op, endpoint, model, HTTP status, duration
  - Request/response snippets
  - Full exception type + message + stack trace

### Share-Text Quality
**File:** `/home/user/twobits/shared/debug-log-ui/src/main/kotlin/com/twobits/debuglogui/DebugLogScreen.kt:286–327`

- **Completeness:** Exports **all entries, not just filtered view**, preserving every detail
- **Device context:** Device fingerprint and exit reason lifted to header for immediate diagnosis
- **No truncation:** Full stack traces included (size-capped by individual entry trace limit of 16 KB)
- **No PII filtering:** Filenames (audiofile.wav, photo.jpg), URLs, model names are all included

### No File Export
- No "Save to file" option
- No email-attachment export
- Must use Intent.ACTION_SEND, which limits to share-to-app (no direct file save)

### Quality for Remote Diagnosis
**Strong points:**
- Device info and exit reason in header = immediate context
- Full traces = engineer can see exactly where things failed
- All entries = chronological timeline preserved

**Weak points:**
- No op-level filtering in export (filtered view exports entire log)
- No search/grep-in-export capability
- Text-based, not structured (no JSON export)
- Requires user to manually copy and paste text into a bug report form if sharing via email is awkward
- No automatic transmission to a crash reporting service

---

## 8. Retention & Privacy

### Retention Policy
**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:514–614`

- **Max entries:** 150 (line 606)
- **Max file size:** 1 MB (line 613)
- **Rotation:** Oldest-first; when byte budget exceeded, drops oldest entries until file fits
- **Lifecycle:** Persists across app restarts; cleared only when user taps "Clear log" in Debug Log screen
- **No automatic expiry:** Entries from 3 days ago sit alongside today's; no time-based rotation
- **Typical coverage:** 150 entries at ~5–10 KB per entry = roughly 750 KB on disk = **~1–2 hours of app usage** (depending on how many AI calls/services are invoked)

### Privacy Concerns

#### What is Logged
1. **Device fingerprint:** Android API, device model, RAM available, CPU, LiteRT availability — **Non-sensitive; identifies device type, not user.**
2. **File names:** `audiofile.wav`, `transcription_2026_09_25.m4a`, `photo_12345.jpg` — **Could reveal user's naming habits or file organization**
3. **Operation names:** `transcribe`, `ask`, `barcode` — **Reveals which features the user employed, but not content**
4. **Request summaries:** File names, file sizes, memory state, model names — **No actual audio/photo content, no query text, no response text**
5. **Response snippets:** Character counts ("1200 chars"), error messages, exception types — **Summaries only, never raw content**
6. **Exception messages:** "File not found", "Network timeout", "Illegal argument" — **Generic, not user-content-specific**
7. **Full stack traces:** Internal method names, line numbers — **Source code structure, not user data**

#### What is NOT Logged
- Audio recordings (even summary length is logged, not content)
- Photos (only filename and size)
- Transcripts (only character count)
- User queries/prompts (only "request/response summaries", e.g., "model=qwen, endpoint=/v1/responses")
- Web pages (only "page-read op=jina-read" and character count of result)
- Prices, products, or search queries (only operation names and summaries)

#### Privacy Assessment
**Low risk:**
- No sensitive personal data (SSN, passwords, API keys) in log fields
- Filenames and operation names reveal feature usage, not content
- Summaries are intentionally non-detailed

**Moderate risk:**
- File names can leak user naming patterns (e.g., "tax_return_2025.pdf")
- Stack traces expose internal app structure (package names, method names, line numbers—useful to attackers for reverse engineering)
- A 1–2 hour history of feature usage (transcribe 5 times, ask 3 times, barcode 10 times) reveals app usage patterns

**Mitigation:**
- No automatic transmission to cloud; export is manual user action
- File is in app's private directory, not readable by other apps
- User can Clear log at any time
- Debug Log screen is in Settings, not in main app UI (users who don't know about it won't stumble into it)
- No third-party service integration (no Crashlytics sending logs to Google)

#### Sensitive Data Not Captured (Gaps)
- **Latitude/longitude:** No location logging
- **User ID / account name:** No user identification
- **API keys / tokens:** Not logged (though endpoints like "/v1/audio/transcriptions" are)
- **Raw responses:** Only summaries (e.g., "1200 chars", not the 1200 chars themselves)

### Compliance Considerations
- **GDPR:** If EU user exports their debug log, they can see their own data + can delete via Clear button. No automatic cloud transmission = lower GDPR burden than a centralized crash reporting service.
- **Data minimization:** Only logs what's necessary for diagnosing AI inference failures and crashes. Navigation, UI state, etc. not logged = minimal creep.
- **User control:** Full manual control; no surprise transmissions.

---

## Top 5 Priorities

### 1. Add Generic Breadcrumb Logging for Non-AI App Events (Highest ROI)
**Why:** Every navigation-related bug (commits 5d3e9b4, 32dc950), concurrency race (32dc950), and UI-state bug requires manual user description or inference from cancellation exceptions. A breadcrumb log recording navigation transitions, background service lifecycle, and explicit "app state" checkpoints would allow on-device diagnosis without user testimony.

**What:** Add a separate, lightweight breadcrumb/event log (not replacing AI call log) with entries like:
- Navigation: "screen_shown: SessionDetail", "screen_hidden: SessionDetail"
- Service: "foreground_service_started: RecordingForegroundService", "stopped"
- App lifecycle: "onCreate", "onStart", "onPause", "onDestroy"
- Explicit checkpoints: "transcription_started", "transcription_cancelled", "transcription_completed"
- Each entry: timestamp, event type, optional detail (e.g., exception type for cancellation)

**Scope:** 10–100 entries max (much smaller than AI call log's 150). Keep in memory only (no file persistence) to avoid I/O cost, or if persisted, separate file rotated independently.

**Impact:** Would have **immediately surfaced** navigation cancellations (reason clear in breadcrumb timeline) and concurrency races (state transitions visible).

---

### 2. Integrate Third-Party Crash Reporting (Firebase Crashlytics or Sentry)
**Why:** On API 26–29, native crashes are invisible unless a start marker was already written. A production-ready crash reporting service is a backstop.

**What:** Add Firebase Crashlytics (or Sentry) integration, sending:
- Uncaught Kotlin exceptions (already captured by app's handler, just forward to Crashlytics)
- Custom breadcrumbs (navigation, AI call start/completion)
- Crash reason from `ApplicationExitInfo` (if API 30+, send to Crashlytics on next launch)

**Scope:** Opt-in or behind feature flag (users concerned about privacy can disable).

**Impact:** Would have **caught native JNI crash on API 26–29 devices** (currently relies on user upgrading to API 30+ or manually recording a start marker beforehand).

---

### 3. Add Duration Thresholds & Performance Alerts
**Why:** A transcription that takes 10x longer than usual, or a model load that stalls for 5 minutes, is invisible unless duration is compared to expected ranges. Real hangs are indistinguishable from merely-slow operations.

**What:** For each AI call op, log and track:
- Expected duration range (e.g., transcribe 1–30s, ask 2–60s)
- If actual > 2x expected, record a warning entry
- If duration caps out (e.g., no heartbeat for 5 minutes), record as "likely hung"

**Scope:** Per-op thresholds tuned to device/model (e.g., a slow device's transcription might be 2x a fast device's).

**Impact:** Would have **caught hung/freezing local transcriptions** (real bug fixed in commit 5a37401, but only after user reported "transcription never completes").

---

### 4. Add Pre-Crash Start Markers for High-Risk Operations
**Why:** Some operations (JNI calls, FFI calls, native model loads, low-level I/O) are more likely to crash natively and should record start markers proactively, even in low-logging-overhead environments.

**What:** For every AI call that invokes native code, unconditionally record a `<op>-start` marker with `startMarker=true` before the risky call, even if details-logging is disabled. (Already done for Whisper transcribe and Shelf Snap vision; generalize to all native model calls.)

**Scope:** No code change needed; this is already the pattern. Just ensure every native call site follows it.

**Impact:** Improved detection of native crashes across all stages (already mostly implemented; ensures consistency).

---

### 5. Add Debug Log Search & Op-Level Export
**Why:** A 150-entry log with one-line-card filtering by type (All/Crashes/AI/Service) is coarse. A user trying to diagnose a specific transcription failure must manually scan entries. Search (op name, timestamp range, endpoint) would reduce manual work.

**What:** 
- Add text search in Debug Log screen (search by op name, model, endpoint, error message)
- Add op-level export (export only entries matching filter, with timestamps, not just filtered view)
- Optional: JSON export format for programmatic parsing

**Scope:** UI enhancement + export formatter. Minimal backend change.

**Impact:** Would **speed up diagnosis 5–10x** for multi-hour logs; enables filtering to just failed `transcribe` ops instead of scanning all 150 entries.

---

## Summary: Highest-Value Gap
**The single highest-value addition is #1: Breadcrumb Logging for Non-AI Events.**

Current state: Debug Log catches native crashes and AI inference failures well. But 3 out of 4 recent real bugs (navigation cancellations, concurrency races, UI glitches) would NOT be caught without a breadcrumb trail of app state transitions. Adding breadcrumb logging is a modest code addition (~500 lines, simple append-to-list semantics) with the highest ROI for unblocking on-device diagnosis of non-crash bugs.

Second highest: #2 (Crashlytics integration) for API < 30 coverage. After that, #3 (duration thresholds) for detecting hangs, which are currently invisible.
