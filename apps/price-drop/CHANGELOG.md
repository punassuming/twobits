# Changelog

## Unreleased

### Features

* **AI credentials — collapsible rows** — provider credential rows in the AI Config screen now collapse by default; connected rows show a masked key ("sk-••••••••1234") and a coral "Connected ✓" badge; unconfigured rows show the provider description and a gray "Not configured" badge; tapping expands to reveal the key field, Save, Test, and Clear
* **Rainforest API provider** — added as a new optional BYOK provider for Amazon product data (ASIN lookup + real-time price); included in Price check feature
* **Provider metadata** — each provider row now shows a description line below the name explaining what it does
* **Provider icon avatars in feature detail** — the PROVIDERS card in each feature's detail sheet now shows a 28dp icon avatar per provider (filled when key is set, muted when not) alongside the provider name, description, and key-status badge

### Improvements

* Renamed "Web search" → "Jina AI" and "Shopping search" → "SerpAPI" to match provider brand names
* A "BYOK · YOUR KEYS" sub-header now separates the Pro row from the BYOK provider rows in the Credentials card

### Fixes

* Release automation corrected from 0.0.1 → 0.5.0 (concurrent-release race caused tag action to start from 0.0.0)
* Non-OpenAI BYOK providers (SerpAPI, Keepa, Coupon, Rainforest) now correctly route through the Worker at `api.twobits.app` with the user's provider key as the Bearer token, instead of calling the provider's URL directly with an incompatible Worker path
* BYOK requests now send an `X-BYOK-Provider` header so the Worker can forward the user's key to the correct upstream provider and skip the RevenueCat subscription check
* Price lookups for ASIN products now prefer Rainforest BYOK when configured, rather than always falling back to SerpAPI/Shopping
* Chat model selection now reads the user's AI Config model choice for the Ask feature; falls back to default Pro/BYOK model constants only when the user has not selected a model

## 0.4.0 (2026-06-23)

### Features

**AI configuration — feature-oriented redesign:**
* the AI Configuration screen is now organized by feature (Product search, Price checking, Coupon discovery, Drop detection, Ask assistant) instead of a flat provider list
* each feature has its own source selector (Off / BYOK / Pro), provider toggles, and — where applicable — model choice, all persisted independently of the provider credentials that drive request routing
* a call-budget gauge shows the relative API-call weight of each feature plus an estimated total per event
* the per-provider credentials dock (OpenAI, Web search, Shopping, Keepa, Coupons) is preserved as a "Credentials" section with a Pro upgrade affordance

### Improvements

**Settings — redesigned layout:**
* a richer PriceDrop Pro banner at the top with an upgrade button (or an "Active" chip when subscribed) and a details link
* a dedicated AI configuration entry card (providers · models · API keys · call budget)
* Tracking, Privacy, and About sections now use the shared gray uppercase section label (icon + ALL-CAPS text above the card) consistent with the design system spec

### Fixes

* Pro screen: the plan toggle "Monthly – $5.99/mo" chip no longer wraps to two lines — the Annual/Monthly chips now share the row evenly and keep their labels on one line
* AI configuration: provider status badges no longer wrap to two lines when a provider title is long (shared credential row fix)



## 0.3.0 (2026-06-22)

### Features

**Ask AI — persistent multi-turn chat:**
* conversation history now persists across app restarts (stored in Room `chat_messages` table)
* all previous turns are included in each API request so the assistant maintains context across multiple messages
* a "Clear chat" button in the top bar lets users start a fresh conversation

### Improvements

**Documentation** — provider setup and BYOK status:
* README: added "Getting your OpenAI key (BYOK)" step-by-step section; clarified that only OpenAI BYOK is functional today (other providers route to Pro until per-provider adapters land); updated data model table with `ChatMessageEntity`; updated architecture with `ChatMessageDao`

**AI Configuration** — credential save validation and consistent provider cards:
* each provider (OpenAI, Web search, Shopping, Keepa, Coupons) now shows Save, Test, and Clear actions with inline success/error feedback
* keys are format-validated on save and test — invalid keys are flagged instead of being silently accepted
* provider cards now use the shared design-system credential component for a consistent look across the suite

**Pro plans** — now uses the shared `ProTierCard` design-system component:
* Free / Pro / BYOK tier cards extracted to shared design module; visual output unchanged

**About** — version and privacy parity:
* About section now shows the app version plus a Privacy policy link, matching Shelf Snap and Scrybe

### Fixes

* restore missing `fillMaxWidth` import in `ProScreen` that caused a build failure after extracting `ProTierCard`




## 0.2.0 (2026-06-22)

### Features

**Watch** — redesigned filter chips and per-card actions:
* filter chips updated to All / Below target / Coupons / Needs check / Paused for clearer intent
* three-dot context menu on each card with Refresh, Pause/Resume, Open retailer, and Remove actions
* below-target banner shows savings amount when current price ≤ target
* last-checked timestamp footer with inline refresh spinner per card

**Drops** — section grouping and dismiss all:
* drops now grouped into Ready to buy · New coupons · Big drops · Historical lows sections
* section headers show a color-coded icon and drop count
* "Mark all done" button in the top bar dismisses all active drops at once

**Search** — alert type picker:
* confirm dialog now shows radio buttons to choose the alert type: Below target price / Any % drop / Coupon found
* selected alert type is persisted on the watched product

**Ask** — expanded suggestion chips:
* updated from 4 to 6 suggestion chips covering: find cheaper, good deal check, find coupons, compare, track until drop, free shipping filter

**Product detail** — Buy now and tracking rules:
* "Buy now" button opens the retailer URL directly in the browser
* "Tracking rules" card shows target price (with Edit link), alert type, and Pause/Resume toggle

**Settings** — privacy section:
* new Privacy section with local-first storage description, clear search history action, and export data action

### Improvements

* added `apps/price-drop/README.md` with features table, architecture diagram, data model, AI provider tiers, setup guide, and CI/CD section

### Fixes





## 0.1.0 (2026-06-21)

### Features

### Improvements

### Fixes

* ktlint formatting fixes across source files (trailing commas, annotation placement, multiline expressions, blank lines)






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
