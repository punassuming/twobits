# Changelog

## Unreleased

### Features

### Improvements

* on-device model downloads (Gemma) now resume from where they left off instead of restarting from byte 0 after a dropped or stalled connection, and retry automatically with backoff — previously a stalled (not failed) connection could hang indefinitely with no retry ever triggering, and any interruption meant starting the multi-gigabyte download over from scratch
* model downloads now check available storage before starting, and old abandoned partial downloads are cleaned up automatically instead of silently taking up space with no way to notice them
* a failed model download now shows a Discard action alongside Retry, so a partial file you don't want to resume can be removed immediately instead of waiting for it to age out
* Gemma model download/state-tracking logic moved into a shared coordinator used by Scrybe and Shelf Snap too, replacing three near-identical copies (no visual change)

### Fixes

* an interrupted model download could previously be misreported as fully installed and ready to use, since only file existence was checked, not completeness

## 0.16.0 (2026-07-29)

### Features

**Ask assistant gets a Local mode** — a fourth option next to Off/BYOK/Pro:
* runs entirely on-device once you download the Gemma model, no key or subscription
* multi-turn conversation, same as the cloud path, just private and offline

### Improvements

* Shared: local-models gained a single-file download acquisition type, for Scrybe's Gemma fix (no visual change)
* fixed a missing Gradle dependency that broke the build after the Local Ask mode landed (no visual change)
* fixed a build error in the shared local-AI module — a nonexistent `.text` property on the model response (no visual change)
* bumped the repo's Kotlin/KSP toolchain to 2.3.0 everywhere — required to compile against the on-device engine's litertlm-android dependency, whose newer releases all ship Kotlin metadata older compilers can't read (no visual change)
* bumped Hilt to 2.58 — 2.51.1's Gradle plugin couldn't find KSP's task class once KSP moved to 2.3.0; 2.58 is the newest release that still supports this repo's AGP 8.7.3 (2.59+ requires AGP 9) (no visual change)
* bumped Room to 2.8.4 — 2.6.1 predates Room's KSP2 support and crashed under KSP 2.3.0 (no visual change)

### Fixes


## 0.15.0 (2026-07-23)

### Features

### Improvements

**Screen margins** — content now sits closer to the screen edges:
* the outer margin tightened from 16dp to 12dp across every screen for more usable width

### Fixes



## 0.14.2 (2026-07-22)

### Features

### Improvements

### Fixes

**Watchlist** — products now show again below the filter chips:
* the chip row could expand to fill the screen and push the list off-screen




## 0.14.1 (2026-07-21)

### Features

### Improvements

### Fixes

**Ask** — its own Source setting now actually controls it:
* the two features shared one provider setting; each now has its own
* choosing BYOK or Pro for Ask now routes its calls accordingly, not Product search's setting




## 0.14.0 (2026-07-19)

### Features

### Improvements

**Calmer first run** — permissions and popups now wait their turn:
* notification permission is asked on the watchlist, not at app start
* the welcome popup no longer appears right after onboarding

**Watchlist** — a clearer start and no dead filters:
* empty watchlist gains an "Add your first product" button
* filter chips are hidden until something is watched
* a filter with no matches offers a "Show all" reset

**Filter and suggestion chips** — rows fade at the edges instead of clipping:
* watch filters and Ask AI suggestions signal offscreen options

**Product detail** — the price now states its verdict against your target:
* a "$X above/below your target" line appears under the price
* Buy now is demoted to an outline while above target

### Fixes

**Watch cards** — clearer freshness wording:
* "Never checked" is now "Not checked yet — tap to check now"

**AI configuration** — Source now controls what actually runs:
* Price checking and Ask now share their real provider's Off/BYOK/Pro state
* turning Ask off now actually stops it, instead of quietly still answering
* Ask's web-search toggle and Product search's model picker now take effect
* Promotions and Drop detection no longer show a Source picker they don't use

**Background price checks** — Wi-Fi-only and charging-only now apply right away:
* previously a mid-session change waited for a restart or a frequency edit





## 0.13.0 (2026-07-14)

### Features

**Product discovery** — searches now combine structured offers from enabled providers:
* Serper is the primary broad Google Shopping source
* SearchAPI can run alongside Serper for additional coverage
* matches include identifiers, confidence evidence, and variant conflicts

**Promotion codes** — product details now accept manually entered savings codes:
* embedded offer promotions show their applicability confidence
* unverified codes never reduce the displayed effective price

### Improvements

**Price history** — tracked observations are now provider-neutral and append-only:
* local observations remain authoritative across refreshes
* imported Amazon history retains explicit external provenance
* existing watchlists and price events migrate without data loss

**Provider setup** — search credentials now reflect their actual roles and costs:
* Rainforest is optional Amazon enrichment, not a core requirement
* broad coupon aggregation remains experimental and is not advertised

### Fixes

* Removed obsolete Couponlayer and LinkMyDeals credential routing







## 0.12.3 (2026-07-13)

### Features

### Improvements

### Fixes

**Screen transitions** — fixed a white flash on the edges during navigation:
* backgrounds now stay themed throughout the slide animation
* most noticeable previously in dark mode








## 0.12.2 (2026-07-13)

### Features

### Improvements

**Screen transitions** — navigation now matches Scrybe's directional slide motion:
* forward screens slide in from the right
* back navigation returns toward the left

* Codex and Claude now share deterministic local emulator navigation and visual regression tooling (no visual change)

### Fixes









## 0.12.1 (2026-07-12)

### Features

### Improvements

### Fixes

**AI configuration** — fixed a layout bug, decluttered provider rows, added missing-key warnings:
* fixed the Recommended/Required tag wrapping into vertical letters
* provider rows show a compact colored dot instead of a text tag
* full descriptions, setup steps, and signup links moved to an info sheet (tap ⓘ)
* the feature list now flags BYOK features that have no saved key










## 0.12.0 (2026-07-11)

### Features

### Improvements

**Settings & AI configuration card style** — the Settings screen's "Tracking" and "Privacy" cards and the AI configuration screen's "Credentials" card now use the same shared card components Scrybe and Shelf Snap use — Settings cards gain the correct rounded corners (they were missing an explicit shape before), and the Credentials card switches from a flat card to the same elevated-card style as the rest of AI configuration in all three apps, a visible but intentional style change
**Internal: shared empty-state component** — the empty watchlist state now uses a shared `AppEmptyState` component instead of a private implementation, matching Scrybe's task-list empty state

**BYOK clarity** — AI configuration now shows what's required vs optional:
* credential rows tag each key Required, Recommended, or Optional
* provider and feature descriptions rewritten to match real behavior

**Serper.dev search** — a cheaper alternative to SearchAPI.io:
* new BYOK shopping search provider with real price data
* keyword search now tries SearchAPI.io, then Serper, then Jina

### Fixes

**Price history** — now works, backed by Rainforest instead of Keepa:
* dropped the Keepa BYOK option; Rainforest covers history in both modes
* new watchlist items backfill their price history automatically

**BYOK with no key** — a clear message instead of a raw failure:
* switching a provider to BYOK without a saved key now explains what to do

**What's New — bold formatting** — item titles in the full What's New screen (Settings → What's New) now render bold, matching the automatic popup — previously only the popup applied bold weight to item titles, so the two "unified" surfaces looked inconsistent
**Internal: What's New last-seen key** — standardized on the same DataStore key name/type Scrybe and Shelf Snap use, with a one-time migration from the old key so existing users don't see a spurious re-prompt of the popup











## 0.11.0 (2026-07-03)

### Features

**What's New popup** — PriceDrop now shows an automatic popup after an update, matching Scrybe and Shelf Snap (previously PriceDrop had no automatic update notice at all — only the Settings → What's New screen)

### Improvements

**Settings screen** — the Pro card and About/What's New/Privacy section now use shared design-system components, matching Scrybe and Shelf Snap — "Upgrade" now purchases inline from Settings instead of only navigating to the Pro screen, and a "Restore purchases" option is now available there too
**Internal: check-frequency default** — fixed a fallback-value inconsistency where the default was `24` in one place (`SettingsPrefs.DEFAULT_CHECK_FREQ_HOURS`, used at app boot) but hardcoded to `6` in two others (the Settings screen's initial state and its DataStore-read fallback) — all three now reference the same constant

### Fixes

**Changelog asset** — fixed a build bug where it was never bundled (the Gradle task pointed at a repo-root `CHANGELOG.md` that no longer exists) — the "What's New" screen was silently rendering empty
**Internal: credential card** — fixed a shared bug where the "Connected" badge was tied to session-only validation state instead of whether a key is saved; PriceDrop's own credential flow already re-validates on every save so this was latent here, but the fix removes the risk of a saved-but-not-yet-tested key showing as "Not configured"
**Check-frequency slider** — no longer allows hourly (or sub-4-hour) polling — the range is now 4–96 hours; a previously saved value outside that range is transparently clamped on next read instead of being scheduled as-is












## 0.10.0 (2026-07-02)

### Features

### Improvements

* internal: introduced the shared `pro` core module (managed-Pro policy contracts) that PriceDrop will consume to unify Pro handling across TwoBits apps (no behavior change yet)
* internal: extended the shared `ProTierCard` (badge / price note / accent / compact comparison layout) and added shared Pro usage, spend-cap, and BYOK-note cards for the upcoming unified Pro screen (no behavior change yet)
* the Pro screen now states the managed monthly usage caps up front — Pro is metered, not unlimited. The plan comparison no longer says "Unlimited products / Hourly price checks"; it now shows the real monthly AI Ask allowance, a managed spend-cap card, and the actual per-feature allowances instead of placeholder usage numbers
* the BYOK note now uses the shared wording making clear your key is used directly from the device and never routes through TwoBits managed infrastructure or your Pro allowance
* AI configuration's "Ask assistant" feature now shows the real managed Pro allowance (e.g. "Up to 100 questions included with Pro each month") instead of a generic usage note, sourced from the same policy the Pro screen and worker enforcement share

### Fixes













## 0.9.0 (2026-06-28)

### Features

### Improvements

### Fixes

* BYOK Google Shopping: search and key verification now actually call SearchAPI.io — the earlier provider rename left the search request and the Test/Save validator pointed at SerpAPI, so newly entered SearchAPI.io keys were rejected and searches failed
* the Google Shopping key is now stored and shared across TwoBits apps under the "searchapi" identity (was "serpapi"), completing the SearchAPI.io migration
* Free product cap now refreshes subscription status on a cold start, so a returning PriceDrop Pro subscriber isn't temporarily limited to 3 products before opening Settings
* BYOK Google Shopping results are now parsed from SearchAPI.io's actual response shape (shopping_results + popular_products, seller / product_link), so common queries no longer return empty or save products with a blank retailer/URL
* AI Config: a provider key that previously passed verification now shows "Connected" on launch — the verified state is persisted, so you no longer have to expand, save, and test each key every time you open the app














## 0.8.0 (2026-06-27)

### Features

**Free plan product limit:**
* the free plan now tracks up to 3 active products at once; pausing or removing a product frees a slot
* PriceDrop Pro removes the limit entirely — track unlimited products
* attempting to add past the limit shows a prompt to remove a product or upgrade

### Improvements

**Providers — SearchAPI.io replaces SerpAPI for Google Shopping search:**
* BYOK Google Shopping provider updated to SearchAPI.io (~6× cheaper at low volume: ~$4/1k vs $25/1k searches)
* existing BYOK SerpAPI keys will stop working — re-enter a SearchAPI.io key in AI Config
* Pro managed search endpoint updated automatically (no user action needed)
* price history (Pro) now sourced from Rainforest instead of Keepa — eliminates a separate Keepa subscription requirement; BYOK Keepa still supported for existing subscribers

**AI Config — BYOK cost transparency:**
* each provider key field now shows an estimated cost note (e.g. "~$0.004 per search", "free tier covers hundreds of searches") so you know what bring-your-own-key usage will cost before enabling it

**Background tracking — reduced API cost:**
* default background check interval changed from 6 hours to 24 hours — 4× fewer price calls per product
* coupon checks are now throttled to at most once every 72 hours per product (were checked every background run)
* changing the check frequency in Settings takes effect immediately without requiring an app restart

**AI Config — credential verification** — Test and Save now make a live call to confirm the key works:
* OpenAI, Jina AI, SearchAPI.io, Keepa, Couponlayer, and Rainforest keys are each verified against their provider's API (not just checked for valid format)
* a "Checking connection…" message is shown while the verification is in progress
* Keepa connection shows remaining token count on success

### Fixes

* Ask AI (Pro): chat requests now identify the app to the managed proxy, fixing a "Pro subscription required" error for PriceDrop Pro subscribers (the shared OpenAI proxy was checking the wrong entitlement)
* Ask AI and URL product extraction (Pro): the model is now chosen by the managed service, so we can tune quality/cost without an app update; BYOK still uses your selected model
* AI Config: Keepa sign-up link now opens the correct Keepa API subscription page















## 0.7.0 (2026-06-26)

### Features

* **Jina AI BYOK** — web search and page reading now call Jina AI directly when WEB_SEARCH is in BYOK mode: text product search uses Jina as fallback when SerpAPI is not configured; the Ask assistant fetches live web context before generating answers; adding a product by URL reads the page via Jina reader and extracts the title and price automatically

### Improvements

### Fixes

* **URL product metadata** — when adding a product by URL the extracted title and current price (from Jina reader + OpenAI) are now saved to the watchlist; previously the product was always stored with the placeholder title "Product from URL" and price $0.00
* **Export data** — "Export data" in Settings → Privacy now shares the full watchlist as a JSON file via the system share sheet; previously the button was a no-op
* **Build version** — `versionCode` and `versionName` in `build.gradle.kts` corrected to 0.6.0; automated release tooling mis-stamped 0.0.1 due to a tag-fetch race (now fixed in the release workflow)
















## 0.6.0 (2026-06-25)

### Features

* **Pro screen** — redesigned to match Scrybe and Shelf Snap: compact three-column tier comparison (Try it / Pro / BYOK), separate billing section with annual vs monthly plan cards, active plan card with Manage + Restore buttons, monthly usage card, Why Pro benefit list, and BYOK note with Configure keys link
* **Provider setup guides** — each provider credential row in AI Config now shows step-by-step setup instructions and a "Sign up" link when not yet configured (OpenAI, Jina AI, SerpAPI, Keepa, Couponlayer, Rainforest API)
* **Credential security + cross-app sharing** — all BYOK API keys (OpenAI, Jina/Web search, SerpAPI, Keepa, Couponlayer, Rainforest) are now encrypted at rest using AndroidKeyStore AES-256/GCM; all six keys auto-mirror to installed sibling TwoBits apps when saved and are read through from siblings on a local miss; credential DataStore excluded from Google Auto Backup

### Improvements

* **Credential bridge** — all keys managed by this app (OpenAI, Jina, SerpAPI, Keepa, Couponlayer, Rainforest) are covered by the bridge; future shared credential types are silently skipped if this app doesn't support them

### Fixes

* **Price drop notifications** — tapping an OS notification now opens the app directly to that product's detail screen; previously the app opened to the last-viewed screen with no navigation

















## 0.5.0 (2026-06-24)

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
* BYOK providers now call upstream APIs directly from the app (SerpAPI, Rainforest, Keepa, CouponLayer), matching the existing OpenAI BYOK pattern — the Worker is never in the BYOK path; price/barcode BYOK only activates when Rainforest is configured (SerpAPI is search-only)
* Price lookups for ASIN products now prefer Rainforest BYOK when configured, rather than always falling back to SerpAPI/Shopping
* Chat model selection now reads the user's AI Config model choice for the Ask feature; falls back to default Pro/BYOK model constants only when the user has not selected a model
* `ProviderSettingsStore` gains `getFeatureModel()` suspend getter (was missing — only the flow + setter existed); `isByok()` in `PriceDropApiClient` is now a suspend function to correctly call the suspend `getMode()` — fixes compile errors in CI


















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
* the price overview shows the effective price when shipping or fees apply
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
