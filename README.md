# TwoBits — AI tools that respect your intelligence

[![Scrybe CI](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml)
[![Shelf Snap CI](https://github.com/punassuming/twobits/actions/workflows/shelf-snap-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/shelf-snap-ci.yml)
[![PriceDrop CI](https://github.com/punassuming/twobits/actions/workflows/pricedrop-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/pricedrop-ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.25-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

TwoBits is an independent software studio building small, focused Android utilities that use AI to save you time — without harvesting your data, locking you into a subscription, or making decisions you didn't ask for.

All three apps live in this monorepo. They share a design system, billing infrastructure, and a managed API key proxy — but are independently buildable and independently useful. You can read every line of code and verify every network call.

---

## Apps

| App | Tagline | What it does |
|-----|---------|-------------|
| [Scrybe](apps/scrybe/) | Voice to Document | Record any conversation → Whisper transcription → LLM transformation → structured notes. Seven recording modes shape AI output differently. On-device Whisper + Gemma or cloud GPT. BYOK or Pro ($1.99/mo). |
| [Shelf Snap](apps/shelf-snap/) | What you have, what you give | Snap a photo → GPT-4o vision analysis → inventory draft with category, condition, and estimated value → market research with cited sources → cross-list or donate. |
| [PriceDrop](apps/price-drop/) | Never overpay again | Add a product by name, URL, or barcode scan → background price tracking → drop alerts when your target is hit → AI shopping assistant → coupon discovery. BYOK or Pro ($2.99/mo). |

---

## The project

TwoBits started as a single app (Scrybe) and grew into a shared platform. The monorepo structure reflects what the apps actually share:

- **Design system** — `shared/design/` provides `TwoBitsTypography` (DM Sans), `TwoBitsShapes`, and a unified color palette across all three accent families (Signal blue, Glow teal, Ember orange) with Mist/Ink/Slate surfaces. All three apps use Material 3 with the same token layer.
- **Billing** — `shared/billing/` wraps RevenueCat. All three apps use the same Pro entitlement check and the same subscription status flow.
- **API key management** — `shared/api-keys/` provides a consistent encrypted-storage and validation pattern for OpenAI and other provider keys.
- **Networking** — `shared/network/` provides the configured OkHttp client and Retrofit setup consumed by all three apps.

The managed API key proxy (`api.twobits.app`, deployed from [punassuming/twobits-worker](https://github.com/punassuming/twobits-worker)) is a Cloudflare Worker that validates RevenueCat Pro subscriptions, enforces a per-user $2.00/month spend cap via a Durable Object, and routes requests to OpenAI — so the API key never reaches user devices.

---

## AI provider model

All three apps support multiple tiers:

| Tier | Description |
|------|-------------|
| **BYOK** | Paste your own OpenAI key. Requests go directly from your phone to OpenAI. You pay provider rates. |
| **Pro** | Requests route through `api.twobits.app`. Your key never touches your device. Per-app spend cap enforced atomically server-side (Scrybe: $1.99/mo · Shelf Snap: $1.99/mo · PriceDrop: $2.99/mo). |
| **Fully local** (Scrybe) | On-device Whisper (Sherpa-ONNX) for transcription; Gemma 2 2B (MediaPipe) for transforms. Zero network calls. |

Pro entitlement IDs: `scrybe_pro`, `shelfsnap_pro`, `pricedrop_pro`.

```
Android app (Pro tier)
  └─ Authorization: Bearer <RevenueCat User ID>
       └─ api.twobits.app  (punassuming/twobits-worker — Cloudflare Worker)
            ├─ RevenueCat API  ──► subscription check (cached 5 min in KV)
            ├─ SpendTracker DO ──► atomic per-user monthly spend gate
            └─ OpenAI API     ──► forward request, stream response back
```

Free / BYOK users send requests directly to `api.openai.com` — the worker is never involved.

---

## Privacy commitments

**Bring your own key** — AI processing uses your API key, billed directly to your provider. Or subscribe to Pro and we handle the key through the Cloudflare proxy.

**On-device AI** — Scrybe runs Whisper and Gemma locally. Shelf Snap stores everything in a local Room database. No cloud backend, no sync service.

**No tracking** — No analytics SDKs. No ad networks. No telemetry beyond anonymous Play Console crash reports.

---

## Repository layout

```
apps/
  scrybe/          — Scrybe Android app (multi-module Gradle project)
  shelf-snap/      — Shelf Snap Android app (single-module Gradle project)
  price-drop/      — PriceDrop Android app (single-module Gradle project)
shared/
  billing/         — RevenueCat billing wrapper
  common/          — Shared utilities (ScrybeSectionCard, result extensions)
  api-keys/        — Encrypted key storage and provider validation
  network/         — OkHttp + Retrofit setup
  design/          — TwoBitsTypography, TwoBitsShapes, color tokens
docs/              — GitHub Pages marketing site (twobits.app)
```

The worker lives in the separate **[punassuming/twobits-worker](https://github.com/punassuming/twobits-worker)** repository and deploys independently to Cloudflare Workers on push to `main`.

---

## Tech stack

Kotlin · Jetpack Compose · Hilt · Room · DataStore · OkHttp · Retrofit · Kotlinx Coroutines/Flow · Material 3. minSdk 26, targetSdk 35. MIT licensed.

Scrybe also uses Sherpa-ONNX (on-device Whisper) and MediaPipe (Gemma 2 2B).

PriceDrop also uses WorkManager (background price checks) and ML Kit Barcode Scanning (product lookup by barcode/QR).

---

## CI / CD

Each app has its own CI and release workflow. Both share `reusable-validate.yml` for changelog and manifest validation.

| Workflow | App | Trigger | What it does |
|----------|-----|---------|-------------|
| `scrybe-ci.yml` | Scrybe | Push/PR | Changelog validation → assembleDebug, tests, lint, ktlintCheck, detekt |
| `scrybe-release.yml` | Scrybe | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release with APK/AAB |
| `shelf-snap-ci.yml` | Shelf Snap | Push/PR | Changelog validation → assembleDebug, tests, lintDebug |
| `shelf-snap-release.yml` | Shelf Snap | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release with APK/AAB |
| `pricedrop-ci.yml` | PriceDrop | Push/PR | Changelog validation → assembleDebug, tests, ktlintCheck, detekt |
| `pricedrop-release.yml` | PriceDrop | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release with APK/AAB |
| `reusable-validate.yml` | All | Called by CI | Shared changelog + manifest validation logic |
| `pages.yml` | — | Push to `main` | Deploy `docs/` to GitHub Pages |

Release automation uses [conventional commits](https://www.conventionalcommits.org/). `## Unreleased` in each app's `CHANGELOG.md` is promoted automatically — no manual version bumping.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) for developer setup, coding standards, and CI requirements.

Each app maintains its own changelog:
- Scrybe: `apps/scrybe/CHANGELOG.md`
- Shelf Snap: `apps/shelf-snap/CHANGELOG.md`
- PriceDrop: `apps/price-drop/CHANGELOG.md`

Update the relevant changelog before any commit destined for `main`.
