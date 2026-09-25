# Scrybe Android App - Audit Verification Report
**Date:** 2026-09-25  
**Validator:** Fact-check review  
**Original Report:** 10-scrybe-todos.md

---

## Verification Findings by Claim

### 1. AI Output Token Budget Exhaustion (OpenAiInsightService.kt:91-96)
**Claim:** "gpt-5-mini (reasoning model) can exhaust output tokens mid-reasoning, returning status='incomplete' with empty output, and code silently falls back to default results"

- ✅ **CONFIRMED** - Model name is "gpt-5-mini" (verified at line 235: `const val MODEL_NAME = "gpt-5-mini"`)
- ✅ **CONFIRMED** - Comment at lines 91-96 accurately describes reasoning token exhaustion returning status="incomplete" with EMPTY output
- ✅ **CONFIRMED** - Code uses `.ifBlank` fallback at lines 49 and 70 to silently convert empty responses to neutral sentiment/empty topics

---

### 2. AI Output Token Budget Exhaustion (OpenAiDiarizationService.kt:282-289)
**Claim:** "gpt-5-mini reasoning model can exhaust output token budget, returning status='incomplete' with empty output"

- ✅ **CONFIRMED** - Model is "gpt-5-mini" (line 536: `const val MODEL = "gpt-5-mini"`)
- ✅ **CONFIRMED** - Comment at lines 282-289 accurately describes the issue: "when the cap is hit mid-reasoning the response comes back status='incomplete' with EMPTY output_text"
- ⚠️ **CORRECTED** - The audit's Priority #2 claims this "could silently produce single-speaker results" but this is NO LONGER TRUE. The current code (lines 372-382) THROWS an IOException instead of silently defaulting. The comment at lines 306-311 explains the old contract allowed silent fallback, but that path was changed.

---

### 3. Large Audio File Format Support Gap (OpenAiAudioChunker.kt:30-32)
**Claim:** "Files >25MB cannot be chunked/split except in M4A format; WAV/MP3 >25MB users told to re-record in M4A"

- ❌ **FALSE - THRESHOLD INCORRECT**: The limit is 20MB, not 25MB
  - **Actual code:** Line 236: `const val MAX_DIRECT_UPLOAD_MB = 20`
  
- ⚠️ **CORRECTED - FORMAT SUPPORT**: NOT M4A-only. Supported chunking formats are:
  - Lines 195-200 show `muxerOutputFormatFor()` returns formats for:
    - "aac", "m4a", "mp4" → MUXER_OUTPUT_MPEG_4
    - "webm" → MUXER_OUTPUT_WEBM
  - Unsupported formats (WAV, MP3, OGG, etc.) throw error (lines 28-33)
  - **Corrected message:** "Files >20MB in formats other than M4A/MP4/AAC/WEBM cannot be split for upload"

---

### 4. Large Audio File Format Support - Duration Read (OpenAiAudioChunker.kt:37-38)
**Claim:** "Throws error if file duration cannot be read (potential data corruption not handled gracefully)"

- ✅ **CONFIRMED** - Lines 35-39 throw UnsupportedOperationException if duration read fails

---

### 5. Diarization Incomplete Response Handling (OpenAiDiarizationService.kt:372-382)
**Claim:** "Response returns status='incomplete' with empty output_text when token budget exceeded; could silently produce single-speaker results"

- ❌ **FALSE AS DESCRIBED** - Does NOT silently fall back to single speaker
- ✅ **CONFIRMED (corrected behavior)** - Lines 372-382 show proper error handling: THROWS IOException when output is blank/empty
- **Code:** Lines 372-382 explicitly check `if (text.isNullOrBlank())` and throw IOException with diagnostic detail about token budget
- **Note:** Comment at lines 306-311 acknowledges the old contract allowed silent fallback (which masked failures), but current code has hard error handling

---

### 6. Orphaned File Import UI (FileManagerScreen.kt:232, 316)
**Claim:** "UI shows 'Orphaned' chip with no action (onClick = {}, enabled = false)"

- ✅ **CONFIRMED** - Line 232: `AssistChip(onClick = {}, enabled = false, label = { Text("Orphaned") })`
- ✅ **CONFIRMED** - Line 316: Same chip repeated for orphaned entries

---

### 7. Token Count Display UI (ProfilesScreen.kt:2037)
**Claim:** "SuggestionChip has onClick = {} - informational chip (token count display)"

- ✅ **CONFIRMED** - Line 2037: `SuggestionChip(onClick = {}, label = { Text("3 calls · ${suggestion.tokensUsed} tokens", ...)})`

---

### 8. Local Diarization Service Error (LocalDiarizationService.kt:32-36)
**Claim:** "Local diarization will error if no on-device model available (not gracefully degraded)"

- ✅ **CONFIRMED** - Lines 32-37 explicitly call `.getOrElse { cause -> error(...) }` when LLM call fails
- **Note:** Audit's "not gracefully degraded" is a design choice, not a bug — this is proper error surfacing rather than silent failure

---

### 9. Speaker Assignment JSON Parsing (OpenAiDiarizationService.kt:402-407)
**Claim:** "Hard failure if speaker assignment JSON is malformed/truncated (previously silently defaulted to SPEAKER_1)"

- ✅ **CONFIRMED** - Line 406: `throw IOException("Speaker assignment response was not parseable JSON (truncated?): ...")`
- ✅ **CONFIRMED** - Comment at lines 403-405 explains this was changed from silent fallback (old: "silently defaulted to SPEAKER_1"), which masked real failures

---

### 10. Audio Format & Codec Support (OpenAiDiarizationService.kt:106-114)
**Claim:** "Supports M4A, MP4, MP3, WAV, OGG, WebM; defaults to audio/aac for unknown extensions"

- ✅ **CONFIRMED** - Lines 106-114 exactly match the claimed formats and default behavior

---

### 11. Recording Save Failure (RecordingForegroundService.kt:492)
**Claim:** "Incomplete: 'Recording did not save correctly' error - might not fully save in some cases"

- ✅ **CONFIRMED** - Line 492: `error("Recording did not save correctly. Please try again.")`
- **Note:** Audit's "incomplete" framing is misleading. Lines 490-492 show this is a validation check for zero-byte files, not a partial-save scenario. The code properly detects and errors on empty recordings.

---

### 12. OpenAI Diarization Fallback (OpenAiDiarizationService.kt:40+)
**Claim:** "OpenAI diarization implementation exists but fallback behavior unclear"

- ⚠️ **PARTIALLY CORRECTED** - Audit's "fallback behavior unclear" is vague, but reviewing the full `diarize()` function (lines 40-81):
  - No silent fallbacks found - errors are properly propagated
  - Fallback to Local diarization is determined by caller logic, not this service
  - Current behavior: hard errors on any transcription/assignment failure

---

## Verified Findings for Final Report

### CONFIRMED (Accurate)
1. **AI Output Token Budget Exhaustion - Insights Service** (`OpenAiInsightService.kt:91-96, 49, 70`)
   - gpt-5-mini reasoning model can exhaust output tokens mid-reasoning
   - Returns status="incomplete" with empty output
   - Code silently falls back to neutral sentiment/empty topics via `.ifBlank`
   - **Risk:** Users see default results when analysis actually failed

2. **Audio Format & Codec Support Flexibility** (`OpenAiDiarizationService.kt:106-114`)
   - Transcription endpoint supports M4A, MP4, MP3, WAV, OGG, WebM formats
   - Defaults to audio/aac for unknown extensions

3. **Orphaned File UI Element** (`FileManagerScreen.kt:232, 316`)
   - Informational "Orphaned" chip has no action (onClick = {}, enabled = false)

4. **Profile Token Count Display** (`ProfilesScreen.kt:2037`)
   - Informational token usage chip has no action (onClick = {})

5. **Local Diarization Error Surfacing** (`LocalDiarizationService.kt:32-37`)
   - Errors if on-device model unavailable (proper error handling, not silent fallback)

6. **Speaker Assignment JSON Validation** (`OpenAiDiarizationService.kt:402-407`)
   - Hard failure on malformed/truncated JSON (improved from previous silent fallback)

7. **Recording File Validation** (`RecordingForegroundService.kt:490-492`)
   - Error message when recording file saves as empty/zero-bytes

---

### CORRECTED (Inaccurate Claims)

1. **Large Audio File Format Support Limitation** (`OpenAiAudioChunker.kt:30-32, 195-200`)
   - **Audit claimed:** "Only M4A; files >25MB fail"
   - **Actual:** Supports M4A, MP4, AAC, and WEBM for chunking; threshold is 20MB not 25MB
   - **Corrected:** Unsupported formats (WAV, MP3, OGG) >20MB cannot be split and error with message directing user to supported formats or bit rate reduction

2. **Diarization Incomplete Response Handling** (`OpenAiDiarizationService.kt:372-382`)
   - **Audit claimed:** "Could silently produce single-speaker results"
   - **Actual:** Code THROWS IOException when output is empty/blank (lines 372-382)
   - **Note:** Old code (pre-fix) allowed silent fallback; current code has hard error handling with proper diagnostics

3. **OpenAI Diarization Fallback Behavior** (`OpenAiDiarizationService.kt:40-81`)
   - **Audit claimed:** "Fallback behavior unclear"
   - **Actual:** No silent fallbacks in current code. Errors are properly propagated. Fallback to Local diarization is orchestrated by caller, not this service.

---

## Total Verification Summary

**Claims Checked:** 12 distinct findings + model/threshold specifics  
**✅ CONFIRMED:** 7 findings + audio format support  
**⚠️ CORRECTED:** 3 findings (model availability, AI token budget - Diarization now has proper error handling, large file format support)  
**❌ FALSE:** 2 specific claims (25MB threshold is 20MB; M4A-only is actually M4A/MP4/AAC/WEBM)  

**Actual model name used:** `"gpt-5-mini"` (confirmed in both services)
**Actual file size threshold for chunking:** 20MB (not 25MB)  
**Actual chunking-supported formats:** M4A, MP4, AAC, WEBM (not M4A-only)

---

