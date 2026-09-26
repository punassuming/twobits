# PriceDrop App - Unimplemented & Partial Capabilities Audit — VALIDATION REPORT

**Date:** 2026-09-25  
**Validator:** Code Audit Verification  
**Original Report:** /home/user/twobits/docs/agent-audit-2026-09-25/12-pricedrop-todos.md

---

## Claim Verification Results

### Claim 1: LocalAskSession - Engine lifetime unverified

**Status:** ✅ CONFIRMED

**Original Citation:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/ask/LocalAskSession.kt:25-29`

**Verification:** Read source file. Lines 25-29 contain the exact comment cited:

```kotlin
* NOT verified against sustained/idle use: whether [LiteRtLmEngine]/its underlying `Engine`
* are safe to hold open across a longer-lived, possibly-idle Ask session (rather than the
* short-lived construct-use-close pattern every other caller in this codebase follows) hasn't
* been tested on a real device. If it isn't, callers should still degrade gracefully since
* [send] surfaces failures as a normal thrown exception.
```

The audit's claim that "Long-lived LiteRT-LM engine is not verified for sustained/idle use" is explicitly documented in the code comment. This is not the auditor's inference — it is an explicit statement in the code itself.

---

### Claim 2: Broad coupon aggregation remains experimental

**Status:** ✅ CONFIRMED

**Original Citation:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/AiFeature.kt:72`

**Verification:** Read source file. Line 72 contains the COUPON feature definition with:

```kotlin
callNote = "Uses provider offer metadata; broad coupon aggregation remains experimental",
```

The exact phrase "broad coupon aggregation remains experimental" appears in the callNote field for the COUPON feature. The audit's claim that this is "explicitly flagged as experimental in documentation" is accurate.

---

### Claim 3: PromotionProvider interface defined but never used

**Status:** ✅ CONFIRMED

**Original Citation:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/registry/ProviderRegistry.kt:54`

**Verification:** 

1. **Interface Definition:** PromotionProvider interface is defined in `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/contracts/ProviderContracts.kt` (lines 67-71).

2. **Empty Registry Method:** Read ProviderRegistry.kt line 54:
   ```kotlin
   override suspend fun promotionProviders(): List<PromotionProvider> = emptyList()
   ```
   The method always returns an empty list.

3. **No Implementations Found:** Grep search for all implementations of PromotionProvider in the entire apps/price-drop module returned 0 results. No class in the codebase implements this interface.

4. **No Production Calls:** Grep for `promotionProviders()` found only 2 locations:
   - ProviderRegistry.kt (the definition and empty implementation)
   - ProductDiscoveryCoordinatorTest.kt (test mock setup that returns emptyList())
   
   The method is not called in any production code.

**Conclusion:** The PromotionProvider interface is completely unused in the codebase. It exists as a defined contract, but no provider implements it and no production code calls the registry method. The audit's claim that "the interface is never called anywhere in codebase" is accurate based on comprehensive grep analysis.

---

## Summary Statistics

- **Total Claims Verified:** 3
- **Confirmed:** 3 ✅
- **Corrected:** 0
- **False/Unverifiable:** 0

---

## Verified findings for final report

### 1. LocalAskSession - Engine lifetime unverified
- **File:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/ask/LocalAskSession.kt:25-29`
- **Description:** Long-lived LiteRT-LM engine is explicitly documented as NOT verified for sustained/idle use. The code comment states that "whether [LiteRtLmEngine]/its underlying Engine are safe to hold open across a longer-lived, possibly-idle Ask session hasn't been tested on a real device."
- **Effort:** Medium | **Impact:** User-facing risk - app could crash or become unresponsive on extended Ask sessions
- **Status:** Confirmed - explicit in code documentation

### 2. Broad coupon aggregation remains experimental
- **File:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/AiFeature.kt:72`
- **Description:** The COUPON feature's callNote field explicitly states "broad coupon aggregation remains experimental". This indicates coupon detection/aggregation is not production-ready.
- **Effort:** Medium | **Impact:** User-facing - users may see incomplete or unreliable coupon data
- **Status:** Confirmed - exact text present in code

### 3. PromotionProvider interface defined but never implemented or called
- **File:** 
  - Interface Definition: `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/contracts/ProviderContracts.kt:67-71`
  - Registry Method: `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/registry/ProviderRegistry.kt:54`
- **Description:** PromotionProvider interface exists in the provider architecture but promotionProviders() always returns an empty list. Comprehensive codebase grep confirms: (a) no class implements this interface, (b) the registry method is never called in production code, (c) the method only appears in test mocks returning empty lists.
- **Effort:** Medium | **Impact:** Internal debt - unused interface creates code surface area and confusion
- **Status:** Confirmed - verified via grep of entire apps/price-drop module

---

## Validation Methodology

All three claims were verified against actual source code:
1. Direct file reading at cited line ranges
2. Comprehensive grep searches for related references (promotionProviders(), PromotionProvider implementations)
3. No assumptions made — only verified facts reported

All findings in the original audit report are accurate and supported by concrete evidence in the codebase.
