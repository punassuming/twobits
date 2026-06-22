# shelf-snap — *what you have, what you give*

An Android app that helps users document, value, and export a donation inventory using on-device camera capture and AI-powered item analysis.

---

## Features

| Story | Description |
|---|---|
| **Photo Capture** | Capture one or more photos per item using CameraX. Thumbnails appear in a scrollable strip; individual photos can be removed before analysis. |
| **Draft Item Extraction** | Photos are sent to the OpenAI Vision API (GPT-5 by default; configurable in Settings). The API returns `category`, `description`, `condition`, `estimatedValue`, and a `confidencePercent` as structured JSON, which is saved as a draft record. |
| **Inventory Review & Editing** | Tabbed item detail (Details / Market / List). The **Details** tab edits every AI-proposed field plus extended attributes (brand, model, size, color, quantity, original price, tags). Inventory supports search (category, brand, or description), filter chips (All / Listed / Unlisted / Drafts), inline delete, and a paginated photo viewer. |
| **Valuation Suggestion** | Estimated values are always labeled as *estimates* in the UI and CSV export — never presented as authoritative. Confidence scores from the model are surfaced as a badge. |
| **Market Research & Pricing** | The **Market** tab researches resale value via OpenAI inference plus an optional web search (Jina AI or Brave Search). It surfaces average sold price, a price range, per-platform suggested prices, comparable listings, and **cited sources** — estimates always show where they came from. |
| **Cross-Listing** | The **List** tab lets users select selling platforms (eBay, Mercari, OfferUp, FB Marketplace, Craigslist) and record listings at platform-adjusted prices. Listed/Sold status and platform badges appear back on the inventory cards. |
| **Donation Summary & Export** | Summary screen shows a per-item subtotal list and the total estimated donation value. A one-tap CSV export creates a file in the device's external storage, shareable via the system chooser. |

---

## Architecture

```
com.shelfsnap.app
├── data/
│   ├── local/        Room database (ItemEntity, ItemDao, Converters)
│   ├── model/        Domain models (Item, Condition, Platform, MarketData)
│   ├── remote/       OpenAI clients: VisionAnalysisService (item analysis),
│   │   │             PriceResearchService (pricing); search/ web-search backends
│   │   └── search/   WebSearchService + Brave/DuckDuckGo + resolver
│   └── repository/   ItemRepository — single source of truth
├── di/               Hilt dependency injection module
├── ui/
│   ├── camera/       Photo capture + analysis trigger (multi-photo)
│   ├── inventory/    Searchable + filterable list, listing badges, delete
│   ├── itemdetail/   Tabbed detail: Details / Market / List (cross-listing)
│   ├── summary/      Valuation summary + CSV export/share
│   ├── settings/     OpenAI + web-search provider/key (stored in DataStore)
│   ├── components/   Shared UI (PlatformBadge, platform colors/icons)
│   ├── navigation/   Compose Navigation graph
│   └── theme/        Material 3 colour scheme
└── util/
    ├── CsvExporter   Generates donation CSV with estimate labels
    └── ImageUtils    Camera file helper
```

**Tech stack:** Kotlin · Jetpack Compose · CameraX · Room · Hilt · DataStore · OkHttp · Coil · Accompanist Permissions

---

## Setup

1. Clone the repository and open in Android Studio Hedgehog or later.
2. Build → Run on a device or emulator with API 26+.
3. Open **Settings → AI Configuration** and choose a provider mode for OpenAI (required for both photo analysis and price research):
   - **BYOK** — paste your own [OpenAI API key](https://platform.openai.com/api-keys). You pay OpenAI directly. Vision analysis uses your selected model (default: GPT-5); price research uses a fast model.
   - **Shelf Snap Pro** — subscribe for managed API access. No key required.
4. *(Optional)* In **Settings → AI Configuration → Web search**, choose a search provider to improve price research accuracy:
   - **Jina AI** — paste a [Jina AI API key](https://jina.ai/). Free tier available; grounding real-time web results.
   - **Brave Search** — paste a [Brave Search API](https://api-dashboard.search.brave.com/) subscription token.
   - **None (AI only)** — pricing relies on the model's training data alone, no web calls made.
5. Tap **+** to start capturing donation items, then open an item and use the **Market** tab to research a price and the **List** tab to cross-list it.

> **Privacy:** Your API keys are stored only in the device's local DataStore. Photos are sent to OpenAI for analysis; item attributes and any web-search snippets are sent to OpenAI (and your chosen search provider) only when you tap **Research price**. Nothing else leaves the device.

---

## Running tests

```bash
./gradlew :app:test
```

Pure-JVM unit tests cover the CSV exporter logic, domain model correctness, search
filtering, add/edit/delete total calculations, API-key validation, the OpenAI
error-message mapping, platform/listing logic, and the price-research error mapping.

---

## CI / CD

Every push to `main`, `copilot/**`, or `claude/**` (and every PR to `main`) runs
`shelf-snap-ci.yml`, which:

1. Validates changelog structure and manifest files.
2. Runs **unit tests** and **Android Lint** as gates.
3. Builds a debug APK — artifact `shelf-snap-debug-<sha>`.

Download the artifact from the **Artifacts** section of the workflow run in the GitHub Actions tab, then install it on a device:

```bash
adb install app-debug.apk
```

> **Note:** Workflow artifacts require a GitHub sign-in to download and expire after 14 days.

`shelf-snap-release.yml` runs automatically after a successful `shelf-snap-ci.yml` on `main`. It:

1. Computes the next semantic version from [conventional commits](https://www.conventionalcommits.org/) since the last tag.
2. Promotes `## Unreleased` in `CHANGELOG.md` to a dated version section.
3. Bumps `versionName`/`versionCode` in `build.gradle.kts`.
4. Creates a git tag and GitHub Release with the signed APK and AAB attached.

Release automation uses conventional commit prefixes: `feat:` bumps minor, `fix:`/`chore:` bump patch, `BREAKING CHANGE` bumps major. The `## Unreleased` section is promoted automatically — no manual version bumping.

### Release signing

The release APK is signed with a real keystore **when the following repository secrets
are set** (Settings → Secrets and variables → Actions). When they are absent (e.g. forked
PRs), the release build falls back to the debug signing key so the artifact is still
installable for testing.

| Secret | Description |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks`/`.keystore` file |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Generate a keystore and the base64 secret locally:

```bash
keytool -genkeypair -v -keystore release.keystore -alias shelfsnap \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # paste the output into SIGNING_KEYSTORE_BASE64
```

To build a signed release APK locally, export the same values as environment variables
(`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`) and run `./gradlew assembleRelease`.

> **Note:** Exported CSV files and captured photos live in the app's scoped external
> storage (`Android/data/com.shelfsnap.app/files/`). Use the in-app **Share** action on
> the summary screen to send a CSV off-device; photo paths in the CSV reference this
> app-internal storage.
