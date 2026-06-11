# Changelog

## Unreleased

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
