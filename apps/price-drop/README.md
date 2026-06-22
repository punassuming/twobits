# PriceDrop — *never overpay again*

[![PriceDrop CI](https://github.com/punassuming/twobits/actions/workflows/pricedrop-ci.yml/badge.svg)](https://github.com/punassuming/twobits/actions/workflows/pricedrop-ci.yml)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

An Android app that monitors product prices, fires drop alerts when your target is hit, surfaces coupons, and answers shopping questions through an AI assistant.

---

## Features

| Feature | Description |
|---------|-------------|
| **Watchlist** | Track products by name, URL, or barcode scan. Each product shows current price, target, and a trend delta vs. historical high. |
| **Price tracking** | Periodic background checks via WorkManager. Price events are stored locally — full history available in the price chart. |
| **Drop alerts** | Push notifications when a product hits your target price, crosses a big-drop threshold, or a coupon is discovered. Notification includes old/new price and coupon code with one-tap copy. |
| **Background checks** | PriceCheckWorker runs on a configurable schedule (default 6 h) with Wi-Fi-preferred constraints. Batched notifications group multiple simultaneous drops. |
| **Coupons** | Coupon codes are tested and validated; state (valid / expired / restricted) is shown per-code with discount amount. |
| **Barcode scan** | Point camera at any product barcode or QR code. ML Kit Barcode Scanning extracts the UPC/EAN; the app looks up the product title and current price automatically. |
| **AI shopping assistant** | Chat-based interface for price questions, deal hunting, and product comparisons. Supports BYOK (OpenAI key) and Pro managed API. Conversation history persists across restarts; full multi-turn context is sent with each message. |
| **Multi-provider support** | OpenAI for AI chat; Keepa / SerpAPI for price lookup; Jina AI for web search; CouponLayer for coupon discovery. All configurable per-provider. |
| **Onboarding** | Three-page first-run walkthrough. No account required — watchlist and history live only on the device. |

---

## Architecture

```
com.twobits.pricedrop
├── data/
│   ├── local/        Room database (PriceDropDatabase, WatchedProductDao, DropDao,
│   │                 CouponDao, PriceEventDao, OfferDao, ActivityDao, ChatMessageDao)
│   ├── model/        Domain models (WatchedProduct, Drop, Coupon, PriceEvent,
│   │                 Offer, Activity, AlertType, ChatMessageEntity)
│   ├── remote/       PriceDropApiClient (Worker proxy), PriceDropDtos, PriceParser,
│   │                 SearchHit
│   ├── repository/   WatchlistRepository, DropsRepository — single sources of truth
│   ├── provider/     PriceDropProvider, ProviderSettingsStore
│   └── settings/     SettingsPrefs (DataStore-backed)
├── di/               Hilt injection modules (AppModule, NetworkModule, BillingModule)
├── domain/           EffectivePrice — price + shipping + fees − coupon calculation
├── notifications/    PriceDropNotifier, CouponCopyReceiver
├── ui/
│   ├── ask/          AI chat (AskScreen, AskViewModel, ChatMessage)
│   ├── barcode/      Barcode scan (BarcodeScanScreen, BarcodeScanViewModel)
│   ├── drops/        Drop notification list (DropsScreen, DropsViewModel)
│   ├── navigation/   NavHost + Screen sealed class
│   ├── onboarding/   Three-page tutorial (OnboardingScreen, OnboardingViewModel)
│   ├── pro/          Subscription upsell (ProScreen)
│   ├── product/      Detail view with price chart, coupons, offers, activity
│   ├── search/       Product search + URL confirm (SearchScreen, SearchViewModel)
│   ├── settings/     Settings hub + AI config (SettingsScreen, AIConfigScreen)
│   ├── theme/        Material 3 colour scheme
│   ├── watch/        Watchlist home (WatchScreen, WatchViewModel)
│   └── whatsnew/     Changelog viewer
└── work/
    ├── PriceCheckWorker    Periodic price refresh + drop detection
    └── PriceCheckScheduler WorkManager schedule configuration
```

**Data flow:**

```
WatchScreen
  └─ WatchViewModel.watchlist (StateFlow)
       └─ WatchlistRepository.observeAll()
            └─ WatchedProductDao (Room)

PriceCheckWorker (WorkManager, ~6h)
  ├─ PriceDropApiClient → api.twobits.app (Pro) or direct provider
  ├─ WatchlistRepository.updatePrice()
  ├─ DropsRepository.insert(drop) when threshold crossed
  └─ PriceDropNotifier → NotificationManager
```

---

## Data model

| Entity | Key fields |
|--------|-----------|
| `WatchedProduct` | `id`, `title`, `currentPrice`, `targetPrice`, `alertType`, `alertThresholdPct`, `productUrl`, `asin`, `upc`, `imageUrl`, `trackedHigh`, `trackedLow`, `trackedAvg`, `lastCheckedAt`, `isActive` |
| `PriceEvent` | `id`, `productId`, `price`, `effectivePrice`, `retailer`, `recordedAt` |
| `Drop` | `id`, `productId`, `type` (`below_target` · `coupon` · `big_drop` · `historical_low`), `oldPrice`, `newPrice`, `couponCode`, `couponDiscount`, `detectedAt`, `isDismissed` |
| `Coupon` | `id`, `productId`, `code`, `description`, `discountType`, `discountValue`, `state` (`UNVERIFIED` · `TESTED_VALID` · `EXPIRED` · `RESTRICTED`), `source`, `store`, `expiresAt` |
| `Offer` | `id`, `productId`, `seller`, `sellerRating`, `price`, `shipping`, `fees`, `condition`, `availability` |
| `Activity` | `id`, `productId`, `type` (`ADDED` · `CHECKED` · `DROPPED` · `COUPON_FOUND` · `ALERT_SENT` · `OPENED`), `detail`, `timestamp` |
| `ChatMessageEntity` | `id` (autoGenerate), `role` (`user`/`assistant`), `content`, `timestamp` — persists the Ask AI conversation so context survives restarts |

`AlertType` enum: `BELOW_TARGET`, `PERCENTAGE_DROP`, `BIG_DROP`, `COUPON_FOUND`.

`EffectivePrice = listPrice + shipping + fees − couponDiscount`.

---

## AI provider model

| Provider | BYOK | Pro | Off |
|----------|------|-----|-----|
| **OpenAI** (AI chat) | Paste own key in Settings → AI Config — **fully functional** | Managed via `api.twobits.app` | Chat unavailable |
| **SerpAPI** (price search) | Routes to Pro (per-provider BYOK adapter not yet built) | Managed | Price search unavailable |
| **Keepa** (Amazon history) | Routes to Pro (per-provider BYOK adapter not yet built) | Managed | Amazon history unavailable |
| **Jina AI** (web search) | Routes to Pro (per-provider BYOK adapter not yet built) | Managed | Web context unavailable |
| **CouponLayer** (coupons) | Routes to Pro (per-provider BYOK adapter not yet built) | Managed | Coupon discovery unavailable |

**BYOK today:** Only OpenAI (AI chat) works in BYOK mode. The other providers accept key input in the UI but route through the Pro proxy until per-provider request/response adapters are implemented (`PriceDropApiClient` has the routing stub; set `ProviderMode.BYOK` to enable when adapters land).

Pro routes all requests through `api.twobits.app` (Cloudflare Worker). The entitlement ID is `pricedrop_pro`. A per-user monthly spend cap is enforced server-side via a Durable Object.

### Getting your OpenAI key (BYOK)

1. Go to [platform.openai.com/api-keys](https://platform.openai.com/api-keys) and sign in.
2. Click **Create new secret key** — copy the `sk-...` value immediately (shown only once).
3. In the app, open **Settings → AI Configuration → OpenAI → BYOK**, paste the key, and tap **Save**. A quick validation check confirms connectivity.

---

## Setup

1. Clone the repository and open `apps/price-drop/` in Android Studio Hedgehog or later.
2. Build → Run on a device or emulator with API 26+.
3. Complete the three-page onboarding.
4. Open **Settings → AI Configuration** and paste your OpenAI API key to enable the AI assistant, or subscribe to Pro for the managed API.
5. Tap **Add or ask** → **Search** to add your first product, or point the camera at a barcode.
6. Background price checks start automatically. You can adjust the check frequency in **Settings → Tracking**.

> **Privacy:** No account required. Your watchlist, price history, and coupons live only in the device's local Room database. Network calls are made only to fetch prices for products you are actively tracking, and to the AI provider when you use the chat. Nothing is sent to TwoBits servers unless you subscribe to Pro, in which case requests route through `api.twobits.app` — no personal data is retained beyond the session.

---

## Running tests

```bash
./gradlew :app:test
```

Unit tests cover `EffectivePrice` calculation and domain logic. Run from `apps/price-drop/`.

---

## CI / CD

| Workflow | Trigger | What it does |
|----------|---------|-------------|
| `pricedrop-ci.yml` | Push / PR | Changelog validation → assembleDebug, tests, ktlintCheck, detekt |
| `pricedrop-release.yml` | CI success on `main` | Version bump, changelog promotion, tag, GitHub Release with signed APK/AAB |

Download a debug artifact from the **Artifacts** section of a `pricedrop-ci.yml` run, then install with `adb install app-debug.apk`.

Release automation uses conventional commits. `## Unreleased` in `CHANGELOG.md` is promoted to a versioned section automatically — no manual version bumping.

### Release signing

| Secret | Description |
|--------|-------------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks`/`.keystore` file |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

When secrets are absent the release falls back to debug signing so the artifact is still installable.
