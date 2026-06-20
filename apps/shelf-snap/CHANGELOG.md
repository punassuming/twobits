# Changelog

## Unreleased

### Features

### Improvements

* `shared/network` now provides a `Json` (kotlinx.serialization) singleton in addition to `OkHttpClient`, consolidating serialization config alongside the HTTP client

### Fixes

* ktlint and detekt quality gates now run in CI and locally (pre-commit hook), matching Scrybe
* shared scripts (manage-changelog.py, validate-manifests.py) moved to repo-level scripts/ — release workflow paths updated
* CI and release workflows consolidated into reusable-build.yml and reusable-release.yml; per-app workflows are now thin callers
* shelf-snap-ci.yml build job inlined (reusable-build.yml doesn't exist on main yet; will re-wire once it lands)
* shared/network, shared/common, shared/billing, shared/api-keys build.gradle.kts now reference the shared version catalog (libs.*) instead of hardcoded version strings
* Gradle parallel/caching/configuration-cache flags enabled for faster builds
* shared/design Compose BOM updated to 2024.12.01 (was hardcoded 2024.06.00)

## 1.11.0 (2026-06-19)

### Features

### Improvements

* migrated to shared `gradle/libs.versions.toml` version catalog across all three apps; upgraded Compose BOM to 2024.12.01, coreKtx to 1.15.0, lifecycleRuntimeKtx to 2.8.7, and navigationCompose to 2.8.5

### Fixes


## 1.10.0 (2026-06-19)

### Features

**Pro managed API** — vision, pricing, and web search route through api.twobits.app for Pro subscribers:
* vision analysis and item identification call the Worker proxy (no OpenAI key required)
* price research LLM synthesis calls the Worker proxy
* web search for market research uses managed Jina AI via `/v1/shelfsnap/search` (no Jina/Brave key required)
* AI config web search section shows a managed info card instead of key fields when Pro is active

**Pro** — standalone subscription screen:
* tier comparison: Try it / Pro / BYOK side-by-side
* plan picker: annual ($4.99/mo) or monthly ($5.99/mo)
* usage dashboard when Pro is active (vision analyses, price searches, coupon lookups, listing generations)

**Market Research** — promoted to standalone full-screen view:
* access from the Market tab in Item Detail
* all price analysis, comparable listings, and citations

**Listing Summary** — promoted to standalone full-screen view:
* access from the List tab in Item Detail
* all platform listing management in one dedicated view

### Improvements

* purchase/restore logic now runs through a shared `PurchaseDelegate` in the shared billing module, removing duplicated billing orchestration across the apps

### Fixes

* plan picker now correctly passes the selected plan (Annual / Monthly) to the purchase flow — previously always initiated a monthly purchase regardless of selection
* Market and List tabs now switch in-place within the item detail screen sharing the same view model, so unsaved edits on the Details tab are preserved and suggested prices apply to the active form
* "Manage subscription" on the active Pro card now opens the Google Play subscriptions page instead of doing nothing



## 1.9.0 (2026-06-17)

### Features

**App Icon** — launcher refresh:
* updated the Android launcher icon to better reflect the app's intent

### Improvements

**Build configuration** — upgraded JVM target to 17:
* bumped JVM target and Java compatibility to 17 across the app and all shared modules to support modern Android libraries and Kotlin 2.0

**License** — dual-licensing setup:
* added standard GPLv3 license to both Scrybe and Shelf Snap apps to establish open source rights while preserving commercial/Pro distribution capability

### Fixes

* model serialization survives R8 minification — added keep rules for app and shared data models to prevent field stripping required by Gson
* `ProScreen` top bar extracted into private composable — satisfies ktlint function-body-expression rules without changing visible behaviour




## 1.8.0 (2026-06-12)

### Features

### Improvements

**Dual search API keys** — store Jina AI and Brave Search keys independently:
* Settings → AI Config now shows separate Save / Clear / Test panels for Jina AI Search and Brave Search regardless of which provider is selected
* switching providers no longer wipes the previously saved key — both are retained in DataStore
* existing `search_api_key` value migrated automatically to `jina_search_api_key` on first read
* Test button now available for both providers (Brave Search included)

**Listing integration** — platform-specific text, Share Sheet, Mark Sold, and tips:
* listing text is now formatted per-platform: eBay gets an Item Specifics block with brand/model/condition/size/color and a title capped at 80 characters; Mercari is casual with price at end; Facebook Marketplace puts the price at top with no hashtags; OfferUp uses a short title with bulleted condition notes; Craigslist uses a classic classified format with email footer
* Share button added next to "Copy listing text" — opens the Android Share Sheet with listing text and up to 3 item photos; uses the system chooser so the user can send directly to the eBay/Mercari app, Messages, email, etc.
* "Mark sold" button appears on each active listing row — tapping it flips the listing status to Sold and persists the change
* collapsible "Tips ▼" row added below each platform checkbox with 3–4 platform-specific listing tips (title length limits, tone guidance, pricing strategy)

**Market research search** — targeted queries and stricter evidence filtering:
* search queries now wrap brand + model in quotes for exact-phrase matching (e.g. `"IKEA Ektorp"`)
* added platform-specific queries: `site:ebay.com/itm`, `site:ebay.com sold`, and `mercari.com sold` before the generic fallback
* tag-augmented fallback query added when the item has keyword tags
* early-stop threshold raised from 3 → 5 results so more evidence is gathered per research run
* LLM synthesis prompt now instructs the model to ignore blog posts and buying guides — only snippets with a real price and sold transaction count; confidence capped at ≤ 30 when fewer than 3 real listings are present
* Jina AI Search requests now include `X-Return-Format: text` for cleaner content extraction from listing pages

**Re-analysis** — richer descriptions and inline model picker:
* vision prompt now requests a 3–5 sentence description covering condition, features, visible defects, material, and best use — replacing the previous one-liner
* tags expanded from 3–6 to 6–10 keywords including style, material, color, use case, and condition descriptor for better search matching
* image detail level raised from "low" to "auto" so the model sees full-resolution context when re-analysing
* model picker dropdown appears above the Re-analyze button (BYOK mode) — choose any available GPT-5 family model for a single analysis without changing your default in Settings

### Fixes

* photo viewer next/previous navigation compiles correctly — missing `mutableIntStateOf` import restored
* clearing the Jina AI Search key now takes effect immediately — previously an explicit clear saved an empty string that the migration fallback treated as absent, silently restoring the legacy key
* price research LLM prompt no longer fails to compile — `$XX.XX` example string now escapes the dollar sign correctly
* model picker dropdown compiles correctly — removed invalid import of `ExposedDropdownMenu` which is a scope-only composable accessed through `ExposedDropdownMenuBox`





## 1.7.0 (2026-06-11)

### Features

### Improvements

**Market research search** — platform-targeted queries for better comp evidence:
* price research now sends up to two platform-specific search queries (eBay sold listings, Mercari) before falling back to the generic query
* condition is included in the query string ("like new", "good condition", "used", "parts or repair")
* results from multiple queries are de-duplicated by URL and capped at 12
* LLM synthesis prompt now instructs the model to prefer snippets with a price and "sold", and to lower confidence to ≤ 40 when no actual marketplace listings are present in the evidence

### Fixes






## 1.6.0 (2026-06-11)

### Features

### Improvements

**Jina AI Search** — key setup and validation:
* settings show step-by-step instructions for creating a free Jina API key when no key is configured
* Test button validates the key against the live Jina AI Search service immediately
* Save / Clear / Test buttons replace the single Save button in the web search section
* test result (connected or error message) shown inline below the buttons

### Fixes







## 1.5.0 (2026-06-11)

### Features

**AI models** — GPT-5 and GPT-5.4 families:
* vision analysis models: GPT-5 (default), GPT-5 mini, GPT-5.4, GPT-5.4 mini
* pricing & description models: GPT-5 mini (default), GPT-5.4 nano, GPT-5.4 mini, GPT-5
* GPT-5-family requests routed through the OpenAI Responses API for vision and pricing

### Improvements

**Market research** — search status visibility:
* Market tab shows where each estimate came from: "Based on N web results via …", a search-failure notice, or AI-only
* search status persists with the item instead of resetting on app restart
* Jina AI search authenticates with an API key (free at jina.ai) — fixes silently empty results
* search provider and API key settings moved into the AI configuration screen

**What's New dialog** — structured release notes:
* update popup shows bold item titles with plain descriptions instead of raw flat bullets
* markdown code ticks and bold markers no longer leak into parsed release notes

### Fixes

* GPT-5 mini and nano price-research requests no longer sent to the Chat Completions endpoint they reject

* price research reads the Responses API message item instead of the first output entry, which is usually reasoning

* model selection rows clamp long subtitles to two lines so the cost label stays aligned

* changelog parser unit tests cover markdown backtick and bold-marker stripping

* missing FilterChip import in MarketTab restored after wildcard-import replacement








## 1.4.0 (2026-06-08)

### Improvements

**Settings** — privacy policy link in About section:
* new "Privacy policy" row opens the policy page in the browser

**AI configuration** — redesigned screen with shared design components:
* credentials panel now shows Save, Clear, and Test buttons side-by-side in the BYOK key field
* masked API key subtitle displayed in monospace when a key is set
* Pro/BYOK/Local source tabs added for Vision and Pricing sections
* local vision support: import Moondream 2 (.gguf) for on-device item identification
* local LLM support: import Gemma 3 1B or 4B (.gguf) for on-device descriptions and price estimates
* cloud model lists trimmed to the 3 models in the design (GPT-4o, GPT-4o mini, GPT-4.1 mini)
* Analysis section now shows AI condition detection, Auto price estimate, and Multi-photo analysis toggles

**Market research** — live web evidence:
* updated to use OpenAI Responses API for gpt-5.4 and gpt-5.4-mini models
* DuckDuckGo Instant Answer replaced with Jina AI Search for real pricing results
* Market tab shows a banner when prices are estimated from AI training data rather than live web search
* comparable listing rows and source citations are tappable links that open the source URL in the browser

### Fixes

* CI now builds assembleRelease so R8 minification runs on every PR, catching ProGuard stripping issues before they reach the release workflow

* CI sets android/verified commit status so branch protection can block merges when the build fails

* CHANGELOG asset now generated at build time — in-app "What's New" screen always reflects the latest release notes on a fresh clone

* What's New dialog now accumulates bullets from recent versions rather than showing only the most-recent hotfix section

* HTTP 404 from OpenAI pricing service now shows "Selected model isn't available" instead of a generic unavailable message








## 1.3.1 (2026-06-06)

### Improvements

* release workflow no longer fires on PR CI completions — `branches: [main]` filter added to `workflow_run` trigger so it only activates when CI runs against `main`








## 1.3.0 (2026-06-06)

### Improvements

* CI no longer fires duplicate runs — `push` trigger now restricted to `main` only; feature branches trigger CI exclusively via the `pull_request` event








## 1.2.0 (2026-06-05)

### Features

**Camera** — viewfinder redesign:
* close and flash controls overlaid directly on the viewfinder surface
* teal L-bracket corner guides frame the subject at each corner
* AI tip pill with contextual shooting hints
* dark bottom panel: white-ring shutter button, Analyse pill showing photo count, gallery thumbnail
* full-screen animated AnalysingView replaces the capture overlay with a 5-step progress indicator

**Settings** — AI configuration navigation card:
* prominent `primaryContainer` card at the top of the Settings screen
* links to API key, vision model selection, and Pro subscription settings
* subtitle: "Vision model · pricing · local models · API key"

**Inventory** — screen refresh:
* app name as TopAppBar title with Sort button action
* SummaryBanner between filter chips and list showing item count and total estimated value
* InventoryItemCard: category chip as title, brand/model as bodySmall, confidence badge right-aligned

### Improvements

**Item Detail** — visual polish:
* brand · model subtitle uses middle-dot separator (was space-separated)
* AI confidence badge replaced with a `primaryContainer` pill + "GPT-4o analysis" annotation

* fail release workflow before commit/tag if keystore secret is invalid — early `Validate keystore secret` step and `rebuild_for_tag` dispatch input added to Shelf Snap release workflow








## 1.1.3 (2026-06-05)

### Improvements

* fail release workflow before commit/tag if keystore secret is invalid — early `Validate keystore secret` step and `rebuild_for_tag` dispatch input added to Shelf Snap release workflow








## 1.1.2 (2026-06-04)

### Improvements

* add duplicate release prevention — both release workflows now use `has-new-unreleased-since-tag` to skip when all `## Unreleased` bullets are already present at the last tag








## 1.1.1 (2026-06-04)

### Improvements

* align settings page visual style — wrap each settings section in a card with icon + title header, matching the Scrybe settings design pattern; spacing standardised to 14dp between sections
* consolidate CI/CD — `shelf-snap-build.yml` renamed to `shelf-snap-ci.yml`; `shelf-snap-release.yml` and `shelf-snap-tag-release.yml` merged into single `shelf-snap-release.yml` with `workflow_run` trigger; version computation upgraded to `mathieudutour/github-tag-action` matching Scrybe; signing secrets standardised to `SIGNING_*` convention








## 1.1.0 (2026-06-04)

### Improvements

* align settings page visual style — wrap each settings section in a card with icon + title header, matching the Scrybe settings design pattern; spacing standardised to 14dp between sections
* consolidate CI/CD — `shelf-snap-build.yml` renamed to `shelf-snap-ci.yml`; `shelf-snap-release.yml` and `shelf-snap-tag-release.yml` merged into single `shelf-snap-release.yml` with `workflow_run` trigger; version computation upgraded to `mathieudutour/github-tag-action` matching Scrybe; signing secrets standardised to `SIGNING_*` convention








## 1.0.2 (2026-06-03)

### Features

* add vision model selector for BYOK users — choose from GPT-4o, GPT-4o mini, GPT-5.4, GPT-5.4 mini, or GPT-4.1 mini for item photo analysis; selection persists across sessions; Pro users use the managed API default








## 1.0.1 (2026-06-03)

### Features

* add Shelf-Snap product page to twobits GitHub Pages site — shelf-snap.html with phone mockup, "Snap. Analyze. List or donate." how-it-works steps, tabbed item detail (Details/Market/List), feature grid, cross-listing platform chips, market research price example, and Google Play store listing mockup








## 1.0.0 (2026-06-02)

_Maintenance release._








## 1.0.0 (2026-06-01)

### Features

* Shared `ItemThumb` puts each item's photo front-and-center with a category-specific icon fallback (clothing, appliances, games, furniture, books, electronics).
* Photo thumbnails on every inventory and donation-summary row.
* Item detail: numbered photo gallery with an Add photo slot, a color-coded condition selector, a market price-range bar with platform filter chips, and an AI listing-preview card.
* Camera: numbered photo strip, framing grid + reticle, capture flash, dynamic hint text, and a working flash toggle.
* Settings: auto-analyze-on-capture and keep-original-photos toggles, a storage breakdown, an about/version footer, and this What's new screen.
* Automated releases: merging to main auto-tags the next patch version, updates this changelog, and publishes a GitHub Release.

### Improvements

* Listed/Sold status pills now carry icons; inventory cards use a larger radius.
