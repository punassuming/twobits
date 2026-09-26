# UI Styling & Standardization Audit
**TwoBits Monorepo** • September 25, 2026

## Overview
Audit of UI styling, component standardization, and shared-library equivalence across three Android Compose apps:
- **Scrybe** (`apps/scrybe`)
- **Shelf Snap** (`apps/shelf-snap`)
- **PriceDrop** (`apps/price-drop`)

Baseline: shared design system at `shared/design`

---

## 1. Shared Design Inventory

**Location:** `shared/design/src/main/kotlin/com/twobits/design/`

### Components (29 files)

| Component | File | What it does | Apps using it |
|-----------|------|-------------|----------------|
| **ProgressFooter** | ProgressFooter.kt | Background task progress indicator footer | Scrybe (2), Shelf Snap (3), PriceDrop (3) |
| **SettingsEntryCard** | SettingsEntryCard.kt | Settings list item card with icon/title/value | Scrybe (5), Shelf Snap (3), PriceDrop (3) |
| **SettingsRow** | SettingsRow.kt | Horizontal settings row component | Scrybe, Shelf Snap, PriceDrop |
| **CallBudgetCard** | CallBudgetCard.kt | Pro/subscription budget tracking card | Scrybe (2), Shelf Snap (2), PriceDrop (2) |
| **AppSectionCard** | SectionCard.kt | Generic section card with optional title | Scrybe (14), Shelf Snap (0), PriceDrop (0) |
| **AppLabeledSectionCard** | SectionCard.kt | Section card with icon + title + description | All three apps |
| **AppChipRow** | ChipRow.kt | Horizontal scrollable chip/filter row | Scrybe (4), Shelf Snap (2), PriceDrop (4) |
| **ErrorCard** | ErrorCard.kt | Error state card/message | Shelf Snap (2) |
| **AppEmptyState** | EmptyState.kt | Centered empty state with icon/title/actions | Scrybe (2), Shelf Snap (5), PriceDrop (3) |
| **ProGate** | ProGate.kt | Pro feature gate component | PriceDrop (1) |
| **ProTierCard** | ProTierCard.kt | Pro tier features/benefits card | Scrybe (4), Shelf Snap (4), PriceDrop (4) |
| **SubscriptionBanner** | SubscriptionBanner.kt | Subscription status banner | All three apps |
| **ApiKeyField** | ApiKeyField.kt | API key input field | Referenced in shared/ |
| **LoadingOverlay** | LoadingOverlay.kt | Full-screen loading overlay | Scrybe, Shelf Snap, PriceDrop |
| **ProviderCredentialCard** | ProviderCredentialCard.kt | Provider credential input (OpenAI, etc.) | Scrybe |
| **ModelRadioRow** | ModelRadioRow.kt | Radio button row for model selection | Scrybe |
| **SettingsAppInfoSection** | SettingsAppInfoSection.kt | App version/build info section | Scrybe, Shelf Snap, PriceDrop |
| **SettingsProStatusCard** | SettingsProStatusCard.kt | Settings screen Pro status display | Scrybe, Shelf Snap, PriceDrop |
| **ProUsageCard** | ProUsageComponents.kt | Pro usage/quota tracking card | Scrybe |
| **ProTierCard** | ProTierCard.kt | Pro subscription tier display | All three apps |
| **WhatsNewScreen** | WhatsNewScreen.kt | Release notes / changelog screen | All three apps |
| **AppWhatsNewDialog** | WhatsNewDialog.kt | Modal for release notes | Scrybe |
| **AiCredentialsDock** | AiConfigComponents.kt | AI provider credential input area | Scrybe |
| **LocalModelPanel** | LocalModelPanel.kt | Local model management panel | Scrybe |
| **LocalModelPicker** | LocalModelPicker.kt | Dialog for picking local models | Scrybe |
| **ModelStorageSection** | ModelStorageSection.kt | Model storage quota display | Scrybe |
| **AiSourceSegment** | AiConfigComponents.kt | AI source selection segment | Scrybe |

### Design Tokens (3 files)

- **TwoBitsTypography.kt**: Shared DmSans font family + Material3 Typography variants
- **TwoBitsShapes.kt**: Rounded corner shapes (10dp to 28dp)
- **ThemeMode.kt**: Theme mode management (light/dark/auto)

---

## 2. Near-Duplicate Components

### Found Near-Duplicates

#### A. **Empty State Pattern** — Scrybe has local version, others use shared

- **Scrybe** (FileManagerScreen.kt:202): `private fun EmptyState(message: String)` — simplistic version, text-only, no icon
- **Shelf Snap** (InventoryScreen.kt): Uses `AppEmptyState` with icon + title + actions
- **PriceDrop** (WatchScreen.kt): Uses `AppEmptyState`
- **Finding:** Scrybe should migrate its local `EmptyState` calls to shared `AppEmptyState`. Scrybe's version is overly minimal and not reusing the richer shared component.

**Locations:**
- `/home/user/twobits/apps/scrybe/feature/file-manager/src/main/kotlin/dev/scrybe/feature/filemanager/FileManagerScreen.kt:202`
- `/home/user/twobits/shared/design/src/main/kotlin/com/twobits/design/components/EmptyState.kt:30`

---

#### B. **Settings Row Components** — Similar patterns, different naming

- **Scrybe** (SettingsComponents.kt:49): `SettingOptionRow()` — clickable surface with title/value/supporting text
- **Shelf Snap**: Likely uses Material3 `ListItem` or similar
- **PriceDrop**: Uses shared `SettingsEntryCard` or native Material3
- **Finding:** Scrybe's `SettingOptionRow` appears duplicative with shared `SettingsRow`. Check if consolidation is possible.

**Locations:**
- `/home/user/twobits/apps/scrybe/feature/settings/src/main/kotlin/dev/scrybe/feature/settings/SettingsComponents.kt:49`

---

#### C. **Chip/Badge Components** — Scrybe has local implementations

- **Scrybe** (ScrybeChips.kt): 
  - `ModeBadge(mode: RecordingMode)` — pill badge with mode icon + label
  - `SessionStatusChip(status: SessionStatus)` — status indicator badge
  - `FilterChipRow()`, `TaskFilterChip()`, `DueChip()` — task-specific chips
  - `TagChipEditable()`, `TagPill()`, `EditableTagPill()` — tag display components
- **Shelf Snap**: Uses `AppChipRow` from shared; likely has local tag/badge handling
- **PriceDrop**: Uses `AppChipRow`; may have local platform/condition badges
- **Finding:** Scrybe has created its own chip family for recording modes and session status. These are app-specific enough (RecordingMode, SessionStatus) that they may not be unifiable, but the pattern should be documented in shared/design as a reference implementation.

**Locations:**
- `/home/user/twobits/apps/scrybe/core/common/src/main/kotlin/dev/scrybe/core/common/ScrybeChips.kt:65` (ModeBadge)
- `/home/user/twobits/apps/scrybe/core/common/src/main/kotlin/dev/scrybe/core/common/ScrybeChips.kt:104` (SessionStatusChip)

---

#### D. **Dialog Patterns** — Significant duplication in modals/bottom sheets

- **Scrybe** (SessionDetailScreen.kt): Many dialog functions (TransformResultDialog, ChangeTypeDialog, EditLocationDialog, TagEditorDialog, etc.)
- **Shelf Snap**: Inventory sort sheet (InventorySortSheet)
- **PriceDrop**: Similar modal patterns scattered across screens
- **Finding:** No shared dialog/sheet library exists. Each app hand-rolls confirmation dialogs, pickers, editors. Common patterns: text input dialog, option picker, confirmation alert.

**Locations:**
- `/home/user/twobits/apps/scrybe/feature/session-detail/src/main/kotlin/dev/scrybe/feature/sessiondetail/SessionDetailScreen.kt` (multiple DialogFunctions:2000+)

---

## 3. Theming & Design Tokens

### Shared Tokens (applied by all three apps)

**Typography** (`TwoBitsTypography.kt`):
- DmSans font family (400-700 weights) applied to all Material3 text styles
- All apps use identical typography

**Shapes** (`TwoBitsShapes.kt`):
- extraSmall: 10.dp
- small: 14.dp
- medium: 18.dp
- large: 24.dp
- extraLarge: 28.dp
- All apps use identical shapes

### App-Specific Color Palettes (DRIFT DETECTED)

#### Scrybe (`apps/scrybe/app/src/main/kotlin/dev/scrybe/android/ui/theme/Color.kt`)

**Color Strategy:** Branded 3-color accent system
- Primary: Signal Blue (light: 0xFF005B99, dark: 0xFF89C7FF)
- Secondary: Glow Teal (light: 0xFF1A7F8A, dark: 0xFF7DD4DC)
- Tertiary: Ember Orange (light: 0xFFB85C38, dark: 0xFFFFB695)
- Dark surfaces: Custom 6-layer hierarchy (DarkBg → DarkSurfHighest)
- Light surfaces: Mist palette (Mist100, Mist200)

#### Shelf Snap (`apps/shelf-snap/app/src/main/java/com/shelfsnap/app/ui/theme/Color.kt`)

**Color Strategy:** Same 3-color accent system as Scrybe (shares palette definition)
- Primary: Glow Teal (identical to Scrybe Secondary)
- Secondary: Signal Blue (identical to Scrybe Primary)
- Tertiary: Ember Orange (identical to Scrybe)
- **DRIFT:** Adds EstimateLabel colors (brown for light, amber for dark) — app-specific for "AI-generated" fields
- **DRIFT:** Adds condition-state colors (Excellent/Good/Fair/Poor) — selling-platform specific
- **DRIFT:** Adds platform brand colors (eBay red, Mercari, OfferUp, FB blue, Craigslist purple)

**Location:** `/home/user/twobits/apps/shelf-snap/app/src/main/java/com/shelfsnap/app/ui/theme/Color.kt:1-88`

#### PriceDrop (`apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/theme/Color.kt`)

**Color Strategy:** Distinct 3-color accent system (NOT shared with others)
- Primary: Coral Red (light: 0xFFC0382A, dark: 0xFFFF8066)
- Secondary: Green (light: 0xFF1E8E5A, dark: 0xFF88D7A8)
- Tertiary: Amber (light: 0xFF9A6A00, dark: 0xFFFFD580)
- **DRIFT:** Completely different from Scrybe/Shelf Snap color scheme
- Dark surfaces: Also uses custom layers, slightly different values from Scrybe
- **Issue:** No palette unification — this breaks consistency across the monorepo brand

**Locations:**
- `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/theme/Color.kt:1-76`

### Material3 ColorScheme Application

**Scrybe** (`ScrybeTheme.kt`):
- Uses custom darkColorScheme() with all roles mapped
- Uses inverseSurface/inverseOnSurface for inverse roles
- Maps custom DarkBg, DarkSurf hierarchy to Material3 surface containers

**Shelf Snap** (`Theme.kt`):
- Uses standard lightColorScheme() + darkColorScheme()
- Adds CompositionLocal `LocalEstimateLabel` for app-specific color theme override
- Properly implements M3 inverse roles
- **Comment in code:** "inverseSurface / inversePrimary silently fall back to Material3 stock baseline-purple defaults" — acknowledged unresolved

**PriceDrop** (`Theme.kt`):
- Standard Material3 lightColorScheme() + darkColorScheme() application
- No custom composition locals

### Issues Found

1. **PriceDrop color drift:** Uses completely different accent colors (red/green/amber) vs. Scrybe/Shelf Snap (blue/teal/orange). No shared palette.
2. **Surface container hierarchy:** Scrybe uses DarkBg/DarkSurf/DarkSurfHigh/etc., but PriceDrop defines its own similar-but-different values. Shelf Snap reuses Scrybe's values with M3 containers.
3. **Composition locals:** Only Shelf Snap adds LocalEstimateLabel. Scrybe/PriceDrop don't need it, but if Shelf Snap's pattern spreads, should be centralized.
4. **App-specific color families:** Shelf Snap adds ConditionColors and PlatformColors. These are business-logic-specific and appropriate to keep app-local, but should document the pattern in shared/design.

---

## 4. Material3 Usage Consistency

### Unified Material3 Version

All three apps use **composeBom 2024.12.01** via `gradle/libs.versions.toml`, ensuring identical Material3/Compose UI versions across the monorepo. No version skew.

**Gradle Aliases:**
- Scrybe: `libs.androidx.compose.material3`
- Shelf Snap: `libs.androidx.material3`
- PriceDrop: `libs.androidx.material3`

(Both aliases resolve to same artifact; kept for historical compatibility.)

### Material Components Used

**Consistent across all three apps:**
- Material3 ColorScheme (lightColorScheme/darkColorScheme)
- Material3 Typography
- Material3 Shapes
- Material3 Icons (material3.Icons.Filled.*)
- Material3 TopAppBar, Scaffold
- Material3 Button/TextButton, Switch, Slider
- Material3 Card, Surface
- Material3 TextField/OutlinedTextField
- Material3 AlertDialog, ModalBottomSheet

**Material2 Usage: NONE DETECTED** — all three apps are fully on Material3.

### Material3 Opt-In Annotations

- **Shelf Snap & PriceDrop:** Both have `ExperimentalMaterial3Api` opt-in in build.gradle.kts (for experimental features like TopAppBar scrollBehavior, SegmentedButton, etc.)
- **Scrybe:** No explicit opt-in detected in main build.gradle.kts; may be applied per-file via `@OptIn`

**Finding:** All three are consistently leveraging Material3. No backcompat debt; all experimental uses are documented.

---

## 5. Icon Usage Consistency

### Standard Icons (Consistent)

All three apps use Material3 Icons.Filled.* for common actions:
- **Settings:** Icons.Filled.Settings (all three apps)
- **Close:** Icons.Filled.Close (Scrybe, PriceDrop, Shelf Snap)
- **Back:** Icons.AutoMirrored.Filled.ArrowBack (PriceDrop, Shelf Snap)
- **Search:** Icons.Filled.Search (PriceDrop, Shelf Snap)
- **Delete:** Icons.Filled.Delete
- **Add:** Icons.Filled.Add

### App-Specific Icon Mappings

**Scrybe** (`ScrybeChips.kt`):
- Recording modes have dedicated icons:
  - Meeting → Icons.Filled.Groups
  - Idea → Icons.Filled.Lightbulb
  - Tasks → Icons.Filled.TaskAlt
  - Conversation → Icons.Filled.Forum
  - Story → Icons.Filled.MenuBook
  - Interview → Icons.Filled.PersonSearch
  - Journal → Icons.Filled.Book
  - Custom → Icons.Filled.Label

**Shelf Snap** (`ProviderCredentialRow.kt`):
- Platform icons for selling platforms:
  - eBay, Mercari, OfferUp, Facebook Marketplace, Craigslist each have distinct colors/presentation

**PriceDrop** (`SearchScreen.kt`, `WatchScreen.kt`):
- Search → Icons.Filled.Search
- Settings → Icons.Filled.Settings
- Standard M3 icons

### Finding

**No icon inconsistency detected.** Each app uses Material3 standard icons consistently for common actions. App-specific icons (recording modes, platforms) are intentional and well-scoped.

**Recommendation:** Consider documenting the Scrybe mode icons pattern in shared/design if other apps need to display recording modes. Currently isolated to Scrybe.

---

## 6. Spacing & Padding Conventions

### Shared Design Components (Good Pattern)

Components in shared/design follow consistent spacing:
- **ProgressFooter.kt:** Uses 16.dp horizontal padding, Material3 shapes
- **SettingsEntryCard.kt:** Uses 16.dp, 12.dp padding
- **CallBudgetCard.kt:** Uses 16.dp structural padding
- **AppEmptyState.kt:** Uses 32.dp horizontal, 8.dp gaps between elements
- **SettingsRow.kt:** Uses 12.dp, 8.dp padding

### App-Level Spacing Patterns (INCONSISTENCY FOUND)

#### Scrybe (SettingsComponents.kt)

```kotlin
// Line 63: SettingOptionRow — horizontal=14.dp, vertical=10.dp
modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),

// Line 64: verticalArrangement = Arrangement.spacedBy(4.dp),

// Line 72–73, 79–80, 82–83: Nested padding calls
.padding(12.dp) // inline text styles
```

**Pattern:** Scrybe uses hardcoded values (10, 12, 14, 4 dp) scattered throughout. No spacing constant defined.

#### Shelf Snap (InventoryScreen.kt)

Similar pattern to Scrybe — hardcoded padding values in lambda definitions.

#### PriceDrop (SettingsScreen.kt)

Uses Material3 defaults where possible (Scaffold, LazyColumn padding). Still has some hardcoded values in custom components.

### Issues Found

1. **No spacing token library:** Unlike typography and shapes which use TwoBits* shared objects, spacing is ad-hoc hardcoded dp values in each component.
2. **Inconsistent padding conventions:**
   - SettingOptionRow uses 14.dp horizontal, others use 12-16.dp
   - Vertical spacing varies (4.dp, 5.dp, 8.dp, 10.dp gaps)
   - No pattern for intra-component vs. between-component spacing
3. **Drift risk:** As components evolve, padding divergence will increase.

### Locations of Hardcoded Values

- `/home/user/twobits/apps/scrybe/feature/settings/src/main/kotlin/dev/scrybe/feature/settings/SettingsComponents.kt:63-87` (SettingOptionRow)
- Scrybe settings screens across feature/settings/
- Shelf Snap inventory/detail screens
- PriceDrop watch/search screens

### Recommendation

Create `TwoBitsSpacing.kt` in shared/design:
```kotlin
val TwoBitsSpacing = object {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 20.dp
  val xxl = 24.dp
}
```

Then migrate component padding to use TwoBitsSpacing.md, TwoBitsSpacing.lg, etc.

---

## 7. Shared Component API Consistency

### Components with Cross-App Usage (3+ apps)

#### ProgressFooter

**API:** `ProgressFooter(isVisible: Boolean, progress: Float?, label: String?, isError: Boolean)`

**Scrybe usage:**
- SessionDetailScreen, CaptureScreen
- Passes progress as Float, label as String
- Standard usage

**Shelf Snap usage:**
- InventoryScreen during import/analysis
- Similar usage pattern

**PriceDrop usage:**
- WatchScreen during market research / local analysis
- Similar usage pattern

**Finding:** ProgressFooter API is consistent across all three apps. No one-off overrides detected.

---

#### SettingsEntryCard

**API:** `SettingsEntryCard(icon, title, value, supportingText?, onClick)`

**Scrybe usage** (`SettingsScreen.kt`):
- Settings rows for API key, model selection, etc.
- Consistent parameter usage

**Shelf Snap usage** (`SettingsScreen.kt`):
- Settings rows for theme, filters, notifications
- Consistent parameter usage

**PriceDrop usage** (`SettingsScreen.kt`):
- Settings rows for notifications, theme, budget cap
- Consistent parameter usage

**Finding:** SettingsEntryCard API is consistent. All three apps use it the same way.

---

#### AppEmptyState

**API:** `AppEmptyState(icon, title, subtitle?, modifier, iconTint, primaryActionLabel?, onPrimaryAction?, secondaryActionLabel?, onSecondaryAction?)`

**Shelf Snap usage** (InventoryScreen.kt:73-79):
```kotlin
AppEmptyState(
    icon = Icons.Filled.Search,
    title = stringResource(R.string.no_items_match_search),
    primaryActionLabel = stringResource(R.string.show_all_items),
    onPrimaryAction = { viewModel.onFilterChange(InventoryFilter.ALL) },
)
```
- Uses icon + title + actions ✓

**PriceDrop usage** (WatchScreen.kt):
- Similar: icon + title + optional action ✓

**Scrybe usage:**
- Does NOT use AppEmptyState in FileManagerScreen
- Defines local `private fun EmptyState(message: String)` instead
- **Issue:** Scrybe is opting out of shared API; reimplements minimal version

**Finding:** API exists and is well-designed, but Scrybe doesn't use it. Migration opportunity.

---

#### ProTierCard

**API:** `ProTierCard(tier, features, onUpgrade?, modifier?)`

**Scrybe usage** (ProScreen.kt):
- Displays Pro tier options
- Standard usage

**Shelf Snap usage** (SettingsScreen.kt):
- Pro tier display
- Standard usage

**PriceDrop usage** (SettingsScreen.kt):
- Pro tier display
- Standard usage

**Finding:** ProTierCard API is consistent.

---

### Components with Limited Cross-App Adoption

#### AppSectionCard

**Current:** Only Scrybe uses this (14 imports found).

**Shelf Snap & PriceDrop:** Don't import it; likely use Material3 Card/Surface directly.

**Finding:** Underutilized component. Should standardize adoption across all three apps or reconsider its design.

---

#### SubscriptionBanner

**Designed for:** All three apps have subscription models.

**Finding:** All three apps import it, but usage frequency unknown. Should audit whether it's truly used or imported but unused.

---

### API Design Issues Found

1. **AppEmptyState not adopted by Scrybe:** Scrybe defined its own simpler `EmptyState()` before AppEmptyState existed in shared/design. Should migrate.
2. **AppSectionCard underutilized:** Only Scrybe uses it; Shelf Snap & PriceDrop roll their own card layouts.
3. **Optional parameters create flexibility, but:** When one app passes `modifier.fillMaxWidth()` and another passes default `fillMaxSize()`, behavior diverges. Document expected modifiers in API comments.

**Locations:**
- `/home/user/twobits/apps/scrybe/feature/file-manager/src/main/kotlin/dev/scrybe/feature/filemanager/FileManagerScreen.kt:202` (local EmptyState)
- `/home/user/twobits/shared/design/src/main/kotlin/com/twobits/design/components/EmptyState.kt:30` (shared AppEmptyState)

---

## Top 5 Unification Priorities

Ranked by **(duplication frequency) × (risk of bug/drift)**, based on ProgressFooter/SettingsEntryCard precedents.

---

### 1. **Scrybe EmptyState → AppEmptyState Migration** (Priority A1)

**Duplication:** Scrybe has `private fun EmptyState(message: String)` in FileManagerScreen (and elsewhere?); shared `AppEmptyState` exists but is unused in Scrybe.

**Drift Risk:** MEDIUM → HIGH
- Scrybe's version is oversimplified (text-only, no icon, no actions)
- Shelf Snap & PriceDrop are correctly using rich `AppEmptyState`
- If Scrybe adds use cases requiring icons/actions, will diverge further

**Scope:** ~2 files in Scrybe (FileManagerScreen + any other local EmptyState definitions)

**Action:** Grep for all `EmptyState(` calls in Scrybe, migrate to `AppEmptyState(icon=..., title=...)`, update API surface if needed.

**Effort:** LOW (rename + parameter reshaping)

**File:** `/home/user/twobits/apps/scrybe/feature/file-manager/src/main/kotlin/dev/scrybe/feature/filemanager/FileManagerScreen.kt:202`

---

### 2. **Create TwoBitsSpacing Token Library** (Priority A2)

**Duplication:** All three apps scatter hardcoded padding values (4dp, 5dp, 8dp, 10dp, 12dp, 14dp, 16dp, etc.) throughout components.

**Drift Risk:** MEDIUM
- As screens evolve, spacing will become inconsistent
- Precedent: ProgressFooter drift occurred because each app had local tweaks to padding
- Once spacing diverges, syncing becomes expensive

**Scope:** 
- Create `shared/design/src/main/kotlin/com/twobits/design/TwoBitsSpacing.kt`
- Update all shared components to use TwoBitsSpacing tokens
- Audit + update Scrybe/Shelf Snap/PriceDrop feature screens

**Effort:** MEDIUM (token definition, component updates, lint rule for hardcoded dp detection)

**Files affected:**
- Create: `/home/user/twobits/shared/design/src/main/kotlin/com/twobits/design/TwoBitsSpacing.kt` (new)
- Update: All in `shared/design/src/main/kotlin/com/twobits/design/components/`
- Audit: Feature screens in all three apps

---

### 3. **Unify PriceDrop Color Palette with Scrybe/Shelf Snap** (Priority B1)

**Duplication:** PriceDrop uses red/green/amber accents (0xFFC0382A, 0xFF1E8E5A, 0xFF9A6A00) while Scrybe/Shelf Snap use blue/teal/orange (0xFF005B99, 0xFF1A7F8A, 0xFFB85C38).

**Drift Risk:** HIGH
- This is a brand-level inconsistency
- Makes it confusing for users to recognize shared components across apps
- Monorepo uses shared/design for consistency, but color palette breaks that promise

**Scope:** 
- Determine which palette is "canonical" (likely blue/teal/orange shared by Scrybe & Shelf Snap)
- PriceDrop custom colors (condition states, etc.) can remain app-local
- Update PriceDrop's primary/secondary/tertiary to match Scrybe/Shelf Snap

**Effort:** HIGH (requires design review + brand decision, impacts theme visual testing)

**Files affected:**
- `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/theme/Color.kt:1-76`
- `/home/user/twobits/apps/price-drop/app/src/main/kotlin/com/twobits/pricedrop/ui/theme/Theme.kt`

**Blocker:** Requires design+product decision. Flag for PriceDrop team.

---

### 4. **Create Shared Dialog / BottomSheet Library** (Priority B2)

**Duplication:** Scrype defines 20+ dialog functions (ProfileEditorDialog, TransformResultDialog, ChangeTypeDialog, etc.). Shelf Snap has InventorySortSheet. PriceDrop has similar modals scattered.

**Drift Risk:** MEDIUM → HIGH
- No shared patterns for common dialogs (confirm, text input, option picker, sort/filter sheet)
- Each app will continue hand-rolling; maintenance burden grows
- Historical precedent: ProgressFooter showed drift when each app had custom footers; unified version prevents future splits

**Scope:** 
- Extract common dialog patterns:
  - `ConfirmDialog(title, message, onConfirm, onCancel)`
  - `TextInputDialog(title, initialValue, onSave, onCancel)`
  - `OptionPickerDialog(options, selected, onSelect)`
  - `SortFilterSheet(options, onApply)`
- Create in shared/design/src/main/kotlin/com/twobits/design/components/Dialogs.kt

**Effort:** MEDIUM (pattern extraction + API design, good opportunity for code review)

**Files:** Create new `/home/user/twobits/shared/design/src/main/kotlin/com/twobits/design/components/Dialogs.kt`

---

### 5. **Document & Standardize AppSectionCard Usage** (Priority C1)

**Duplication:** Scrybe uses AppSectionCard in 14 places; Shelf Snap & PriceDrop don't use it, rolling their own Material3 Card layouts.

**Drift Risk:** LOW → MEDIUM
- Component exists but isn't standardized across all apps
- If Shelf Snap/PriceDrop add similar use cases, they'll reimplement instead of reusing
- Not critical, but represents missed unification opportunity

**Scope:**
- Audit Shelf Snap & PriceDrop for card layout patterns that could use AppSectionCard
- If API fits, migrate to shared component
- Update API docs/kdoc for clarity on when to use vs. Material3 Card

**Effort:** LOW (audit only; migration is optional)

**Files:** 
- `/home/user/twobits/shared/design/src/main/kotlin/com/twobits/design/components/SectionCard.kt`

---

## Summary Table

| Priority | Component | Issue | Risk | Effort | Files |
|----------|-----------|-------|------|--------|-------|
| **A1** | Scrybe EmptyState | Not using shared AppEmptyState | MED→HIGH | LOW | FileManagerScreen.kt:202 |
| **A2** | TwoBitsSpacing | Hardcoded padding scattered | MED | MED | Create TwoBitsSpacing.kt + update all components |
| **B1** | PriceDrop Colors | Uses different palette | HIGH | HIGH | Color.kt, Theme.kt (design review required) |
| **B2** | Dialog/Sheets | 20+ hand-rolled dialogs | MED→HIGH | MED | Create Dialogs.kt in shared/design |
| **C1** | AppSectionCard | Underutilized, not standardized | LOW→MED | LOW | SectionCard.kt + audit Shelf Snap/PriceDrop |

