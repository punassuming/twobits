# Shelf Snap - Audit Validation Report
**Date:** 2026-09-25  
**Validator:** Fact-check audit of AI-generated findings

---

## Verification Status by Claim

### 1. **Three UI-only settings toggles with zero backend implementation**
**Status:** ⚠️ CORRECTED

**Original claim:** `multiPhotoAnalysis`, `aiConditionDetection`, `autoPriceEstimate` exist in the AI Configuration screen but have "zero backend implementation."

**Finding:** ALL THREE SETTINGS ARE ACTUALLY IMPLEMENTED AND ACTIVELY USED IN BUSINESS LOGIC:

1. **multiPhotoAnalysis** (line 119 in ItemRepository.kt)
   - ✅ CONFIRMED IMPLEMENTED
   - File: `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:119`
   - Usage: Controls whether `effectivePaths` includes all photos or just the primary photo
   - Code: `if (observeMultiPhotoAnalysis().firstOrNull() == true) { photoPaths } else { listOfNotNull(...) }`
   - Doc comment: Lines 102-111 explicitly document this setting's effect

2. **aiConditionDetection** (line 156 in ItemRepository.kt)
   - ✅ CONFIRMED IMPLEMENTED
   - File: `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:156`
   - Usage: Controls whether the AI-detected condition is applied to the result or defaulted to GOOD
   - Code: `condition = if (getAiConditionDetection()) result.condition else Condition.GOOD,`
   - Doc comment: Lines 107-108 document this behavior

3. **autoPriceEstimate** (line 157 in ItemRepository.kt)
   - ✅ CONFIRMED IMPLEMENTED
   - File: `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:157`
   - Usage: Controls whether the AI-estimated value is applied to the result or defaulted to 0.0
   - Code: `estimatedValue = if (getAutoPriceEstimate()) result.estimatedValue else 0.0,`
   - Doc comment: Lines 107-108 document this behavior

**Corrected statement:** All three toggles ARE implemented in the backend and actively influence app behavior during photo analysis. The settings are displayed in the UI and function correctly.

---

### 2. **Multi-photo Local Vision Limitation**
**Status:** ✅ CONFIRMED (partially accurate)

**Original claim:** "LocalVisionService explicitly only sends single photos to the on-device model regardless of the multi-photo setting."

**Finding:** 
- File: `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/local/LocalVisionService.kt:24-33`
- Doc comment confirms (lines 24-33): "Only sends a single photo regardless of the user's multi-photo-analysis setting; multi-image local vision is a separate, untested question."
- In code (ItemRepository.kt line 139-149), LOCAL mode explicitly uses only `primaryPath`, not the multi-photo setting
- ✅ CONFIRMED: On-device vision does not support multi-photo analysis

**Nuance:** The multi-photo setting DOES work for cloud modes (PRO and BYOK), but has a known limitation in LOCAL/on-device mode. The setting itself is not unimplemented; the LOCAL execution path simply doesn't support it yet.

---

### 3. **On-device Vision Experimental/Unverified Status**
**Status:** ✅ CONFIRMED

**Original claim:** "Feature marked EXPERIMENTAL with 'unverified accuracy on this device'."

**Finding:**
- File: `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/ui/settings/AIConfigScreen.kt:243-244`
- UI Text (line 244): "Experimental — on-device vision reuses the same Gemma model as local listing generation; accuracy is unverified on this device."
- Doc comment (LocalVisionService.kt lines 29-30): "which is evidenced (litert-community lists these repos under 'Multi-Modality Models') but not independently verified end-to-end"
- ✅ CONFIRMED: Feature is marked experimental with unverified accuracy

---

## Verified Findings for Final Report

The following claims from the audit report are confirmed accurate for inclusion in the final engineering priority list:

### Finding 1: Multi-photo Local Vision Limitation (CONFIRMED)
- **File:** `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/local/LocalVisionService.kt:24-33`
- **Status:** EXPERIMENTAL/INCOMPLETE
- **Description:** Local on-device photo analysis only sends a single photo regardless of user's multi-photo-analysis setting. Multi-image local vision is untested and unimplemented.
- **Implementation details:** ItemRepository.kt:139 explicitly uses only primaryPath for LOCAL execution mode
- **Impact:** User-facing gap — users on local-only mode cannot analyze multiple photos at once, unlike cloud path

### Finding 2: Multi-photo Analysis Setting Has Limited Implementation (CORRECTED)
- **File:** `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:119`
- **Status:** IMPLEMENTED BUT INCOMPLETE
- **Description:** Multi-photo analysis setting IS implemented and works in cloud modes (PRO/BYOK), but the LocalVisionService does not support it. The setting correctly controls photo selection for non-local analysis paths.
- **Contradiction with original audit:** The setting is NOT "UI only" with "zero backend implementation" — it is actively used in ItemRepository.analysePhotos()
- **Impact:** Setting works as intended for cloud analysis; incomplete only for local on-device mode

### Finding 3: AI Condition Detection Setting IS Implemented (CORRECTED)
- **File:** `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:156`
- **Status:** FULLY IMPLEMENTED
- **Description:** The aiConditionDetection toggle correctly controls whether the AI-detected condition is applied to results. When disabled, condition defaults to GOOD. **This is not a UI-only setting.**
- **Contradiction with original audit:** Setting is actively used in the photo analysis pipeline
- **Impact:** No issue — feature is working as designed

### Finding 4: Auto Price Estimate Setting IS Implemented (CORRECTED)
- **File:** `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/data/repository/ItemRepository.kt:157`
- **Status:** FULLY IMPLEMENTED
- **Description:** The autoPriceEstimate toggle correctly controls whether the AI-estimated price is applied to results. When disabled, estimatedValue defaults to 0.0. **This is not a UI-only setting.**
- **Contradiction with original audit:** Setting is actively used in the photo analysis pipeline
- **Impact:** No issue — feature is working as designed

### Finding 5: On-device Vision Accuracy Unverified (CONFIRMED)
- **File:** `apps/shelf-snap/app/src/main/java/com/shelfsnap/app/ui/settings/AIConfigScreen.kt:243-244`
- **Status:** EXPERIMENTAL/UNVERIFIED
- **Description:** On-device vision is marked EXPERIMENTAL with "accuracy is unverified on this device." The Gemma model's image-input capability is evidenced but not independently verified end-to-end.
- **Impact:** User trust, data quality — users may get incorrect item data without knowing the feature is untested

---

## Summary of Corrections

**Original audit claims:** 6 incomplete/partial features (with emphasis on "3 UI-only settings with zero backend implementation")  
**Actual findings after validation:**
- ❌ FALSE (3 claims): `aiConditionDetection` and `autoPriceEstimate` have full backend implementation; `multiPhotoAnalysis` has backend implementation but limited to cloud modes
- ✅ CONFIRMED (2 findings): Local vision doesn't support multi-photo; on-device vision accuracy is unverified
- **Real issue count:** 2 genuine technical gaps, not 6

**Key correction:** The audit's central claim about "three UI-only settings toggles with zero backend implementation" is factually incorrect. All three settings ARE implemented. The auditor appears to have searched only the UI layer (SettingsViewModel, AIConfigScreen) without checking where the settings are READ in ItemRepository's business logic.

