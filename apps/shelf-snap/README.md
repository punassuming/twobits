# shelf-snap — *what you have, what you give*

An Android app that helps users document, value, and export a donation inventory using on-device camera capture and AI-powered item analysis.

---

## Features

| Story | Description |
|---|---|
| **Photo Capture** | Capture one or more photos per item using CameraX. Thumbnails appear in a scrollable strip; individual photos can be removed before analysis. |
| **Draft Item Extraction** | Photos are sent to the OpenAI Vision API (GPT-4o). The API returns `category`, `description`, `condition`, `estimatedValue`, and a `confidencePercent` as structured JSON, which is saved as a draft record. |
| **Inventory Review & Editing** | Tabbed item detail (Details / Market / List). The **Details** tab edits every AI-proposed field plus extended attributes (brand, model, size, color, quantity, original price, tags). Inventory supports search (category, brand, or description), filter chips (All / Listed / Unlisted / Drafts), inline delete, and a paginated photo viewer. |
| **Valuation Suggestion** | Estimated values are always labeled as *estimates* in the UI and CSV export — never presented as authoritative. Confidence scores from the model are surfaced as a badge. |
| **Market Research & Pricing** | The **Market** tab researches resale value via OpenAI inference (`gpt-4o-mini`) plus an optional web search (Brave or DuckDuckGo). It surfaces average sold price, a price range, per-platform suggested prices, comparable listings, and **cited sources** — estimates always show where they came from. |
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
3. Open **Settings** (gear icon) and paste your [OpenAI API key](https://platform.openai.com/api-keys).
4. *(Optional)* In **Settings → Price research search**, choose a web-search provider for market pricing:
   - **DuckDuckGo** — keyless; works out of the box.
   - **Brave Search** — paste a [Brave Search API](https://api-dashboard.search.brave.com/) subscription token.
   - **None (AI only)** — pricing relies on the model's own knowledge, no web calls.
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

## CI / Building a test APK

Every push to `main`, `copilot/**`, or `claude/**` (and every PR to `main`) runs
`.github/workflows/build.yml`, which:

1. Runs **Android Lint** (`:app:lintDebug`) and the **unit tests** (`:app:test`) as gates.
2. Builds and uploads an install-ready **debug** APK — artifact `shelf-snap-install-v1.0-<sha>` (file: `shelf-snap-install.apk`).
3. Builds and uploads the raw **debug** APK — artifact `shelf-snap-debug-v1.0-<sha>`.
4. Builds and uploads the raw **release** APK — artifact `shelf-snap-release-v1.0-<sha>`.
5. Builds and uploads an install-ready **release** APK — artifact `shelf-snap-release-install-v1.0-<sha>` (file: `shelf-snap-release-install.apk`).

Download any of these from the **Artifacts** section of the workflow run in the GitHub
Actions tab, then install it on a device:

```bash
adb install shelf-snap-install.apk          # debug build
adb install shelf-snap-release-install.apk  # release build (signed if secrets are set)
```

> **Note:** Workflow artifacts require a GitHub sign-in to download and expire after
> 14 days. For permanent, publicly downloadable builds, use a **Release** (below).

---

## Publishing a Release

`.github/workflows/release.yml` publishes the release APK as a **GitHub Release**
asset — a permanent, public download on the repo's [Releases page](../../releases).
It runs when either:

- **A version tag is pushed** (official, versioned release):

  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```

- **It's triggered manually** from the **Actions → Publish Release APK → Run
  workflow** menu, where you supply the tag to create (and optionally mark it a
  pre-release).

Either way it builds `assembleRelease`, attaches the APK as
`shelf-snap-<tag>.apk` with auto-generated release notes, and signs it with the
release keystore when the signing secrets are present (debug-signed fallback
otherwise). Download it from the Releases page and `adb install shelf-snap-<tag>.apk`.

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
