# Changelog

## Unreleased

### Features

### Improvements

### Fixes

## 1.25.0 (2026-07-29)

### Features

**On-device AI is real now** — Gemma downloads in-app instead of a manual .gguf import:
* local listing generation now actually refines your copy, on-device
* local vision analysis (experimental) can identify items from a photo, on-device
* both share one downloaded model — no separate Moondream import anymore

### Improvements

* Shared: local-models gained a single-file download acquisition type, for Scrybe's Gemma fix (no visual change)
* fixed a build error in the shared local-AI module — a nonexistent `.text` property on the model response (no visual change)
* bumped the repo's Kotlin/KSP toolchain to 2.3.0 everywhere — required to compile against the on-device engine's litertlm-android dependency, whose newer releases all ship Kotlin metadata older compilers can't read (no visual change)
* bumped Hilt to 2.58 — 2.51.1's Gradle plugin couldn't find KSP's task class once KSP moved to 2.3.0; 2.58 is the newest release that still supports this repo's AGP 8.7.3 (2.59+ requires AGP 9) (no visual change)
* bumped Room to 2.8.4 — 2.6.1 predates Room's KSP2 support and crashed under KSP 2.3.0 (no visual change)

**Market research** — searches finish noticeably faster:
* a provider's eBay/Mercari/OfferUp queries now run at the same time, not one after another

**Market research** — a live status toast now shows research progress:
* slides up from the bottom while searching, verifying, and analyzing
* shows short marketplace names and a verified count that stays visible throughout
* the "found" count no longer drops when analysis starts

**Market research** — Craigslist and Facebook Marketplace are now searched too:
* both were previously skipped entirely, even as broadening queries
* expect fewer hits there — Facebook is barely indexed, Craigslist deletes sold posts

**Market research** — active listings are found, not just sold ones:
* eBay's sold-only filter no longer silently excluded every active listing
* the extra query now runs only for the provider that needs it
* other marketplaces already return a natural mix, so no extra query needed

**Market research** — Debug info now shows total API calls and which services ran:
* a new "API calls" section lists the total count and services used

### Fixes

**Market research** — active-listing evidence is now used correctly:
* a historical sales count no longer misclassified active listings as sold
* the managed (Pro) search path no longer stops after one marketplace hits 5 results
* active listings can now become comps instead of being silently discarded


## 1.24.2 (2026-07-28)

### Features

### Improvements

### Fixes

**Market research** — every marketplace is searched again, not just eBay:
* a quota bug let eBay results crowd out Mercari and OfferUp
* SearchAPI and/or Serper now search every marketplace directly
* Jina now verifies listings independently of its own search toggle
* Brave only searches as a fallback, skipping dead or blocked reads



## 1.24.1 (2026-07-28)

### Features

### Improvements

**Market research** — far fewer search calls for the same evidence:
* each provider now stops once it finds four real marketplace postings
* the same query is no longer fanned out to every provider at once
* real marketplace listings outrank blog and shop pages in the evidence

**Price analysis** — the plot now shows every comparable price:
* each listing price appears as its own bar, so the spread is visible
* the comp average is a separate circle, never mistaken for a real sale
* the plotted range always covers every price shown, active or sold

### Fixes

**Price analysis** — an AI guess no longer poses as sold data:
* with no verified listings it now reads "AI estimate", not "Avg sold price"
* the price range and plot are hidden when nothing backs them

**Sources** — only listings that back the estimate are cited:
* unrelated results the AI already rejected no longer appear
* a note explains that verified sold prices need SearchAPI.io

**Facebook Marketplace comps** — these were silently discarded:
* marketplace detection returned a platform key nothing could resolve




## 1.24.0 (2026-07-23)

### Features

### Improvements

**Screen margins** — content now sits closer to the screen edges:
* the outer margin tightened from 16dp to 12dp across every screen for more usable width

### Fixes





## 1.23.3 (2026-07-23)

### Features

### Improvements

**AI categories** — analysis now assigns consistent resale-friendly categories:
* clothing, home goods, media, toys, and other items use a shared taxonomy
* common AI terms such as apparel and kitchenware map consistently to those categories
* similar labels no longer split matching items across near-duplicate groups

### Fixes






## 1.23.2 (2026-07-22)

### Features

### Improvements

### Fixes

**Inventory list** — items now show again below the filter chips:
* the chip row was expanding to fill the screen and pushing the list off-screen







## 1.23.1 (2026-07-21)

### Features

### Improvements

### Fixes

**Photo upload size** — large captures are now actually downsized:
* a common 4032px-wide photo previously skipped resizing entirely

**Inventory filters** — selecting a filter with no matches no longer hides everything:
* filter chips and search stay visible; a "Show all items" action appears instead







## 1.23.0 (2026-07-19)

### Features

**Welcome walkthrough** — a four-page intro now runs on first launch:
* covers capture, AI review, cross-listing, and local-first privacy
* skippable, and finishes on the inventory capture action

### Improvements

**Better first steps** — the empty inventory now leads with capture:
* "Snap your first item" is the primary action; Settings is demoted
* the capture button shows a text label until the first item exists
* the welcome popup no longer covers the walkthrough
* the empty listing summary now jumps back to the List tab

**Market research** — the screen now reports its own status:
* a "Last researched" line shows when results were fetched
* research errors now appear on this screen, not just elsewhere

**Cross-listing** — FB Marketplace copy now matches Facebook's own listing form:
* condition text now reads New / Used – Like New / Used – Good / Used – Fair
* title length matches Facebook's real limit, tips cover photos and tags

**Vision analysis** — sharper photos and a real AI-written title:
* photos are captured and uploaded at much higher quality
* small text like model and serial numbers reads far more reliably
* the AI now writes a specific, marketable title directly

### Fixes

**Inventory cards** — readable status at a glance:
* condition badges are legible in light theme, not washed out
* "92% conf." is now "92% confidence"
* inventory filter chips fade at the edges instead of clipping

**Item title** — re-analyzing an item now actually saves its refreshed title:
* previously the field updated on screen but reverted on save

**Multi-photo analysis toggle** — Settings → AI now does what it says:
* off sends only the primary photo; on sends every captured photo
* previously every photo was always sent regardless of this toggle

**AI analysis toggles** — three more Settings controls now do what they say:
* condition detection off keeps condition manual, instead of always AI-set
* price estimate off leaves value blank, instead of always AI-filled
* auto-analyze now actually skips the manual Analyse tap on the first photo

**Removed "Keep original photos"** — it never affected app behavior:
* no compression or deletion path existed for it to control








## 1.22.1 (2026-07-15)

### Features

### Improvements

### Fixes

**Market research** — verified sold listings now produce reliable price comparisons:
* SearchAPI uses completed sales and preserves structured sold prices
* provider failures appear beside the exact query that failed
* unverified AI listings no longer appear as comparable evidence
* empty evidence no longer displays a misleading zero-price card










## 1.22.0 (2026-07-14)

### Features

### Improvements

* Shared: Codex now validates the sibling Worker workspace and PriceDrop discovery contracts (no visual change)

### Fixes











## 1.21.4 (2026-07-13)

### Features

### Improvements

### Fixes

**Screen transitions** — fixed a white flash on the edges during navigation:
* backgrounds now stay themed throughout the slide animation
* most noticeable previously in dark mode












## 1.21.3 (2026-07-13)

### Features

### Improvements

**Screen transitions** — navigation now matches Scrybe's directional slide motion:
* forward screens slide in from the right
* back navigation returns toward the left

* Codex and Claude now share deterministic local emulator navigation and visual regression tooling (no visual change)

### Fixes













## 1.21.2 (2026-07-12)

### Features

### Improvements

### Fixes

**AI configuration** — fixed a layout bug, decluttered credential rows, added missing-key warnings:
* fixed the Recommended tag wrapping into vertical letters
* credential rows show a compact colored dot instead of a text tag
* full descriptions, setup steps, and signup links moved to an info sheet (tap ⓘ)
* a search provider toggled on with no saved key now shows an inline warning














## 1.21.1 (2026-07-11)

### Features

### Improvements

**Internal: shared section-card components** — Settings and AI configuration screens now use shared `AppLabeledSectionCard`/`AiSectionCard`/`AppEmptyState` components instead of a private near-duplicate implementation, matching Scrybe and PriceDrop's card styling exactly

**BYOK clarity** — AI configuration now shows what's required vs optional:
* credential rows tag each key Required, Recommended, or Optional
* descriptions explain what breaks vs just degrades without each key

**Serper.dev search** — a cheaper alternative to SearchAPI.io:
* new BYOK search provider, off by default like Brave
* honors site: filters but has no dedicated eBay engine

### Fixes

**What's New — bold formatting** — item titles in the full What's New screen (Settings → What's New) now render bold, matching the automatic popup — previously only the popup applied bold weight to item titles, so the two "unified" surfaces looked inconsistent
**Internal: What's New last-seen key** — standardized on the same DataStore key name/type Scrybe and PriceDrop use, with a one-time migration from the old key so existing users don't see a spurious re-prompt of the popup
**Re-analyze — item title** — re-running AI analysis on an existing item now refreshes the title from the new brand/model/category, unless you've already typed your own title — previously the title stayed stuck at whatever it was when the item was first created, even after re-analysis found very different brand/model info
**Vision analysis — more specific titles** — the AI is now asked to identify the specific product/style name when it's visible in photos (e.g. "Air Force 1 '07"), not just a generic brand — this feeds directly into the item's auto-generated title
**Listing refine — better titles** — AI-refined per-platform listing titles are now explicitly composed to be specific and keyword-rich (brand + model + size/color + condition), the way experienced resale sellers write them, instead of a generic copywriting instruction with no guidance on title quality; size and color are now included in what the AI sees when refining a listing
**Inventory search — no-results state** — searching with no matches now shows a proper empty state (search icon + message) instead of plain unstyled text
**Back button accessibility** — every screen's back button now has a "Back" content description for screen readers, instead of none















## 1.21.0 (2026-07-03)

### Features

**Item title field** — items now have an editable Title field (Item Detail → Title, above Category). New AI-created items get a generated title (brand + model, or category as fallback); existing items show the same fallback until edited. The title is now used for the Inventory list, the listing preview, per-platform listing copy, and market-research search queries instead of each screen recomputing its own brand/model fallback

### Improvements

**What's New popup** — now renders the same polished, topic-grouped changelog formatting (bold topic rows with sub-bullets) as the full Settings → What's New screen, instead of a flatter list
**Internal: What's New popup** — de-duplicated the popup's version-tracking and changelog-loading logic into a shared `WhatsNewPopupCoordinator`
**Settings screen** — the Pro card and About/What's New/Privacy section now use shared design-system components, matching Scrybe and PriceDrop
**AI configuration** — now has a dedicated "Listing generation" section (separate from "Market research"), each with its own Pro / BYOK / Local source control and model picker, matching PriceDrop's per-feature layout — listing refinement is routed through the same Pro/BYOK logic as Vision and Market Research instead of always using your raw OpenAI key directly
**Market Research** — now fails immediately with a clear message ("Add an OpenAI key and enable at least one search provider…", "needs an active Shelf Snap Pro subscription…", or "is turned off…") instead of spinning for several seconds before a generic error when nothing is configured

### Fixes

**Changelog asset** — fixed a build bug where it was never bundled (the Gradle task pointed at a repo-root `CHANGELOG.md` that no longer exists) — the full "What's New" screen and the automatic popup were both silently rendering empty
**API keys** — (OpenAI, Jina, Brave, SearchAPI.io) now correctly show as "Connected" immediately after a cold app restart — the "Connected" badge was tied to this session's live validation result, which always starts unset, instead of to whether a key is actually saved; a re-test can still reset the validation feedback, but the saved key now always shows as configured
**Internal: Market Research preflight** — now refreshes the subscription status before rejecting a request — otherwise a cold-started Pro user could hit the "needs an active Pro subscription" error until they'd separately opened Settings once
**Internal: item title field** — editing an item's category, brand, or any other field no longer silently writes the title-field's fallback text (brand+model or category) into the persisted title — the fallback stays a display-only fallback until the title field is actually edited
















## 1.20.0 (2026-07-02)

### Features

### Improvements

* internal: introduced the shared `local-models` and `pro` core modules (on-device model specs/state and managed-Pro policy contracts) that Shelf Snap will consume to unify local-model and Pro handling across TwoBits apps (no behavior change yet)
* internal: extended the shared `ProTierCard` (badge / price note / accent / compact comparison layout) and added shared Pro usage, spend-cap, and BYOK-note cards for the upcoming unified Pro screen (no behavior change yet)
* internal: Shelf Snap's on-device Moondream and Gemma models now implement the shared `LocalModelSpec` contract and the import flow uses the shared `LocalModelState` type (Absent/Acquiring/Ready/Error); local AI execution is still not implemented (the existing "not yet available" behavior is unchanged)
* the Pro screen now shows the managed monthly spend cap and the real per-feature allowances (replacing the placeholder "this month's usage" numbers), and the plan tiers use the shared design component
* the BYOK note now uses the shared wording making clear your key is used directly from the device and never routes through TwoBits managed infrastructure or your Pro allowance
* internal: `ItemRepository.analysePhotos`/`researchPrice` now dispatch on the shared `ExecutionMode` type instead of raw "pro"/"byok"/"local" strings, so the compiler enforces all three modes are handled at the exact call sites that decide whether a request goes to the worker, direct to the provider, or on-device; no change to behavior or persisted settings

### Fixes

















## 1.19.0 (2026-06-28)

### Features

### Improvements

**Market research — SearchAPI.io search:**
* added SearchAPI.io as a BYOK web-search provider (in Credentials) and made it the primary search for market research — it honors `site:` filters and returns real marketplace listings with links, where Jina returned eBay error pages and no usable links
* Jina AI is now used to read listing pages and as a search fallback; Brave remains a supplemental index
* fixed an over-long fallback search query (it embedded the full AI description) that returned 0 results

**AI configuration — all keys in one place:**
* OpenAI, Jina AI, and Brave Search keys are now grouped in a single Credentials section at the top of AI configuration — collapsible rows with masked keys and connection status, matching PriceDrop
* the Web search section now just toggles each provider on/off; the keys live in Credentials above

* internal: shared billing gained a one-time subscription-refresh helper used by sibling apps to avoid cold-start Pro mis-detection (no behavior change in Shelf Snap — its Pro/BYOK source is an explicit setting)
* internal: the shared credential registry's SerpAPI entry was renamed to SearchAPI.io (no behavior change in Shelf Snap)
* internal: the shared billing refresh helper now retries after a failed RevenueCat fetch rather than latching to Free (no behavior change in Shelf Snap)

### Fixes

* List tab: a sold listing can now be marked unsold (reverted to active) — previously a listing marked sold had no way to undo it


















## 1.18.0 (2026-06-27)

### Features

### Improvements

**Market research — marketplace search now honors site: filters:**
* managed (Pro) web search switched to SearchAPI.io, which respects `site:` operators — the previous Jina endpoint silently ignored them, so platform-targeted queries (eBay, Mercari, OfferUp) returned no marketplace evidence
* eBay-targeted queries use SearchAPI.io's dedicated eBay engine for structured sold-listing results
* generic item searches now use up to 50 characters of item name instead of 40, preserving more context for platform-targeted queries

**Shelf Snap Pro — independent subscription:**
* Shelf Snap Pro is now its own subscription (shelfsnap_pro entitlement), separate from Scrybe and PriceDrop Pro
* managed requests carry an app identifier so the Worker verifies the Shelf Snap entitlement specifically
* in Pro, the model for vision analysis, price research, and listing generation is chosen by the managed service, so we can tune quality/cost without an app update; BYOK still uses your selected model

**Vision model default** — item analysis now uses GPT-5 mini by default:
* default changed from GPT-5 ($1.25/$10 per 1M tokens) to GPT-5 mini ($0.25/$2 per 1M tokens) — 5× cheaper with comparable accuracy for draft generation
* existing model selection in AI Config is preserved; GPT-5 remains available

**AI Config — credential validation** — key verification is now consistent across all providers:
* saving a Jina AI or Brave Search key immediately tests the connection, matching OpenAI's save-and-verify behaviour
* a "Checking connection…" message is shown while any key is being verified
* success messages use "Connected to X" format across all three providers

### Fixes



















## 1.17.0 (2026-06-25)

### Features

* provider credential rows: added optional setup hint and Sign up link (used in PriceDrop; no visual change in Shelf Snap)

**Credential security** — your API keys are now encrypted on this device:
* OpenAI, Jina, and Brave keys stored with AndroidKeyStore AES-256/GCM
* saving a key in Settings silently mirrors it to Scrybe and PriceDrop
* a missing local key reads through from sibling apps on launch — no setup needed
* credential data excluded from Google Auto Backup; keys re-populate from siblings after restore

### Improvements

* cross-app credential bridge covers OpenAI, Jina, and Brave keys; future shared types silently skipped if unsupported

**Market research** — price research results load significantly faster and return more relevant sold listings:
* All search queries now run in parallel instead of sequentially, cutting total wait time from ~44s to ~10–15s
* Page reading (Jina Reader) also runs in parallel across the top results
* Search queries for items without a brand or model are now shorter and more targeted, producing real sold-listing results from eBay and Mercari instead of generic article pages

### Fixes




















## 1.16.0 (2026-06-23)

### Features

**Listing summary** — new screen after cross-listing shows per-platform expandable cards:
* Title, Description, Condition, Price, and Shipping fields each have a copy button
* title field shows character count against the platform limit
* "Copy all fields" copies everything to the clipboard at once
* "Refine" and "Refine all" call the AI listing copywriter to polish your draft

**Inline platform tips** — tips appear directly below each checked platform row:
* no expand/collapse needed — tips show when you check a platform, hide when you uncheck

**Unlist action** — active listings now have an Unlist button:
* tapping Unlist moves the listing to Unlisted and removes it from the active section

**Cross-listing flow** — platforms are now created as Drafts before going live:
* "List on X platforms" generates copy and navigates to the Listing summary screen
* "Mark listed" in the summary confirms the listing went live on a platform

### Improvements

**Market research** — platform-targeted queries always run, even for generic items:
* site:ebay.com/itm, mercari.com, and offerup.com queries generated for all items
* generic items previously got bare text queries that returned 0 sold listings

**Market research debug** — new expandable Synthesis prompt section:
* shows the first 800 characters of the AI system prompt used to synthesize price estimates

* market research: AI now instructed to copy exact source URLs rather than synthesizing them, reducing hallucinated comp links

**Comparable listings** — source attribution shown below the comps list:
* shows which platforms were searched and which AI provider synthesized the estimates

### Fixes

* market research: generic-item query descriptor now uses item.description + category (Item has no name field); fixes Debug/Release compile error





















## 1.15.0 (2026-06-23)

### Features

**Market research — page reading** — Jina AI now reads full listing pages for better price estimates:
* top comparable results opened via Jina Reader (r.jina.ai)
* estimates based on actual listing price, condition, and sold status — not just snippets

**Market research — debug info** — new collapsible panel shows exactly what happened:
* inline "search queries" expander shows the queries sent
* timings for search, page reading, AI synthesis, and total duration
* confidence factors: sold listings found, platforms covered, pages read, price variance

### Improvements

* Settings sections use shared AppSectionLabel (gray uppercase label above each card) — consistent with other TwoBits apps

**AI configuration** — Jina AI and Brave Search now have independent toggles:
* both can be active simultaneously for broader search coverage
* Jina searches and reads listing pages; Brave adds an independent search index
* with both on, results are merged and top pages are read via Jina

### Fixes

* AI config: Pro row "Not subscribed" badge no longer wraps to two lines on narrow screens






















## 1.14.0 (2026-06-22)

### Features

### Improvements

**AI Config key panels** — Jina and Brave key fields now use the shared credential card:
* consistent Save, Test, and Clear buttons with inline success/error feedback

* listing tips label now shows the platform name ("eBay listing tips") instead of generic "Tips"

* README: updated provider setup section with current model names and Jina/Brave key requirements

### Fixes

* Item Detail: "GPT-4o analysis" label now shows the actual model name used (tracked in UI state)






















## 1.13.0 (2026-06-22)

### Features

### Improvements

**Inventory** — sort and updated filter chips:
* Sort button now opens a bottom sheet with 5 options: Newest first, Oldest first, Value (high/low), A → Z
* filter chips updated to All · Drafts · Listed · Sold (replacing the Unlisted chip)
* Sold filter shows only items with at least one platform listing in Sold status

**Listing URL tracker** — track live listing URLs per platform:
* active listing rows show an "Paste listing URL after publishing…" text field when no URL is set
* saving a URL replaces the field with a tappable link chip that opens the listing in the browser
* `PlatformListing.listingUrl` persisted in the item's JSON listings field

### Fixes

* MarketTab: comp status chip fixed to use status_listed (filter_unlisted was removed)






















## 1.12.0 (2026-06-21)

### Features

### Improvements

### Fixes

* ktlint formatting fixes across source and test source files (trailing commas, multiline expressions, blank lines at class body start, parameter formatting in ItemThumb and PlatformUi components)
* ktlint formatting fixes in CameraScreen, CameraViewModel, AppModule, BillingProviderModule, LocalModelManager, Migrations, Condition, Item, DraftItemResult, and WebSearchResolver
* ktlint formatting fixes in AppDatabase, Converters, ItemDao, and ItemEntity (trailing commas, blank lines, multiline expressions)






















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
