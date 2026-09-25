# Scrybe Android App - Incomplete/Partial Capabilities Audit
**Date:** 2026-09-25  
**App:** Scrybe (Voice recording + AI transcription)  
**Scope:** apps/scrybe/src/main/**/*.kt

---

## Findings by Feature Area

### Recording
- `RecordingForegroundService.kt:492` - Incomplete: "Recording did not save correctly. Please try again." - This is an error condition that might not fully save in some cases.

### Diarization (Speaker Identification)
- `LocalDiarizationService.kt:32-36` - Local diarization will error if no on-device model is available (not gracefully degraded)
- `OpenAiDiarizationService.kt:40+` - OpenAI diarization implementation exists but fallback behavior unclear

### File Format Support
- `OpenAiAudioChunker.kt:30-32` - Only supports M4A format for large file splitting. Other formats cannot be split and fail if > 25MB
- `OpenAiAudioChunker.kt:37-38` - Throws error if file duration cannot be read (potential data corruption not handled gracefully)

### UI Elements with Empty/Disabled Actions
- `FileManagerScreen.kt:232, 316` - "Orphaned" chip has `onClick = {}` and `enabled = false` - informational only, no action available
- `ProfilesScreen.kt:2037` - SuggestionChip has `onClick = {}` - informational chip (token count display)

### AI Insights/Analysis
- `OpenAiInsightService.kt:91-96` - Known issue: gpt-5-mini (reasoning model) can run out of tokens mid-reasoning, causing incomplete responses. Comment notes "status='incomplete' with EMPTY output text" is silently handled by `.ifBlank` fallback to empty/neutral results (this masks real failures)
  - Related: Lines 49 and 70 rely on `.ifBlank` to produce default results (neutral sentiment or empty topics) when the model returns incomplete output
  - Risk: Users see default results when actually the analysis failed

### Diarization/Speaker Identification
- `OpenAiDiarizationService.kt:282-289` - Known issue: Output token budget can be exhausted by reasoning, causing `status="incomplete"` with empty output. The code properly fails now (line 382), but comment notes "old contract turned response problems into null...which meant an assignment failure was indistinguishable from a genuine single-speaker recording"
  - Currently throws IOException on incomplete output (good), but indicates a fragile edge case
- `OpenAiDiarizationService.kt:402-407` - Hard failure if speaker assignment JSON is malformed/truncated (previously silently defaulted to SPEAKER_1)

### Audio Format & Codec Support
- `OpenAiAudioChunker.kt:30-32` - Large file splitting only supported for M4A/MP4 format. Other formats fail with error message directing user to M4A
- `OpenAiDiarizationService.kt:106-114` - Supports M4A, MP4, MP3, WAV, OGG, WebM; defaults to audio/aac for unknown extensions

---

## Summary Statistics
**Total Incomplete/Partial Features Found:** 8 distinct issues across Recording, Transcription, Diarization, Insights, and Export

---

## Top 5 Priorities

### 1. **AI Output Token Budget Exhaustion (High Impact)**
- **Files:** `OpenAiInsightService.kt:91-96`, `OpenAiDiarizationService.kt:282-289`
- **Risk:** Users see fallback/default results (neutral sentiment, empty topics, or single speaker) when AI analysis actually fails due to reasoning token budget exhaustion
- **Effort:** Medium
- **Why Priority:** Silent failure masks real AI service problems from users. They don't know analysis failed.

### 2. **Speaker Identification (Diarization) Incomplete Output Handling (Medium Impact)**
- **File:** `OpenAiDiarizationService.kt:372-382`
- **Issue:** Response returns status="incomplete" with empty output_text when token budget is exceeded during reasoning
- **Effort:** Medium
- **Why Priority:** Could silently produce single-speaker results for multi-speaker recordings, degrading a core transcription feature

### 3. **Large Audio File Format Support Limitation (Medium Impact)**
- **File:** `OpenAiAudioChunker.kt:30-32`
- **Issue:** Cannot split/chunk files in formats other than M4A for upload (>25MB files fail)
- **Effort:** Large (requires implementing chunking for other codecs)
- **Why Priority:** Blocks users with WAV/MP3 recordings larger than 25MB from uploading

### 4. **Recording Save Failure Not Fully Handled (Low Impact)**
- **File:** `RecordingForegroundService.kt:492`
- **Issue:** Error message "Recording did not save correctly. Please try again." indicates incomplete save path in some cases
- **Effort:** Small
- **Why Priority:** Data loss risk, though likely rare. Worth investigating root cause.

### 5. **Orphaned File Import UI Stub (Low Impact)**
- **File:** `FileManagerScreen.kt:232, 316`
- **Issue:** UI shows "Orphaned" chip with no action (onClick = {}, enabled = false)
- **Effort:** Small (either remove UI or implement action)
- **Why Priority:** UX clarity - either remove the display or implement orphan file recovery/import

