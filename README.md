# TwoBits

[![Scrybe CI](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/scrybe-ci.yml)
[![Shelf Snap CI](https://github.com/punassuming/twobits/actions/workflows/shelf-snap-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/shelf-snap-ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.25-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/target%20sdk-35%20(Android%2015)-brightgreen.svg)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

TwoBits is a monorepo for two Android apps — **Scrybe** and **Shelf Snap** — backed by a shared billing library and a managed API key proxy. Both apps use AI to do useful things with your phone's microphone and camera.

| App | What it does |
|-----|-------------|
| [Scrybe](apps/scrybe/) | Voice recording → Whisper transcription → LLM transformation → structured notes |
| [Shelf Snap](apps/shelf-snap/) | Camera capture → GPT-4o vision analysis → inventory valuation + price research |

---

## Repository layout

```
apps/
  scrybe/          — Scrybe Android app (voice recording + AI transcription)
  shelf-snap/      — Shelf Snap Android app (camera inventory + price research)
shared/            — Gradle composite build: billing, common, api-keys, network, design
```

The `shared/` modules are consumed by both apps: billing (RevenueCat), common utilities, API-key management, networking, and design tokens (TwoBitsTypography, TwoBitsShapes, TwoBitsColors).

---

## API proxy

Both apps' Pro tiers route AI requests through a managed key proxy so the OpenAI key never reaches user devices:

```
Scrybe / Shelf Snap  (Pro tier)
  └─ Authorization: Bearer <RevenueCat User ID>
       └─ api.twobits.app  (punassuming/twobits-worker)
            ├─ RevenueCat  (subscription check, cached 5 min in KV)
            ├─ SpendTracker DO  (atomic per-user monthly spend cap)
            └─ OpenAI API  (key never leaves the proxy)
```

The worker lives in **[punassuming/twobits-worker](https://github.com/punassuming/twobits-worker)** and is deployed independently to Cloudflare Workers. Free / BYOK users send requests directly to `api.openai.com` with their own key — the worker is never involved.

---

## CI / CD

Each app has its own CI and release workflow. Both share `reusable-validate.yml` for changelog and manifest validation.

| Workflow | App | Trigger | What it does |
|----------|-----|---------|-------------|
| `scrybe-ci.yml` | Scrybe | Push/PR | Changelog validation → assembleDebug, tests, lint, ktlintCheck, detekt |
| `scrybe-release.yml` | Scrybe | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release |
| `shelf-snap-ci.yml` | Shelf Snap | Push/PR | Changelog validation → assembleDebug, tests, lintDebug |
| `shelf-snap-release.yml` | Shelf Snap | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release |
| `reusable-validate.yml` | Both | Called by CI | Shared changelog + manifest validation |
| `pages.yml` | — | Push to `main` | Deploy `docs/` to GitHub Pages |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) for developer setup, coding standards, and CI requirements.

Each app maintains its own changelog under `apps/<app>/CHANGELOG.md`. Update the relevant changelog before any commit destined for `main`.
