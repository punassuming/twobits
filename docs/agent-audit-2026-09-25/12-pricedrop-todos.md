# PriceDrop App - Unimplemented & Partial Capabilities Audit

**Date:** 2026-09-25  
**App:** PriceDrop (price-tracking + shopping-assistant)  
**Scope:** `/home/user/twobits/apps/price-drop/src/main/**/*.kt`

---

## Findings by Feature Area

### Ask/Chat Assistant

**LocalAskSession - Engine lifetime unverified**
- **File:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/ask/LocalAskSession.kt:25-29`
- **Description:** Long-lived LiteRT-LM engine is not verified for sustained/idle use. Documented as unverified whether holding the engine open across a longer-lived, possibly-idle Ask session is safe.
- **Effort:** Medium | **Impact:** User-facing risk - app could crash or become unresponsive on extended Ask sessions
- **Details:** The comment explicitly states "NOT verified against sustained/idle use: whether [LiteRtLmEngine]/its underlying `Engine` are safe to hold open across a longer-lived, possibly-idle Ask session (rather than the short-lived construct-use-close pattern every other caller in this codebase follows) hasn't been tested on a real device."

### Coupons/Promotions

**Broad coupon aggregation remains experimental**
- **File:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/AiFeature.kt:72`
- **Description:** Coupon detection feature is marked as experimental. Functionality exists but may have limitations or reliability issues.
- **Effort:** Medium | **Impact:** User-facing - users may see incomplete or unreliable coupon data
- **Details:** AiFeature.COUPON's callNote explicitly states "broad coupon aggregation remains experimental". This indicates the coupon detection/aggregation across retailers is not production-ready.

**PromotionProvider interface defined but not implemented**
- **File:** `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/data/provider/registry/ProviderRegistry.kt:54`
- **Description:** PromotionProvider interface exists and is part of the provider architecture, but promotionProviders() always returns an empty list. The interface is never called anywhere in the codebase.
- **Effort:** Medium | **Impact:** Internal debt - feature architecture is incomplete
- **Details:** The PromotionProvider interface is defined in ProviderContracts but the registry method that should provide instances always returns emptyList(). Promotions are currently handled via offers or manual codes (see ProviderSettingsStore.migrateCouponProvider() line 109).

---

## Top 5 Priorities

### 1. LocalAskSession - Engine lifetime unverified (MEDIUM EFFORT, HIGH USER RISK)
**Why it matters:** Users could experience crashes or app unresponsiveness during extended Ask conversations. This is explicitly documented as untested on real devices.
**Action:** Test long-lived engine behavior on real devices; either verify safety or redesign to construct/destroy engines per conversation turn.

### 2. Broad coupon aggregation remains experimental (MEDIUM EFFORT, MEDIUM USER IMPACT)
**Why it matters:** Users may receive incomplete or unreliable coupon alerts, reducing the value of the app's core shopping-assistant feature.
**Action:** Expand provider coverage for coupon aggregation or document limitations more clearly in the UI.

### 3. PromotionProvider architecture incomplete (MEDIUM EFFORT, INTERNAL DEBT)
**Why it matters:** The provider architecture has a defined but unused interface that increases code surface area without benefit. This creates technical debt and confusion.
**Action:** Either implement promotion provider routing or remove the unused PromotionProvider interface and its registry method.

### 4. Page reader provider selection (SMALL EFFORT, LOW RISK)
**Why it matters:** Page reader choice is a dedicated preference separate from multi-select feature toggles (Jina vs Firecrawl). Implementation looks complete, but warrants verification that all five search/shopping providers (Jina, SearchAPI, Serper, Firecrawl, Rainforest) are correctly wired in all features.
**Action:** Verify all five providers are called correctly in search, shopping, and enrichment flows.

### 5. LocalAskSession engine state management (SMALL EFFORT, LOW-MEDIUM RISK)
**Why it matters:** The engine is held open across conversation turns for performance, but no lifecycle guarantees are documented if the app is backgrounded or the session left idle.
**Action:** Document the intended lifecycle or add safety timeouts to close idle engines.

---

## Summary

**Total unimplemented/partial features found:** 3 concrete issues + 2 risk areas  
**Most critical finding:** LocalAskSession long-lived engine not verified for sustained use  
**Technical debt:** PromotionProvider interface defined but never used  
**Experimental features:** Coupon aggregation flagged as experimental

