# Changelog

## Unreleased

### Features

### Improvements

**Notifications** — aligned appearance with design mockup:
* per-type accent color: below-target = green, coupon = yellow, big-drop = coral, provider error = red
* expanded notification body via BigTextStyle: full message + summary sub-text (e.g. "Down from $299.99")
* "Open item" action button visible in expanded notification shade
* coupon notifications include a "Copy CODE" action that writes the code to the clipboard
* `CouponCopyReceiver` registered in manifest to handle the clipboard action

* renamed app module directory from `apps/pricedrop` to `apps/price-drop` for naming consistency with the other apps

### Fixes

## 0.0.1 (2026-06-19)

### Features

**PriceDrop** — initial release:
* watch screen with live price watchlist, alert status badges, and coupon chips
* drops feed showing price drops, coupons found, and historical lows
* search screen for adding products by URL, keyword, or barcode
* barcode scan via device camera
* product detail with price history chart and multi-retailer offer comparison
* ask AI screen for shopping recommendations
* Pro tier and BYOK configuration
* onboarding flow for new users

### Improvements

**Live data** — wired the app to the TwoBits Worker (Pro passthrough):
* product search now calls the real `/v1/pricedrop/search` endpoint instead of placeholder results
* the Ask assistant now answers via the managed `/v1/chat/completions` proxy with a shopping-scoped prompt
* added a network layer (OkHttp + Gson) reusing `shared/network`, with friendly error mapping and bearer auth from the RevenueCat user id
* repository can now refresh current price, backfill Keepa price history, fetch coupons, and resolve scanned barcodes

**Tooling** — PriceDrop now has its own CI workflow (build + unit tests + Android lint on every PR) and a ktlint `.editorconfig`, matching Scrybe and Shelf Snap.

**Billing** — purchase/restore logic now runs through a shared `PurchaseDelegate` in the shared billing module, removing duplicated billing orchestration across the apps.

**Background price checks** — the app now actually tracks prices on its own:
* a periodic WorkManager job refreshes price + coupons for every active product, honoring the check-frequency, Wi-Fi-only, and charging-only settings
* it generates drops on target-hit and big-drop (≥ alert threshold) crossings and posts notifications, gated by the quiet-hours toggle (22:00–08:00 local)
* three notification channels (price drops / coupons / provider issues); tapping a drop opens the app
* requests the Android 13+ notification permission on launch
* the worker resolves its dependencies via a Hilt entry point, so no custom WorkerFactory is required

**Product detail** — surfaced the data the model already tracks:
* price-history chart now plots observed price, effective price (incl. shipping/fees), and the target line, with 7D/30D/90D/All range selectors and a current/low/avg/high metrics row
* added a coupons section with verification-state badges (Valid/Untested/Restricted/Expired), discount labels, and copy-to-clipboard
* added an activity timeline (added / checked / dropped / coupon found / alert sent) with relative timestamps
* the price overview now shows the effective price when shipping or fees apply
* toolbar refresh action pulls the latest price and coupons from the Worker on demand

**Data model** — expanded local schema for the full tracking experience:
* new `Offer`, `Coupon`, and `Activity` entities plus DAOs; `WatchedProduct` gains shipping/fees/seller/source/confidence and `PriceEvent` gains effective price
* added effective-price math (`item + shipping + fees − coupon`, never negative) with unit tests
* added a per-provider settings store (Off/BYOK/Pro modes + encrypted key storage) as the foundation for provider management

**AI Config** — per-provider routing and BYOK support:
* AI Config screen now shows a per-provider card (OpenAI, Web search, Shopping, Keepa, Coupons) with an Off / BYOK / Pro segmented toggle
* BYOK mode stores the user's API key and routes calls directly to the upstream provider; OpenAI BYOK bypasses the TwoBits proxy entirely
* Pro mode uses a fixed internally-managed model — not user-configurable

**Offer comparison** — multi-retailer price rows on Product Detail:
* `pdPrice` Worker handler now surfaces Rainforest's `sellers_results.listings` as an `offers[]` array alongside the buybox winner
* Product Detail refreshes competing-seller offers and shows them in an "Other sellers" section sorted by effective price (base + shipping)

**Tooling** — migrated to shared `gradle/libs.versions.toml` version catalog across all three apps; upgraded Compose BOM to 2024.12.01, coreKtx to 1.15.0, lifecycleRuntimeKtx to 2.8.7, and navigationCompose to 2.8.5

### Fixes

* barcode scan now resolves the scanned UPC through the Worker (`/v1/pricedrop/barcode`) and pre-fills the product before tracking, with a manual fallback when there is no catalog match — previously it returned a placeholder result after a fixed delay
* first launch now shows the onboarding flow exactly once, gated by a persisted `onboarding_complete` flag — previously the app always opened straight to the watchlist and the onboarding screens were unreachable
* Pro entitlement now checks `pricedrop_pro` (was defaulting to the shared `pro` entitlement)
* Pro upgrade CTA now initiates a real RevenueCat purchase (annual or monthly based on the selected plan) instead of a no-op; BYOK CTA routes to AI Config
* annual plan selection is now correctly passed through to the purchase flow — previously always purchased the monthly package regardless of selection
