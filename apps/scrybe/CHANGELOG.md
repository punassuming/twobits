# Changelog

## Unreleased

### Features

### Improvements

* add duplicate release prevention — `has-new-unreleased-since-tag` subcommand in `manage-changelog.py` compares current `## Unreleased` bullets against the last tag; both release workflows use this to skip releases when all bullets are already in a versioned section

### Fixes

## 1.6.0 (2026-06-04)

### Features

### Improvements

* consolidate CI/CD workflows — shared `reusable-validate.yml` for changelog and manifest validation; rename `android-ci.yml` → `scrybe-ci.yml` and `release.yml` → `scrybe-release.yml`; standardise signing secret names to `SIGNING_*` across both apps; add `workflow_dispatch` trigger to release workflows
* unify documentation — merge `CLAUDE.md` into `AGENTS.md` as the single authoritative agent instruction file; update `README.md` to describe the TwoBits monorepo with both apps and the worker; fix stale `android-whispering` path references in `CONTRIBUTING.md`
* standardise changelog location — move Scrybe changelog from repo root to `apps/scrybe/CHANGELOG.md` (matching Shelf Snap's `apps/shelf-snap/CHANGELOG.md`); update all CI workflow, pre-commit hook, manage-changelog.py, AGENTS.md, copilot-instructions.md, and codex skill references to new path; add per-app changelog gate in pre-commit hook covering both apps; fix `app/build.gradle.kts` changelog asset path accordingly
* add per-app README files — create `apps/scrybe/README.md` with full feature table, recording modes, AI provider tiers, architecture diagram, module map, tech stack, and CI docs; expand root `README.md` into a TwoBits project overview covering design philosophy, privacy commitments, shared infrastructure, and monorepo layout; update `apps/shelf-snap/README.md` CI section to reference current workflow names

### Fixes


## 1.5.0 (2026-06-04)

### Features

### Improvements

* consolidate CI/CD workflows — shared `reusable-validate.yml` for changelog and manifest validation; rename `android-ci.yml` → `scrybe-ci.yml` and `release.yml` → `scrybe-release.yml`; standardise signing secret names to `SIGNING_*` across both apps; add `workflow_dispatch` trigger to release workflows
* unify documentation — merge `CLAUDE.md` into `AGENTS.md` as the single authoritative agent instruction file; update `README.md` to describe the TwoBits monorepo with both apps and the worker; fix stale `android-whispering` path references in `CONTRIBUTING.md`

### Fixes



## 1.4.0 (2026-06-03)

### Features

* add vision model selector for BYOK users in Shelf Snap — free-tier users can choose from GPT-4o, GPT-4o mini, GPT-5.4, GPT-5.4 mini, or GPT-4.1 mini for item photo analysis; selection persists in DataStore; Pro users continue to use the worker default

### Improvements

* harden worker model validation — reject chat completions requests for any model not in the pricing table with HTTP 422 instead of silently falling back to gpt-5-mini pricing; prevents unexpected charges for newly added or expensive models
* serialize worker spend tracking with Durable Objects — replace KV read-modify-write spend accounting with a SpendTracker Durable Object that atomically reserves budget before forwarding to OpenAI and settles to actual cost afterward; eliminates the race condition where concurrent requests could each read the same KV total and bypass the monthly spend cap

### Fixes




## 1.3.0 (2026-06-03)

### Features

### Improvements

* annotate worker vision support — gpt-4o and gpt-4o-mini entries in the Cloudflare Worker pricing table now carry explicit vision notes; image tokens are counted inside prompt_tokens by OpenAI so no separate billing path is needed

### Fixes





## 1.2.0 (2026-06-03)

### Features

* add twobits GitHub Pages marketing site — four-page static site in docs/ (index.html, scrybe.html, shelf-snap.html, privacy.html) copied pixel-faithfully from the Claude Design handoff; shared site.css with full design token system (DM Sans, dark palette, signal/ember/brand color scheme); pages workflow deploys to GitHub Pages on push to main; all inter-page links use clean URL-friendly filenames; covers landing, Scrybe product + Play Store listing, Shelf-Snap product + Play Store listing, Privacy data tables + FAQ

* add ProGate composable to core:design — ModalBottomSheet paywall interceptor with two paths: “Go Pro” (purchase) and “Use your own API key” (navigate to settings); drop it anywhere an AI feature needs to be gated behind Pro or a BYOK key

### Improvements

* upgrade managed API proxy — rate-limited Cloudflare Worker now enforces a $2.00/month per-user spend cap tracked in KV; full GPT-5 and GPT-4.1 model pricing table (gpt-5-nano/mini/5/5.1/5.4/5.4-mini, gpt-4.1-nano/mini, gpt-4o/mini, whisper-1) synchronized with app model enums; vision calls via /v1/chat/completions billed correctly through prompt_tokens (image tiles counted server-side by OpenAI); streaming responses use include_usage injection to track token cost without buffering; spend keys auto-expire after 35 days

* fix Scrybe release stale check — release.yml now only considers itself stale when Scrybe-relevant files changed on main after the triggering commit; a concurrent shelf-snap version-bump push to main no longer suppresses a valid Scrybe release; push step retries with rebase to handle the concurrent-push race condition
* align shelf-snap CI validation with Scrybe — shelf-snap-build.yml now runs changelog and validate jobs before building (validate-manifests.py --root apps/shelf-snap, manage-changelog.py validate + check-updated against apps/shelf-snap/CHANGELOG.md); shelf-snap-tag-release.yml replaces inline Python changelog promotion with manage-changelog.py has-unreleased-bullets + promote-release (skips release when no bullets, matching Scrybe's pattern); validate-manifests.py gains a --root argument so both apps reuse the same script; android-ci.yml updated to pass --root apps/scrybe explicitly; CLAUDE.md documents the shelf-snap changelog requirement

* unify CI artifact output and triggers — android-ci.yml now uploads the Scrybe debug APK as a downloadable artifact and triggers on claude/** and copilot/** feature branches; shelf-snap-build.yml restructured to match Scrybe's single-Gradle-invocation pattern (lint + test + assembleDebug in one pass), drops redundant release APK build from CI (covered by shelf-snap-tag-release.yml on main), and reduces permissions to contents:read; all four shelf-snap workflows upgraded from setup-android@v3 to @v4 with explicit platform and build-tools package selection

* fix composite build AndroidX property — add shared/gradle.properties with android.useAndroidX=true so the shared library modules (billing, design, etc.) resolve RevenueCat and Compose dependencies correctly; add pipefail to shelf-snap CI Gradle steps so build failures propagate correctly
* fix SettingsScreen compile errors — add missing imports for CircularProgressIndicator, Spacer, and width used in the Pro subscription button; remove duplicate AutoAwesome import
* align shelf-snap AGP to 8.7.3 — composite builds require a single AGP version across all included builds; shelf-snap was on 8.4.0 which conflicts with shared/ modules pinned to 8.7.3
* fix BillingProviderModule KtLint violation — multiline BillingConfig(...) constructor call must start on a new line after the = operator per the multiline-expression-wrapping rule
* migrate both app themes to shared design tokens — scrybe and shelf-snap now use TwoBitsTypography (DM Sans) and TwoBitsShapes from core:design; local Type.kt and Shape.kt become single-line aliases so existing symbol references continue to compile
* add ThemeMode support to shelf-snap — ShelfSnapTheme now accepts ThemeMode (SYSTEM/LIGHT/DARK) from core:design, matching scrybe's existing dark-mode architecture; wiring a settings toggle reads ThemeMode from DataStore
* both apps now rebuild when shared/** changes — android-ci.yml and shelf-snap-build.yml path filters now include shared/** alongside their respective app paths; shelf-snap tag-release also triggers on shared changes
* align shelf-snap versionCode formula to scrybe — tag-release workflow now computes versionCode as major×1 000 000 + minor×1 000 + patch (e.g. 1.2.3 → 1002003) matching scrybe's formula; Play Store requires only monotonic increases so the jump from 2 is valid

* add Pro subscription tier — users can upgrade to Scrybe Pro ($1.99/month) via Google Play for managed OpenAI API access without requiring a personal API key; subscription status is surfaced at the top of the Settings screen
* add RevenueCat billing integration — core:billing module wraps RevenueCat Purchases SDK with a SubscriptionRepository and BillingManager providing subscription tier as a StateFlow; purchase, restore, and refresh flows are coroutine-based
* add core:common shared module — Result<T> sealed interface, ReleaseNotesParser, AppDispatchers qualifier and enum, and a Hilt DispatchersModule providing IO and Default coroutine dispatchers under com.twobits.common
* add core:api-keys shared module — ApiKeyProvider interface, KeystoreApiKeyProvider (DataStore-backed, keyed by ProviderType), ApiKeyValidator, ApiKeyRouter (routes BYOK vs. managed Pro keys via api.twobits.app), ProUserIdProvider interface, and a Hilt ApiKeysModule under com.twobits.apikeys
* add core:network shared module — OkHttpClientFactory (configurable logging, AI-tuned timeouts), HttpErrorMapper for user-friendly HTTP error strings, and a Hilt NetworkModule providing a singleton OkHttpClient under com.twobits.network
* add core:design shared module — DM Sans variable-font family, TwoBitsTypography (full Material 3 type scale), TwoBitsShapes (8/12/18/24/28dp radius scale), ThemeMode enum (SYSTEM/LIGHT/DARK), and shared Compose components: ApiKeyField, SubscriptionBanner, SettingsRow, ErrorCard, LoadingOverlay under com.twobits.design

### Improvements

* rebrand monorepo to Two Bits — Cloudflare worker renamed to twobits-proxy, custom domain api.twobits.app configured in wrangler.toml, worker setup docs updated with deployment URL
* migrate to monorepo — Shelf Snap app moved into apps/shelf-snap alongside apps/scrybe; all CI workflows unified under .github/workflows with per-app path filters; CLAUDE.md updated for monorepo layout
* rename app directory from apps/android-whispering to apps/scrybe for clarity within the monorepo
* release workflow now builds both APK and AAB (App Bundle) artifacts — AAB is attached to GitHub Releases for direct Google Play upload
* settings screen surfaces Pro subscription card at the top with upgrade, restore, and error dismissal actions

### Fixes

* consolidate billing into shared core/billing module — BillingManager, SubscriptionRepository, SubscriptionTier, BillingConfig, and PurchaseCancelledException live once in core/billing under com.twobits.billing; both apps reference it via Gradle composite build (includeBuild); per-app Hilt BillingProviderModule supplies the RevenueCat key
* fix BillingManager compile error — replace removed purchaseWith() with RevenueCat 8.x purchase(PurchaseParams, PurchaseCallback) API
