# Shelf Snap - Unimplemented Capabilities Audit
**Date:** 2026-09-25

This document catalogs incomplete, placeholder, and unimplemented features in the Shelf Snap app source code (apps/shelf-snap/src/main).

---


## Vision Analysis

### Multi-photo Local Vision (Experimental)
**File:** `app/src/main/java/com/shelfsnap/app/data/local/LocalVisionService.kt:24-32`

- **Status:** EXPERIMENTAL/INCOMPLETE
- **Description:** Local on-device photo analysis only sends a single photo regardless of user's multi-photo-analysis setting. Multi-image local vision is untested and unimplemented.
- **Effort:** Large
- **Impact:** User-facing gap — users on local-only mode cannot analyze multiple photos at once, unlike cloud path


## Photo Analysis

### Multi-photo Analysis Setting (UI Only)
**File:** `app/src/main/java/com/shelfsnap/app/ui/settings/SettingsViewModel.kt:99`, `app/src/main/java/com/shelfsnap/app/ui/settings/SettingsScreen.kt`

- **Status:** UI TOGGLE ONLY
- **Description:** `multiPhotoAnalysis` toggle exists in settings but is never actually checked or used. The setting is displayed to users but has zero effect on analysis behavior.
- **Effort:** Small
- **Impact:** User-facing confusion — setting suggests capability that doesn't work; should either be implemented or removed from UI


### On-Device Vision (Experimental/Unverified)
**File:** `app/src/main/java/com/shelfsnap/app/ui/settings/AIConfigScreen.kt:243-244`

- **Status:** EXPERIMENTAL
- **Description:** On-device vision analysis marked as "Experimental — on-device vision reuses the same Gemma model as local listing generation; accuracy is unverified on this device." This indicates the feature has unknown accuracy and is not production-ready.
- **Effort:** Medium
- **Impact:** User-facing risk — users may get inaccurate item data from local vision analysis


### AI Condition Detection Setting (UI Only)
**File:** `app/src/main/java/com/shelfsnap/app/ui/settings/AIConfigScreen.kt`

- **Status:** UI TOGGLE ONLY
- **Description:** `aiConditionDetection` toggle exists in settings but is never actually checked or used. The setting is displayed to users but has zero effect on vision analysis behavior.
- **Effort:** Small
- **Impact:** User-facing confusion — setting suggests capability that doesn't work; should either be implemented or removed from UI

### Auto Price Estimate Setting (UI Only)
**File:** `app/src/main/java/com/shelfsnap/app/ui/settings/AIConfigScreen.kt`

- **Status:** UI TOGGLE ONLY
- **Description:** `autoPriceEstimate` toggle exists in settings but is never actually checked or used. The setting is displayed to users but has zero effect on price research behavior.
- **Effort:** Small
- **Impact:** User-facing confusion — setting suggests capability that doesn't work; should either be implemented or removed from UI

---

## Top 5 Priorities

### 1. **Remove or Implement Unused Settings Toggles**
**Impact:** User confusion, cluttered UI  
**Effort:** Small  
- Three settings (`multiPhotoAnalysis`, `aiConditionDetection`, `autoPriceEstimate`) appear in the AI Configuration screen but have zero backend implementation
- Users see these toggles but they don't affect app behavior
- **Action:** Either remove from UI or implement the promised functionality

### 2. **Fix Multi-photo Local Vision Limitation**
**Impact:** Feature limitation for local-only users  
**Effort:** Large  
- LocalVisionService explicitly only sends a single photo regardless of user's multi-photo-analysis setting
- Users on local-only mode (no Pro/BYOK) cannot use the multi-photo feature that cloud users can
- **Action:** Implement multi-image support for on-device vision pipeline

### 3. **Clarify On-Device Vision Accuracy Status**
**Impact:** User trust, data quality  
**Effort:** Medium  
- On-device vision is marked EXPERIMENTAL with "unverified" accuracy on device
- Users may get incorrect item data without knowing the feature is untested
- **Action:** Either verify accuracy across devices or more clearly gate feature behind opt-in with warnings

### 4. **Complete Vision Analysis Experimental Status**
**Impact:** Reliability, user expectations  
**Effort:** Large  
- LocalVisionService doc comment notes EXPERIMENTAL reliance on Gemma 4 E2B/E4B image input support which is "evidenced but not independently verified end-to-end"
- No testing documented for this critical path
- **Action:** Add comprehensive testing or move feature out of experimental status

### 5. **Consistency Review: Feature Toggles vs Implementation**
**Impact:** Long-term maintainability  
**Effort:** Medium  
- Pattern of UI-only settings suggests incomplete feature cycles (design/UI done before backend)
- **Action:** Audit all settings to ensure toggle → implementation mapping is complete before release

---

## Summary

**Total findings:** 6 incomplete/partial features found  
**User-facing gaps:** 4 (multi-photo vision, experimental status, unused toggles)  
**Experimental/unverified:** 2 features

All cross-listing and marketplace platform implementations appear complete. All 5 platforms (eBay, Mercari, OfferUp, FB Marketplace, Craigslist) have full integration support. Vision analysis, price research, and listing generation pipelines are fully implemented.

