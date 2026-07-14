# TwoBits UI/UX review roadmap

This is a future-work inventory, not an instruction to redesign the apps in one pass. It reviews all 70 curated API 35 artifacts (35 screens in light and dark) captured on the canonical `scrybe-api35` emulator. Recheck an item against a fresh capture before implementing it.

## Shared-first rules

Use the shared layer when an interaction, accessibility behavior, layout primitive, or visual token has the same meaning in at least two apps. Keep domain navigation, copy, data density, and app-specific workflows local.

- Extend an existing shared component before cloning it into another app.
- Add a new shared primitive only when two concrete consumers are ready.
- Prefer slots and semantic parameters over app-name conditionals.
- Keep screen composition in each app; share cards, empty states, selectors, onboarding chrome, and coordinators.
- Add shared screenshot coverage when a shared change affects multiple apps, then recapture every consumer.
- Do not force visual parity where the jobs differ; require interaction and accessibility consistency.

Highest-value shared candidates:

1. `OnboardingScaffold`: progress, Back/Next/Skip placement, safe-area handling, test tags, and resume state.
2. `FirstRunCoordinator`: deterministic ordering for onboarding, permission education, and What's New.
3. `ProviderCredentialRow`: compact status, setup help, validation, cost note, and missing-key warning.
4. `ProTierComparison`: responsive one/two/three-column layout with readable minimum type sizes.
5. `AppEmptyState`: optional illustration, primary/secondary actions, and compact/expanded variants.
6. Shared loading, error, permission-rationale, and camera-shell states.

## Cross-app opportunities

| Priority | Opportunity | Shared ownership |
|---|---|---|
| P0 | Prevent first-run modals from stacking after dedicated onboarding | `FirstRunCoordinator` in shared design/common |
| P0 | Make denied permissions recoverable without trapping progression | shared rationale pattern; app-local permission lists |
| P1 | Replace compressed three-column Pro copy on phones | shared `ProTierComparison` |
| P1 | Reduce AI Config density and expose missing requirements earlier | shared credential/feature primitives |
| P1 | Standardize empty-state action hierarchy and useful next steps | shared `AppEmptyState` variants |
| P1 | Guarantee 48dp targets, screen-reader labels, and large-font reflow | shared components plus per-screen audits |
| P2 | Align loading, offline, empty, and partial-data behavior | shared state primitives; app-local messages |
| P2 | Keep directional slide navigation consistent across all apps | shared transition specification and tests |
| P2 | Add tablet/window-size behavior after phone layouts stabilize | shared adaptive tokens and app-local panes |

## Scrybe artifacts

| Screen | Artifacts | Discrepancy or opportunity | Priority / likely owner |
|---|---|---|---|
| AI Config | [light](ui-baselines/scrybe/api35/light/ai-config.png) · [dark](ui-baselines/scrybe/api35/dark/ai-config.png) | Dense provider/model controls need stronger grouping, requirement status, and large-font reflow. | P1 shared credential primitives; local feature copy |
| Capture empty | [light](ui-baselines/scrybe/api35/light/capture-empty.png) · [dark](ui-baselines/scrybe/api35/dark/capture-empty.png) | Recording-mode chips crowd and clip; the primary record action needs clearer mode context. | P1 local screen; shared chip overflow guidance |
| File Manager | [light](ui-baselines/scrybe/api35/light/file-manager.png) · [dark](ui-baselines/scrybe/api35/dark/file-manager.png) | Empty state leaves a large dead area and does not teach import/storage behavior. | P2 shared empty state; local education/action |
| History seeded | [light](ui-baselines/scrybe/api35/light/history-seeded.png) · [dark](ui-baselines/scrybe/api35/dark/history-seeded.png) | Improve metadata hierarchy, scanability, and selection affordance; add an explicit empty baseline. | P1 local list; shared metadata styles |
| People | [light](ui-baselines/scrybe/api35/light/people.png) · [dark](ui-baselines/scrybe/api35/dark/people.png) | Sparse first-use state needs a concrete explanation of speaker assignment and next action. | P2 shared empty state; local copy |
| Pro | [light](ui-baselines/scrybe/api35/light/pro.png) · [dark](ui-baselines/scrybe/api35/dark/pro.png) | Three-column comparison makes type and distinctions too small on a phone. | P1 shared Pro comparison |
| Profiles | [light](ui-baselines/scrybe/api35/light/profiles.png) · [dark](ui-baselines/scrybe/api35/dark/profiles.png) | Cards are information-dense; separate primary identity/actions from advanced transform settings. | P1 local composition; shared card/actions |
| Recording Types | [light](ui-baselines/scrybe/api35/light/recording-types.png) · [dark](ui-baselines/scrybe/api35/dark/recording-types.png) | Empty/default presentation does not explain how types alter capture and output. | P2 local content; shared empty state |
| Session Detail | [light](ui-baselines/scrybe/api35/light/session-detail.png) · [dark](ui-baselines/scrybe/api35/dark/session-detail.png) | Transcript, playback, metadata, and actions compete; establish a clearer task-first hierarchy. | P1 local screen |
| Settings | [light](ui-baselines/scrybe/api35/light/settings.png) · [dark](ui-baselines/scrybe/api35/dark/settings.png) | Capture is contaminated by an automatic approximate-location permission dialog; request only from an explicit action. | P0 local permission trigger; shared rationale |
| Tasks | [light](ui-baselines/scrybe/api35/light/tasks.png) · [dark](ui-baselines/scrybe/api35/dark/tasks.png) | Empty state should explain extraction prerequisites and offer a path to create/capture content. | P2 shared empty state; local action |
| What's New | [light](ui-baselines/scrybe/api35/light/whats-new.png) · [dark](ui-baselines/scrybe/api35/dark/whats-new.png) | Preserve shared structure; verify long entries, expansion focus, and no post-onboarding modal stacking. | P1 shared coordinator/changelog UI |

## Shelf Snap artifacts

| Screen | Artifacts | Discrepancy or opportunity | Priority / likely owner |
|---|---|---|---|
| AI Config | [light](ui-baselines/shelf-snap/api35/light/ai-config.png) · [dark](ui-baselines/shelf-snap/api35/dark/ai-config.png) | Dense credential rows obscure the minimum setup required for the first useful result. | P1 shared credential primitives; local requirements |
| Camera | [light](ui-baselines/shelf-snap/api35/light/camera.png) · [dark](ui-baselines/shelf-snap/api35/dark/camera.png) | Guidance is small over a black/masked preview; define permission, unavailable-camera, and framing states. | P1 shared camera shell; local capture overlay |
| Inventory empty | [light](ui-baselines/shelf-snap/api35/light/inventory-empty.png) · [dark](ui-baselines/shelf-snap/api35/dark/inventory-empty.png) | Primary capture CTA and multi-item workflow are under-explained. | P0 local onboarding/empty copy; shared empty state |
| Inventory seeded | [light](ui-baselines/shelf-snap/api35/light/inventory-seeded.png) · [dark](ui-baselines/shelf-snap/api35/dark/inventory-seeded.png) | Improve item status hierarchy, bulk-selection discoverability, and consistent card actions. | P1 local list; shared card/action patterns |
| Item Detail | [light](ui-baselines/shelf-snap/api35/light/item-detail.png) · [dark](ui-baselines/shelf-snap/api35/dark/item-detail.png) | Long vertical form mixes facts, research, and selling actions; stage the workflow and use progressive disclosure. | P1 local screen |
| Listing Summary | [light](ui-baselines/shelf-snap/api35/light/listing-summary.png) · [dark](ui-baselines/shelf-snap/api35/dark/listing-summary.png) | Mostly blank/dead-end presentation needs generated-content status, editing, retry, and next action. | P0 local workflow; shared generated-state pattern |
| Market Research | [light](ui-baselines/shelf-snap/api35/light/market-research.png) · [dark](ui-baselines/shelf-snap/api35/dark/market-research.png) | Sparse results need source freshness, confidence, empty/error explanations, and comparison guidance. | P1 local results; shared state primitives |
| Pro | [light](ui-baselines/shelf-snap/api35/light/pro.png) · [dark](ui-baselines/shelf-snap/api35/dark/pro.png) | Three-column text is undersized and feature differences are difficult to scan. | P1 shared Pro comparison |
| Settings | [light](ui-baselines/shelf-snap/api35/light/settings.png) · [dark](ui-baselines/shelf-snap/api35/dark/settings.png) | Clarify the relationship between AI credentials, privacy, exports, and capture defaults. | P2 shared settings sections; local content |
| Summary | [light](ui-baselines/shelf-snap/api35/light/summary.png) · [dark](ui-baselines/shelf-snap/api35/dark/summary.png) | Make completion, batch status, corrections, and the next inventory/listing action explicit. | P1 local workflow |
| What's New | [light](ui-baselines/shelf-snap/api35/light/whats-new.png) · [dark](ui-baselines/shelf-snap/api35/dark/whats-new.png) | First launch currently relies on this modal; it should follow, not substitute for, onboarding. | P0 shared first-run coordinator |

## PriceDrop artifacts

| Screen | Artifacts | Discrepancy or opportunity | Priority / likely owner |
|---|---|---|---|
| AI Config | [light](ui-baselines/price-drop/api35/light/ai-config.png) · [dark](ui-baselines/price-drop/api35/dark/ai-config.png) | Recapture the Serper-primary, SearchAPI-secondary, optional-Rainforest copy and validate multi-select comprehension. | P0 provider correction; P1 shared credentials |
| Ask | [light](ui-baselines/price-drop/api35/light/ask.png) · [dark](ui-baselines/price-drop/api35/dark/ask.png) | Suggestion chips clip/crowd; explain live-data limits and preserve input priority at large font sizes. | P1 local screen; shared chip flow |
| Barcode | [light](ui-baselines/price-drop/api35/light/barcode.png) · [dark](ui-baselines/price-drop/api35/dark/barcode.png) | Raw black camera shell needs permission, unavailable-camera, manual-entry, and framing guidance states. | P1 shared camera shell; local barcode behavior |
| Drops | [light](ui-baselines/price-drop/api35/light/drops.png) · [dark](ui-baselines/price-drop/api35/dark/drops.png) | The light baseline is stale: a fresh capture has the correct empty state and hides “Mark all done.” Review and explicitly re-accept it. | P0 baseline maintenance; retain local action-gating test |
| Onboarding | [light](ui-baselines/price-drop/api35/light/onboarding.png) · [dark](ui-baselines/price-drop/api35/dark/onboarding.png) | Only page one of three is captured; add resume, back, accessibility, and complete-page coverage. | P0 shared scaffold; local content |
| Pro | [light](ui-baselines/price-drop/api35/light/pro.png) · [dark](ui-baselines/price-drop/api35/dark/pro.png) | Phone-width comparison is compressed; distinguish managed Pro from direct BYOK more plainly. | P1 shared Pro comparison |
| Product Detail | [light](ui-baselines/price-drop/api35/light/product-detail.png) · [dark](ui-baselines/price-drop/api35/dark/product-detail.png) | Many cards compete; prioritize price/target/actions, then progressively disclose history, offers, coupons, and activity. | P1 local screen |
| Search | [light](ui-baselines/price-drop/api35/light/search.png) · [dark](ui-baselines/price-drop/api35/dark/search.png) | Sparse initial state needs clearer URL/keyword/barcode choices, examples, and provider readiness. | P1 local screen; shared readiness/error state |
| Settings | [light](ui-baselines/price-drop/api35/light/settings.png) · [dark](ui-baselines/price-drop/api35/dark/settings.png) | Improve frequency/cost consequences and make Pro/BYOK routing discoverable without opening AI Config. | P2 shared settings patterns; local policy copy |
| Watch empty | [light](ui-baselines/price-drop/api35/light/watch-empty.png) · [dark](ui-baselines/price-drop/api35/dark/watch-empty.png) | Strengthen the first action and explain URL, search, barcode, targets, and coupon behavior. | P0 shared empty state; local onboarding copy |
| Watch seeded | [light](ui-baselines/price-drop/api35/light/watch-seeded.png) · [dark](ui-baselines/price-drop/api35/dark/watch-seeded.png) | Filter chips crowd; clarify stale/error status, target progress, and per-card primary action. | P1 local list; shared chips/status |
| What's New | [light](ui-baselines/price-drop/api35/light/whats-new.png) · [dark](ui-baselines/price-drop/api35/dark/whats-new.png) | Baselines predate recent releases; refresh explicitly, then prevent display immediately after onboarding and verify expansion focus. | P0 baseline maintenance and shared first-run coordinator |

## Robust first-run experience

Keep app-specific walkthrough content, but implement the mechanics once.

### Shared contract

- Persist the current page and completion separately; resume after process death.
- Order first-run surfaces: onboarding → contextual permission request → home. Defer What's New until a later launch/version.
- Never request a permission before its benefit is explained; denial must offer Continue and Settings recovery.
- Make Back, Next, Skip, and Finish stable, screen-reader labeled, and reachable at 200% font scale.
- Record analytics locally or with explicit consent; onboarding must not require network, billing, or AI calls.
- Test fresh install, interrupted resume, denial, permanent denial, upgrade, dark mode, and large font.
- Capture every page and each permission/error branch on the canonical emulator.

### Scrybe

- Retain the four domain-specific steps, but persist the selected recording mode.
- Let microphone denial continue into a read-only home with an obvious recovery action.
- Treat an OpenAI key as optional; validate only when the user explicitly tests/saves it.
- Request location only from the recording-location setting, never upon merely opening Settings.

### Shelf Snap

- Add a dedicated persisted walkthrough instead of using What's New as onboarding.
- Cover capture purpose, review/correction, market research/listing, and local/privacy expectations.
- Make camera denial lead to manual inventory entry, not a dead end.
- Connect Finish directly to the empty-inventory primary action.

### PriceDrop

- Retain the three app-specific marketing pages and capture all pages in both themes.
- Explain provider readiness before a search fails, including the Pro and BYOK alternatives.
- Offer URL/keyword entry when camera permission is denied.
- Finish at Watch empty with one dominant Add product action; defer What's New.

## Product discovery and promotion decision

Serper Google Shopping is the primary broad offer-discovery source. SearchAPI is a concurrent secondary source. Jina remains fallback web discovery and URL reading. Rainforest is optional Amazon enrichment rather than a core dependency.

No general coupon provider is selected. Promotions come from shopping or retailer metadata and manually entered codes. Applicability stays explicit, and uncertain promotions do not reduce effective price. A future coupon adapter remains experimental until measured against a representative product corpus.

BYOK executes enabled adapters directly. Pro calls the independently deployed Worker route `/v2/products/discover`; both paths normalize into the shared schema under `shared/contracts/price-drop/v2/`. The Worker must deploy before the Android client switches to the route.

## Delivery slices

1. P0 first-run coordination, permission recovery, and empty-action bugs.
2. Shared Pro and credential density improvements across all three apps.
3. Scrybe capture/session hierarchy and Shelf Snap listing workflow.
4. PriceDrop watch/product-detail hierarchy and complete onboarding matrix.
5. Accessibility, large-font, tablet, offline, and partial-data sweep.

Each slice should change the shared layer first where applicable, update app changelogs in the repository format, recapture every affected light/dark baseline, and require explicit baseline acceptance.
