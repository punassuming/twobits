# API Key & Credential Management Audit

**Date:** 2026-09-25 | **Auditor:** Claude Haiku 4.5 | **Scope:** TwoBits monorepo (Scrybe, Shelf Snap, PriceDrop)

---

## 1. Inventory of API Key/Credential Types per App

### Shared Credential Registry
All three apps share access to 8 credential types defined in `SharedCredentialId.kt:4-15`:
- `OPENAI` — OpenAI API (primary AI model)
- `JINA` — Jina AI (web search + page reading)
- `BRAVE` — Brave Search API
- `SEARCHAPI` — SearchAPI.io (marketplace shopping search)
- `SERPER` — Serper.dev (marketplace shopping search)
- `FIRECRAWL` — Firecrawl (alternative page reader)
- `COUPON` — Coupon data provider (reserved, not actively used)
- `RAINFOREST` — Rainforest API (Amazon enrichment)

### Per-App Usage

**PriceDrop** (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/credentials/PriceDropCredentialBridge.kt:16-23`)
- Supports: OPENAI, JINA (mapped to WEB_SEARCH), SEARCHAPI (mapped to SHOPPING), SERPER, FIRECRAWL, RAINFOREST
- Uses internal `PriceDropProvider` enum (`PriceDropProvider.kt:10-150`) with 6 entries: OPENAI, WEB_SEARCH, SHOPPING, SERPER, FIRECRAWL, RAINFOREST
- Does NOT support: BRAVE, COUPON (explicitly returns null/Unit)
- Storage: per-app `ProviderSettingsStore` using encrypted DataStore (`ProviderSettingsStore.kt:18-19`)

**Shelf Snap** (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/credentials/ShelfSnapCredentialBridge.kt:14-22`)
- Supports: OPENAI, JINA, BRAVE, SEARCHAPI, SERPER, FIRECRAWL
- Does NOT support: COUPON, RAINFOREST (no mapping, returns null)
- Storage: per-app repository methods (`getApiKey()`, `getJinaApiKey()`, `getBraveApiKey()`, etc.)

**Scrybe** (`apps/scrybe/core/transcription/src/main/kotlin/dev/scrybe/core/transcription/ScrybeCredentialBridge.kt:13-16`)
- Supports ONLY: OPENAI
- All other credentials return null
- Storage: via `ApiKeyProvider` interface (maps to `KeystoreApiKeyProvider` in shared/api-keys)

### Summary Table

| Credential Type | PriceDrop | Shelf Snap | Scrybe | Screen/Location |
|---|---|---|---|---|
| OpenAI | ✓ (OPENAI) | ✓ | ✓ | AI Config → Credentials |
| Jina AI | ✓ (WEB_SEARCH) | ✓ | ✗ | Settings → Services |
| Brave Search | ✗ | ✓ | ✗ | Settings → Services |
| SearchAPI.io | ✓ (SHOPPING) | ✓ | ✗ | Settings → Services |
| Serper.dev | ✓ (SERPER) | ✓ | ✗ | Settings → Services |
| Firecrawl | ✓ (FIRECRAWL) | ✓ | ✗ | Settings → Services |
| Rainforest API | ✓ (RAINFOREST) | ✗ | ✗ | Settings → Services |
| Coupon Data | ✗ (reserved) | ✗ | ✗ | (not implemented) |

---

## 2. Shared Credential Store Analysis

### Architecture
The cross-app credential sharing is implemented via ContentProvider (`SharedCredentialProvider.kt:29-96`):

**Provider Side:**
- `SharedCredentialProvider` exposes an Android ContentProvider at authority `{appId}.credentials`
- Implements `call()` method (`SharedCredentialProvider.kt:43-66`) supporting three operations:
  - `METHOD_GET` — retrieves a credential by SharedCredentialId
  - `METHOD_SET` — stores a credential (calls `bridge().set()`)
  - `METHOD_CLEAR` — clears a credential
- Delegates to per-app `CredentialBridge` implementation for actual storage
- Returns plaintext values; each app re-encrypts under its own AndroidKeyStore key
- Signature-gated (requires matching package signature)

**Client Side:**
- `SharedCredentialClient` (`SharedCredentialClient.kt:35-82`) calls other apps' ContentProviders
- Hardcoded sibling app IDs:
  - `dev.scrybe.android`
  - `com.shelfsnap.app`
  - `com.twobits.pricedrop`
- Two main operations:
  - `readThrough(id)` (`SharedCredentialClient.kt:57-67`) — queries each sibling in order, returns first non-blank match
  - `mirror(id, value)` (`SharedCredentialClient.kt:69-81`) — writes to all installed siblings

### Current Limitation: Promise NOT Realized
**Finding:** Despite the cross-app credential infrastructure, the "shared once, usable by all" promise is **NOT fully realized** because:

1. **Scrybe doesn't participate fully**: Only OpenAI credential flows through the shared store. No other credentials are supported by `ScrybeCredentialBridge.kt`, so users cannot share Jina/Brave/SearchAPI/Serper/Firecrawl keys to Scrybe even if they're stored in another app.

2. **Search/Data providers only mirrored at explicit save**: When a user enters a key in PriceDrop's Services screen and clicks Save, it's mirrored to Shelf Snap via `mirror()`. However:
   - If the user enters the same key in Shelf Snap first, PriceDrop won't automatically read it until/unless Shelf Snap explicitly mirrors it
   - The flow is "write propagates outward," not "all apps access a true shared store"

3. **Coupon and Rainforest have no fallback**: Coupon is reserved but never implemented. Rainforest is only in PriceDrop, so Shelf Snap and Scrybe users who install both cannot share Rainforest keys.

### Encryption: per-app key wrapping
- `CredentialCrypto.kt` (not shown but referenced) handles encryption/decryption
- Each app wraps credentials under its own AndroidKeyStore key on save
- Plaintext travels across ContentProvider boundaries; signature-gating provides the security boundary

---

## 3. Shared API-Keys Module Contents

Location: `shared/api-keys/src/main/kotlin/com/twobits/apikeys/`

**Data Model:**
- `ProviderType.kt:3-7` — Three types only:
  - `OPENAI` — user-provided OpenAI key
  - `LOCAL` — user's local encrypted key (unused in current codebase)
  - `MANAGED_PRO` — for Pro subscribers using managed proxy
- `ApiConfig.kt:3-6` — Data class holding `baseUrl` and `authToken`

**Routing Logic:**
- `ApiKeyRouter.kt:10-38` — Routes requests based on subscription tier:
  - **Pro tier** → uses managed proxy (`api.twobits.app`) with user ID as auth token
  - **Free tier** → uses OpenAI's public API with user's BYOK key, throws `NoApiKeyException` if missing
- Hardcoded error message: `"No API key stored. Please add your OpenAI key in Settings."` (line 29)

**Storage:**
- `KeystoreApiKeyProvider.kt:18-40` — Stores/retrieves via DataStore preferences
  - Uses key names: `OPENAI`, `LOCAL`, `MANAGED_PRO`
  - **LIMITATION:** Only supports 3 ProviderTypes; doesn't model Jina/SearchAPI/Serper/Firecrawl/Rainforest
  - These other credentials are managed entirely per-app (not via shared/api-keys)

**Validation:**
- `ApiKeyValidator.kt` (location: `shared/api-keys/`) — lightweight offline check
- Format: OpenAI keys must start with `sk-` and be ≥20 chars; others accepted if ≥8 chars

**Finding:** `shared/api-keys` only covers OpenAI (and theoretically LOCAL/MANAGED_PRO modes). Search and data provider credentials (Jina, Brave, SearchAPI, Serper, Firecrawl, Rainforest) are entirely outside this module's scope — they're stored per-app and don't use the ApiKeyRouter.

---

## 4. Entry UX per App

### PriceDrop
**Screens:**
- **AI configuration → Credentials** (`AIConfigScreen.kt:251-296`)
  - Shows OpenAI credential entry via `ProviderCredentialItem` component
  - Pro subscribers see `AiProManagedCard` instead (no key setup needed)
  - Free tier users see an Upgrade button

- **Settings → Services** (`ServicesScreen.kt:37-142`)
  - Shows Jina, SearchAPI, Serper, Firecrawl, Rainforest credentials
  - Each via `ProviderCredentialItem` (line 80 in ServicesScreen)
  - Per-provider feature toggles (which features use each provider)

**Entry & Validation UX:**
- `ProviderCredentialItem` (`ProviderCredentialRow.kt:83-142`):
  - Shows masked key (first 4 + last 4 chars with `•` padding, line 93)
  - **Test before save:** `onTest` button calls `viewModel.testProviderKey(provider, draft)` (line 132)
  - **Save action:** `onSave` calls `viewModel.setProviderKey(provider, draft)` (line 131)
  - **Clear action:** clears and calls `viewModel.clearProviderKey(provider)` (line 135)
  - Displays validation status (isKeyValid, isValidating, validationMessage) live
  - Shows provider cost estimate, signup URL, setup hint

**Validation Chain:**
- Offline check: `CredentialCheck.check()` (`CredentialCheck.kt:20-42`)
  - OpenAI: regex `sk-` + length ≥20
  - Others: length ≥8
- Live network test: `ProviderKeyValidator.validate()` (`ProviderKeyValidator.kt:25-42`)
  - OpenAI: calls `GET /v1/models` with Bearer token
  - Jina: calls `GET s.jina.ai/?q=test` with Bearer token
  - SearchAPI: calls `GET /api/v1/search?engine=google&q=ping` (consumes 1 search credit!)
  - Serper: calls `POST /search` with JSON body (consumes 1 search credit!)
  - Firecrawl: calls `POST /v2/scrape` with example.com (consumes 1 scrape credit!)
  - Rainforest: calls `GET /account?api_key=X` (returns account details)

### Shelf Snap
**Screens:**
- **AI Configuration → Credentials** (`AIConfigScreen.kt:389-456`)
  - Shows OpenAI only via `CollapsibleProviderRow`
  - Same Test/Save/Clear actions as PriceDrop
  - Validation: same as PriceDrop (showing "Checking connection…" while testing, error messages)

- **Settings → Services** (`ServicesScreen.kt:73-224`)
  - Shows SearchAPI, Serper, Jina, Firecrawl, Brave
  - Each via `CollapsibleProviderRow`
  - Feature toggles: which search providers are enabled for market research
  - Reader provider picker (Jina vs Firecrawl for page reading, line 231-248)

**Entry UX:** Same as PriceDrop's `ProviderCredentialItem` — masked key, Test/Save/Clear, cost estimates.

### Scrybe
**Screen:**
- **AI Configuration → Credentials** (`AIConfigScreen.kt:233-251`)
  - Uses `AiCredentialsDock` component (shared/design)
  - Shows OpenAI key entry only
  - Test/Save/Clear actions via `viewModel.testApiConnection()`, `viewModel.saveApiKey()`, `viewModel.clearApiKey()`
  - Validation status: Valid/Invalid/Validating

**NO Services screen** — Scrybe doesn't manage search/data provider credentials; it's transcription-only.

### Key Finding: Inconsistent UX Patterns
1. **PriceDrop & Shelf Snap** split credentials 50/50 (OpenAI in Credentials, others in Services)
2. **Scrybe** shows only OpenAI (no Services screen)
3. All three apps independently re-implement the "Test/Save/Clear" UI pattern
4. All three apps have independent masked-key display logic
5. No shared "credential entry component" — each app reimplements `ProviderCredentialItem` or `CollapsibleProviderRow`

---

## 5. Missing-Key UX & Error Handling

### AiNoKeyWarning Banner
Location: `shared/design/src/main/kotlin/com/twobits/design/components/AiConfigComponents.kt`
- Standard warning card (tertiaryContainer background, Key icon)
- Default text: `"No API key configured. Add your OpenAI key in the credentials panel above."`
- **Customizable:** apps pass `text:String` to override (PriceDrop's `AIConfigScreen.kt:252` uses `noKeyMessage(feature)`)

**Usage across apps:**
- **PriceDrop** (`AIConfigScreen.kt:252`):
  - Shows in feature detail view when no enabled provider has a valid key
  - Custom message via `noKeyMessage(feature)` (`ProviderCredentialRow.kt:75-78`)
  - Example: "No API key configured for Jina or SearchAPI.io. Add one in Services."

- **Shelf Snap** (`AIConfigScreen.kt` multiple locations, lines 438-445):
  - Shows when `isKeyVerified != true`
  - Displays actual error: "Connected to OpenAI" or error message from test

- **Scrybe** (`AIConfigScreen.kt:293`):
  - Shows when transcription mode is BYOK and key is blank
  - Shows when AI features mode is BYOK and key is blank
  - Standard message, no customization

### Error Handling Strategy
**When a feature lacks a key:**
- **PriceDrop, Ask feature** — blocks the feature with AiNoKeyWarning, shows providers that need keys
- **Shelf Snap, Market research** — shows warning; web search issues are shown in the feature detail
- **Scrybe, Transcription** — blocks transcription with warning; user must go to Settings to add key

**Silent failures / no warning:**
- No evidence of features that silently fail on missing keys
- All three apps explicitly show AiNoKeyWarning where keys are required
- Error UX is consistent across apps

### Finding: Missing-Key Message Specificity
**Strong point:** PriceDrop's `noKeyMessage()` is feature-aware and names which providers are missing. Shelf Snap and Scrybe use generic "No API key" messages.

---

## 6. Key Rotation & Multiple Keys

**Current Support: NONE**

The credential model across all three apps is **strictly one key per provider per app-install**:

1. **PriceDrop's `ProviderSettingsStore`** (`ProviderSettingsStore.kt:56-98`):
   - One `stringPreferencesKey()` per provider: `key_${provider.key}`
   - `setKey()` overwrites; no history, version, or multi-key support

2. **Shelf Snap's per-repository methods**:
   - Single methods: `saveApiKey()`, `getApiKey()`, etc.
   - No version tracking or rotation mechanism

3. **Scrybe's `KeystoreApiKeyProvider`** (`KeystoreApiKeyProvider.kt:23-39`):
   - One preference key per `ProviderType`: `OPENAI`, `LOCAL`, `MANAGED_PRO`
   - `setApiKey()` overwrites

**Multi-key use case (e.g., separate OpenAI keys for different projects):**
- Not supported
- User would have to re-enter the other key each time they switch projects
- No credential labels, descriptions, or metadata to distinguish keys

---

## 7. Redundant Entry Burden

**Counting distinct places a user with all three apps installed must enter an OpenAI key:**

1. **Scrybe**: 1 place — Settings → AI configuration → Credentials
2. **Shelf Snap**: 1 place — Settings → AI configuration → Credentials
3. **PriceDrop**: 1 place — Settings → AI configuration → Credentials

**TOTAL: 3 places** (one per app, even if all are installed)

**But:** Once entered in any one app, the key should auto-mirror to the others via `SharedCredentialClient.mirror()`. Testing this mechanism:
- User enters OpenAI key in Scrybe Settings → saves
- `SettingsViewModel.saveApiKey()` calls `apiKeyProvider.setApiKey(ProviderType.OPENAI, value)`
- This should trigger a mirror to Shelf Snap and PriceDrop (if the app-specific bridge wiring is complete)
- **HOWEVER:** The shared `mirror()` call is only explicit in PriceDrop's ProviderCredentialItem viewmodel; Scrybe and Shelf Snap would need to wire it themselves.

**For search/data providers (Jina, SearchAPI, etc.):**
- **PriceDrop**: 1 place (Settings → Services)
- **Shelf Snap**: 1 place (Settings → Services)
- **Scrybe**: NOT SUPPORTED (no Services screen, no credential storage for these)

**TOTAL for Jina (example): 2 places** — users must enter it in both PriceDrop and Shelf Snap; Scrybe cannot use it.

### Finding: Mirroring Incomplete
- Mirroring IS implemented (ContentProvider IPC exists)
- But not all apps call it on every save
- Search providers can only be mirrored if BOTH apps' viewmodels explicitly call `SharedCredentialClient.mirror()` after saving
- Need to audit SettingsViewModel implementations to confirm actual wiring

---

## 8. Unified Cross-App Management Proposal

### Current State Gaps
1. **No true shared credential UI component**: Each app reimplements its own credential entry (ProviderCredentialItem, CollapsibleProviderRow)
2. **Scrybe is read-only on search credentials**: Even if Scrybe could call the shared store, it doesn't expose the UI to enter them
3. **Coupon and Rainforest are under-specified**: Coupon reserved but never used; Rainforest only in PriceDrop
4. **Mirroring is opt-in per viewmodel**: Not automatic when credentials are saved; must be wired explicitly
5. **Multi-key / key rotation not supported**: One key per provider per app; no labeled alts or version history

### Proposed Unified Solution

**Phase 1: Shared Credential Entry Component**
Create `shared/design/CredentialEntryScreen.kt`:
- Reusable Composable accepting a `List<ProviderType>` (or enum of providers to show)
- Handles key masking, Test/Save/Clear UI uniformly
- Integrates `shared/api-keys/ApiKeyValidator` and per-app `ProviderKeyValidator` for live validation
- Returns: `CredentialSaveResult(provider, key, wasValid)`

**Phase 2: Automatic Mirroring on Save**
- Create a wrapper for `ProviderSettingsStore.setKey()` that auto-calls `SharedCredentialClient.mirror()`
- Implement in each app's CredentialBridge:
  ```kotlin
  override suspend fun set(id: SharedCredentialId, value: String) {
      providerStore.setKey(...) // save locally
      sharedCredentialClient.mirror(id, value) // propagate to siblings
  }
  ```

**Phase 3: Scrybe Credential UI**
- Add a Services screen to Scrybe with read-only status for Jina/Brave/SearchAPI/Serper/Firecrawl
- Let users see which keys are available (from shared store readThrough)
- Optionally allow editing if Scrybe UI is expanded in future

**Phase 4: Coupon & Rainforest Alignment**
- Define actual use cases for Coupon (reserved but unused)
- Decide: should Shelf Snap access Rainforest data? If yes, wire it; if no, document the gap
- Add to CredentialBridge contracts and UI as needed

**Phase 5: Key Metadata & Rotation**
Future enhancement (not in this cycle):
- Extend credential storage to include: `label` (e.g., "Main Project" vs "Testing"), `createdAt`, `lastUsedAt`
- Allow multiple keys per provider (e.g., two OpenAI keys) with picker UI
- UI: "Switch key" selector in AI Config when multiple are stored

### Specific Implementation Steps

**A. Consolidate Credential Entry UI**
- Extract `CollapsibleProviderRow` logic into parameterized component in `shared/design`
- Have all three apps use it instead of reimplementing

**B. Wire Automatic Mirroring**
- Update `PriceDropCredentialBridge.set()` to call `sharedCredentialClient.mirror()` (`PriceDropCredentialBridge.kt:26-40`)
- Update `ShelfSnapCredentialBridge.set()` to call `sharedCredentialClient.mirror()` (`ShelfSnapCredentialBridge.kt:24-36`)
- Add Scrybe wiring in `ScrybeCredentialBridge.set()` (`ScrybeCredentialBridge.kt:19-26`)

**C. Add Scrybe Services Screen**
- Create `apps/scrybe/feature/settings/ServicesScreen.kt`
- Show read-only status of Jina/Brave/SearchAPI/Serper/Firecrawl (queried via `SharedCredentialClient.readThrough()`)
- Option to "Import from sibling app" button if key exists elsewhere

**D. Document Search Provider Scope**
- Create per-app data model for which providers each app supports
- Update Changelog entries for each app to clarify (e.g., "Scrybe transcription does not use search providers; configure them in Shelf Snap or PriceDrop")

---

## Top 5 Priorities

1. **Auto-mirror credentials on save** (Priority: CRITICAL)
   - **Why:** The "shared once" promise is broken because only explicit saves in one app trigger mirroring
   - **Impact:** Users entering OpenAI key in Scrybe still must enter it again in Shelf Snap/PriceDrop; same for search providers
   - **Fix:** Wire `SharedCredentialClient.mirror()` into all three apps' CredentialBridge.set() methods
   - **Effort:** ~1–2 hours; test with multi-app scenarios
   - **File references:** PriceDropCredentialBridge.kt:26, ShelfSnapCredentialBridge.kt:24, ScrybeCredentialBridge.kt:19

2. **Clarify Scrybe's credential scope and document it** (Priority: HIGH)
   - **Why:** Scrybe only supports OpenAI; users may expect search/data provider keys to work, causing confusion
   - **Impact:** Reduced friction if users understand upfront that Scrybe is transcription-only
   - **Fix:** Add a "Services" screen showing that search providers are not applicable to Scrybe; update app descriptions
   - **Effort:** ~3–4 hours; requires UI addition and documentation update
   - **File references:** ScrybeCredentialBridge.kt:13-16 (define limits); new ServicesScreen.kt

3. **Consolidate credential entry UI into shared component** (Priority: HIGH)
   - **Why:** PriceDrop reimplements `ProviderCredentialItem`, Shelf Snap uses `CollapsibleProviderRow`, Scrybe uses `AiCredentialsDock`; all three implement masked-key logic separately
   - **Impact:** Reduces code duplication, ensures UX consistency, easier to update validation/test UX in one place
   - **Fix:** Extract into shared/design; have all three apps use it
   - **Effort:** ~4–5 hours; moderate refactor with comprehensive testing
   - **File references:** ProviderCredentialRow.kt, CollapsibleProviderRow (in shared/design), AIConfigScreen.kt (all three apps)

4. **Resolve Rainforest & Coupon gaps** (Priority: MEDIUM)
   - **Why:** Rainforest is PriceDrop-only; Coupon is reserved but never implemented
   - **Impact:** Confusing for users who expect consistency; Coupon data is inaccessible
   - **Fix:** Either (a) expand ShelfSnapCredentialBridge to support Rainforest, (b) document why it's PriceDrop-only, or (c) implement Coupon if there's a real use case
   - **Effort:** ~2–3 hours for (b); ~8 hours for (a) if Shelf Snap market research needs Rainforest
   - **File references:** ShelfSnapCredentialBridge.kt, PriceDropCredentialBridge.kt:20-23

5. **Implement key metadata and rotation framework** (Priority: MEDIUM, future)
   - **Why:** One-key-per-provider model doesn't support users with multiple projects/environments
   - **Impact:** Friction when switching between projects; no audit trail or last-used metadata
   - **Fix:** Extend ProviderSettingsStore to store key metadata (label, createdAt, lastUsedAt) and support multiple keys per provider
   - **Effort:** ~8–10 hours (new data model, UI picker, migration logic)
   - **File references:** ProviderSettingsStore.kt, new CredentialMetadata data model

---
