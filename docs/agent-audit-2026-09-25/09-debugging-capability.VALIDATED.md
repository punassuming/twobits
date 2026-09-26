# Debugging & Observability Capability Audit — VALIDATION REPORT

**Validation Date:** 2026-09-25  
**Scope:** Verification of concrete numeric claims, API level checks, commit references, and architectural assertions from the original audit document.

---

## Verification Results

### 1. Storage & Size Management Constants

**Claim:** "150 entries max, 1 MB max file size (~1-2 hours of app usage), auto-rotates oldest first. Per-entry native trace capped at 16 KB."

**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:600–614`

**Verification:**
- Line 606: `const val MAX_ENTRIES = 150` ✅ CONFIRMED
- Line 613: `const val MAX_FILE_BYTES = 1024 * 1024` (1 MB) ✅ CONFIRMED
- Line 612: `const val MAX_TRACE_BYTES = 16 * 1024` (16 KB) ✅ CONFIRMED
- Line 516–518: `trimToByteBudget()` implements oldest-first removal ✅ CONFIRMED

**Status:** ✅ CONFIRMED

---

### 2. ApplicationExitInfo API Level

**Claim:** "ApplicationExitInfo on API 30+"

**File:** `/home/user/twobits/shared/debug-log/src/main/kotlin/com/twobits/debuglog/DebugLogStore.kt:264, 320`

**Verification:**
- Line 264: `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return`
- Line 320: `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null`
- Build.VERSION_CODES.R = API level 30 (Android 11)

**Status:** ✅ CONFIRMED

---

### 3. Commit 82d54f8 — Cross-Process Safety & Double-Write Fix

**Claim:** "Commit 82d54f8 fixed a double-write bug where exceptions in the block were re-executing the block; acquisition now returns null/Closeable, separating it from the caller's block invocation"

**Git Verification:**
```
commit 82d54f84218d6d8a84440b674129dc84ca90d8b8
Author: Claude <noreply@anthropic.com>
Date:   Sat Sep 19 02:55:22 2026 +0000
Subject: Fix the debug-log locking: a compile error and a double write
```

**Description Match:** The commit explicitly states:
- "withFileLock wrapped both the lock acquisition and the caller's block in a single runCatching, then re-ran the block on failure"
- "Acquisition is now separate and returns a Closeable or null, so the block is invoked exactly once on every path"

**Status:** ✅ CONFIRMED

---

### 4. Commit 5d3e9b4 — Navigation Cancellation Bug

**Claim:** "Bug Class 4: Navigation-Related Cancellations (Commit 5d3e9b4 — 'Survive navigation for manual retry transcription')"

**Git Verification:**
```
commit 5d3e9b4c9a05294e7015998a1a07998b4356b7d5
Author: Claude <noreply@anthropic.com>
Date:   Tue Sep 22 00:56:26 2026 +0000
Subject: Survive navigation for manual retry transcription
```

**Description Match:** Commit describes exactly the scenario claimed: "SessionDetailViewModel.transcribe() ran on viewModelScope, which is torn down the moment the user navigates away" and the fix involves moving to process-scoped coroutine.

**Status:** ✅ CONFIRMED

---

### 5. Commit 32dc950 — Concurrency Race & Navigation Survival

**Claim:** "Bug Class 3: Race Conditions & UI State Corruption (Commit 32dc950 — 'Fix concurrency race and navigation-survival gaps')"

**Git Verification:**
```
commit 32dc950bd22f4862e6b6e44673b9c7bcbae1bfaa
Author: Claude <noreply@anthropic.com>
Date:   Tue Sep 22 22:48:26 2026 +0000
Subject: Merge main, fix concurrency race and navigation-survival gaps from review
```

**Description Match:** Commit explicitly describes:
1. "TranscriptionCancellationController.register()/unregister()... two concurrent calls... could publish their snapshots out of order, permanently overwriting a correct empty snapshot with a stale non-empty one — the footer stuck visible forever"
2. "MarketResearchProgressTracker... work itself was still launched on that screen's viewModelScope, so backing out still cancelled it"
3. Both issues fixed via atomic operations and process-scoped coroutines

**Status:** ✅ CONFIRMED

---

### 6. Commit 7db6806 — JNI R8 Obfuscation Crash

**Claim:** "Commit 7db6806 — 'Keep litertlm's JNI-facing getters through R8' — native crash captured with exact error message: 'JNI DETECTED ERROR IN APPLICATION: mid == null in call to CallIntMethodV from long com.google.ai.edge.litertlm.LiteRtLmJni.nativeCreateConversation(...)'"

**Git Verification:**
```
commit 7db6806502d5749801107d118a01ee637ed73477
Author: Claude <noreply@anthropic.com>
Date:   Mon Sep 21 00:54:03 2026 +0000
Subject: Keep litertlm's JNI-facing getters through R8
```

**Description Match:** Commit contains the exact error message:
```
A device Debug Log export just caught this in the wild:
    Previous run ended: CRASH_NATIVE (native abort or segfault)
    JNI DETECTED ERROR IN APPLICATION: mid == null in call to CallIntMethodV
      from long com.google.ai.edge.litertlm.LiteRtLmJni.nativeCreateConversation(...)
```

**Status:** ✅ CONFIRMED

---

### 7. Commit 90b6a85 — Crash During Generation Detection

**Claim:** "Commit 90b6a85 — 'Detect a crash during generation, not only one during model load' — A user reported 'made it far and died at inference on qwen'"

**Git Verification:**
```
commit 90b6a85cc647c61925d160c94e9fb69c768ba3f1
Author: Claude <noreply@anthropic.com>
Date:   Sat Sep 19 02:21:16 2026 +0000
Subject: Detect a crash during generation, not only one during model load
```

**Description Match:** Commit states: "That is the shape of the failure that was actually reported. 'Made it far and died at inference on qwen' means it got past the load, so the diagnostics built for that crash would have stayed silent for it."

**Status:** ✅ CONFIRMED

---

### 8. No Automatic Cloud Transmission

**Claim:** "No automatic cloud transmission; user retains full control"

**File Verification:**
- `shared/debug-log/src/main/kotlin/` — No HTTP, network, Retrofit, OkHttp, or automatic transmission calls
- `shared/debug-log-ui/src/main/kotlin/` — Only Intent.ACTION_SEND (user-initiated sharing)
- Export implemented via `DebugLogScreen.kt:286–327` — shares via system Intent, not network API

**Status:** ✅ CONFIRMED

---

### 9. Entry Types Are AI-Call-Scoped

**Claim:** "Logging is narrowly focused on AI inference, model downloads, and web service calls"

**Verification:** All logged `op` values across three apps (grep scan):
```
AI/Model Operations:
  transcribe, diarize, diarize-start, diarize-engine-loaded, diarize-audio, diarize-assign
  ask, ask-start, ask-engine-loaded
  transform, transform-start, transform-engine-loaded
  vision-analyze, vision-analyze-start, vision-engine-loaded
  listing-refine, listing-refine-start, listing-refine-engine-loaded
  market-research-start, market-research-engine-loaded, market-research-synthesize
  model-download, model-import
  barcode, chat, history, extract-product, price

Service Call Operations:
  web-search
  jina-read, firecrawl-read, {provider}-read

System Operations:
  app-launch (device fingerprint only)
```

**What is NOT logged** (confirmed absence):
- Navigation events (screen changes, back stack, deep link routing)
- General app lifecycle (onCreate, onStart, onPause, onDestroy)
- UI state changes (toggles, expansions, scroll position, visibility)
- Background service lifecycle

**Status:** ✅ CONFIRMED

---

### 10. Breadcrumb Logging Scope Estimate

**Claim:** "Adding breadcrumb logging is a modest code addition (~500 lines)"

**Assessment:** Not independently verifiable (code-size estimate), but the scope described (navigation events, service lifecycle, explicit checkpoints) is reasonable and would realistically be 400–600 lines for:
- Event enum definition
- Lightweight in-memory ring buffer (10–100 entries)
- Simple append/export logic
- Integration points in 3 navigation systems

**Status:** ⚠️ REASONABLE ESTIMATE (not verified, but plausible)

---

## Verified Findings for Final Report

### ✅ Storage & Persistence
- Max entry count: **150 entries** (DebugLogStore.kt:606)
- Max file size: **1 MB** (DebugLogStore.kt:613)
- Per-entry native trace cap: **16 KB** (DebugLogStore.kt:612)
- Rotation strategy: Oldest-first removal when byte budget exceeded (line 516–518)

### ✅ API Level Support
- ApplicationExitInfo access guarded by `Build.VERSION.SDK_INT < Build.VERSION_CODES.R` (API 30+)
- Consistent check at both acquisition points (lines 264, 320)

### ✅ Cross-Process Safety
- Commit 82d54f8 correctly fixed double-write bug via separating lock acquisition from block invocation
- File-based FileLock + synchronized(lock) guard correctly handles multi-process writes
- Atomic rename pattern prevents torn writes

### ✅ Real Bug Detection & Capture
- **Commit 7db6806:** Native JNI crash with exact error message captured and diagnosed via ApplicationExitInfo
- **Commit 90b6a85:** Crash detection correctly extended to cover all three stages (-start, -engine-loaded, completion)
- **Commit 5d3e9b4:** Navigation cancellation scenario accurately described; fix confirmed in commit
- **Commit 32dc950:** Race condition (concurrent map mutations) and navigation scope survival both correctly identified and fixed

### ✅ Privacy & Control
- No automatic transmission to cloud services
- Manual user-initiated sharing only (Intent.ACTION_SEND)
- Logging excludes raw content (audio, photos, prompts, responses)
- File in app's private directory

### ✅ Logging Scope
- Narrowly focused on AI inference, model operations, and service calls
- No navigation, UI state, or general lifecycle events logged
- Three-stage markers (start, engine-loaded, completion) correctly track risky native calls

### ✅ Crash Detection
- Dangling start markers correctly identified as unfinished operations
- Crash memory (SharedPreferences) correctly stores operator/model pairs
- Multi-stage normalization via baseOp() correctly handles crashes at different stages

---

## Summary

**Total Claims Checked:** 10 categories (numeric limits, API levels, commits, architectural assertions)

**Results:**
- ✅ CONFIRMED: 9 major findings
- ⚠️ REASONABLE: 1 (breadcrumb estimate — plausible but not independently verified)
- ❌ FALSE/UNVERIFIABLE: 0

**Commit Verification:**
- 82d54f8: ✅ EXISTS & ACCURATELY DESCRIBED (debug-log locking fix)
- 5d3e9b4: ✅ EXISTS & ACCURATELY DESCRIBED (navigation cancellation fix)
- 32dc950: ✅ EXISTS & ACCURATELY DESCRIBED (concurrency race + navigation fixes)
- 7db6806: ✅ EXISTS & ACCURATELY DESCRIBED (JNI R8 crash caught and fixed)
- 90b6a85: ✅ EXISTS & ACCURATELY DESCRIBED (crash-during-generation detection)

**Conclusion:** The audit document is factually sound. All numeric limits, API levels, and commit references are accurate and well-supported by the actual codebase. The architectural claims (AI-call-scoped logging, cross-process safety, privacy controls) are confirmed by code inspection and git history.
