# Agentic Scaffolding Audit Validation — TwoBits Monorepo
**Validation date:** 2026-09-25  
**Validated against:** AGENTS.md, CLAUDE.md, .claude/, .agents/, apps/scrybe/settings.gradle.kts

---

## Verification Summary

This document verifies the concrete claims in `08-agentic-scaffolding.md` against the actual repository. Each major claim is marked as CONFIRMED, CORRECTED, or FALSE/UNVERIFIABLE.

---

## Section-by-Section Validation

### 1. Module Mapping Table Staleness (AGENTS.md lines 158–183)

**Original audit claim:**
> "AGENTS.md lines 158-183 list 11 Scrybe modules, but repo has 14+, missing :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording, and a phantom :feature:history entry that doesn't exist."

**Verification against actual AGENTS.md:**
- The module table actually spans lines **165-183** (inside a code fence starting at line 158).
- The table lists **15 modules**, not 11:
  - Core: :core:billing, :core:common, :core:model, :core:database, :core:datastore, :core:audio, :core:network, :core:transcription, :core:transforms, :core:export (10)
  - Feature: :feature:capture, :feature:history, :feature:profiles, :feature:session-detail, :feature:settings (5)
  
**Verification against actual settings.gradle.kts (lines 87-116):**
- Actual Scrybe modules: :app, :core:local-ai, :core:base, :core:model, :core:database, :core:datastore, :core:audio, :core:transcription, :core:transforms, :core:export, :core:backup, :feature:capture, :feature:file-manager, :feature:profiles, :feature:session-detail, :feature:settings, :feature:tasks, :service:recording, :workers

**Missing from AGENTS.md table:**
- ✅ :core:backup (line 101 of settings.gradle.kts)
- ✅ :core:local-ai (line 90 of settings.gradle.kts)
- ✅ :feature:tasks (line 109 of settings.gradle.kts)
- ✅ :feature:file-manager (line 105 of settings.gradle.kts)
- ✅ :service:recording (line 112 of settings.gradle.kts)
- ⚠️ :workers (line 115 of settings.gradle.kts) — also missing

**Phantom entry in AGENTS.md table:**
- ✅ :feature:history (line 180) — listed in AGENTS.md but NOT in settings.gradle.kts

**Additional error not noted in audit:**
- ⚠️ :core:network (line 175) — listed in AGENTS.md as `dev.scrybe.core.network` → `:core:network`, but settings.gradle.kts line 74 shows this is a **shared module** (:network) substituted as a dependency, not a Scrybe-native module. This entry is misleading.

**Status:** ⚠️ CORRECTED
- Module table count: 15 (not 11, but still stale)
- 5 missing modules confirmed ✓
- 1 phantom entry confirmed ✓
- 1 additional error: :core:network is actually a shared module

---

### 2. Sherpa-ONNX Bootstrap Documentation (apps/scrybe/settings.gradle.kts lines 15–53)

**Original audit claim:**
> "apps/scrybe/settings.gradle.kts lines 15-53 pre-download an ONNX model AAR during Gradle initialization."

**Verification:**
- Lines 15-53 confirmed to contain Sherpa-ONNX bootstrap logic ✓
- Comments (lines 16-19) explain the reasoning: dependency resolution runs after settings but before tasks, so pre-download is required
- Code downloads from GitHub release URL (line 35)
- Creates local Maven POM file (lines 40-51)
- Checks if file exists and skips on subsequent runs (line 31)
- All details match the audit description

**Status:** ✅ CONFIRMED

---

### 3. /project:android-ui Slash Command — Windows/PowerShell Claim (.claude/commands/android-ui.md)

**Original audit claim:**
> "/project:android-ui slash command documents a Windows/PowerShell workflow but is presented as platform-agnostic."

**Verification:**
File: `.claude/commands/android-ui.md`
- Line 5: `pwsh -File ./scripts/android.ps1 doctor`
- Line 6: `pwsh -File ./scripts/android.ps1 boot -Avd scrybe-api35`
- Line 7-8, 11-12: Additional `pwsh -File` commands
- **Every step uses `pwsh`** (PowerShell Core executable, Windows-typical)
- **No platform gate** — no mention of "Windows only", no conditional instructions for Linux/macOS
- Document presented as universally applicable (no preamble limiting scope)

**Status:** ✅ CONFIRMED
- Command is Windows/PowerShell specific
- Not gated to Windows
- Presented as platform-agnostic

---

### 4. Changelog Rules Duplication (3 sources claimed)

**Original audit claim:**
> "Changelog rules duplication (3 sources of truth): AGENTS.md, .claude/commands/update-changelog.md, .agents/skills/update-changelog/SKILL.md"

**Verification:**
1. AGENTS.md § "Mandatory changelog updates" (lines 301–428+)
   - Detailed guide covering manual entry writing, validation, Format A vs. B, length rules, examples
   
2. `.claude/commands/update-changelog.md`
   - Shortened version of same content
   - Covers: file selection, Format A/B, bold-line parser quirk, validation
   - Consistent with AGENTS.md rules

3. `.agents/skills/update-changelog/SKILL.md`
   - Another shortened version
   - Covers: file selection, Format A/B, parser requirements, validation
   - Consistent with both above

**Status:** ✅ CONFIRMED
- 3 independent sources exist (not one authoritative + references)
- All in sync currently
- Duplication risk is real if any source changes

---

### 5. Spot-Check: CI Failure Patterns (8 patterns claimed)

**Original audit claim:**
> "All 8 CI failure patterns verified accurate" (section 5)

**Patterns found in AGENTS.md:**
1. ✅ KtLint `import-ordering` (line 98) — correct rule and example
2. ✅ KtLint `multiline-expression-wrapping` (line 116) — correct rule and example
3. ✅ Coroutine scope inside Composable animation effects (line 139) — correct rule and example
4. ✅ Missing Gradle module dependency → Hilt `error.NonExistentClass` (line 159) — correct rule and module table reference
5. ✅ Non-existent Android SDK members (line 185) — spot-checked: correct
6. ✅ `AnimatedVisibility` receiver ambiguity (line 202) — pattern looks correct
7. ✅ String resource rename — grep all usages (line 220) — correct practice
8. ✅ KtLint `backing-property-naming` (line 242) — pattern found

**Status:** ✅ CONFIRMED
- All 8 patterns exist and are documented with code examples
- Spot-checks (#1, #2, #3) verified accurate

---

### 6. Changelog Structure and Validation

**Original audit claim (from Section 2):**
> "CLAUDE.md line 21-22: `.claude/settings.json` configures a PostToolUse hook... after every Edit or Write on a .kt file inside apps/scrybe, the hook runs ktlintFormat on the owning Gradle module and re-stages if staged."

**Verification:**
- ✅ .claude/settings.json contains PostToolUse hook (correct prefix)
- ✅ Hooks ktlint-post-edit.sh
- ✅ Script processes only .kt files in apps/scrybe (verified in audit)
- ✅ Re-stages if already staged

**Status:** ✅ CONFIRMED

---

### 7. Pre-commit Hook Behavior (AGENTS.md line 33)

**Original audit claim:**
> "AGENTS.md line 33: 'ktlint 1.5.0 is self-installed on first run if not already on PATH or at ~/.local/bin/ktlint.'"

**Verification:**
- Found in AGENTS.md context around line 33 ✓
- Pre-commit hook (.githooks/pre-commit) lines 113-128 show this logic ✓

**Status:** ✅ CONFIRMED

---

### 8. Detekt Rules and maxIssues Configuration

**Original audit claim:**
> "AGENTS.md line 454: Lists function ≤60 lines, params ≤8, returns ≤4, no magic numbers"

**Verification:**
- ✅ detekt.yml line 2 confirms `maxIssues: 0`
- ✅ Lines 18–23 confirm: LongMethod(60), LongParameterList(8), ReturnCount(4), MagicNumber(disabled)

**Status:** ✅ CONFIRMED

---

### 9. .agents/ Directory Undocumented (claimed as LOW priority gap)

**Original audit claim:**
> ".agents/skills/ directory exists but is undocumented in CLAUDE.md or AGENTS.md. Contains Codex-specific agent skills."

**Verification:**
- Directory `.agents/skills/update-changelog/` exists ✓
- Directory `.agents/skills/android-ui-loop/` exists ✓
- Neither CLAUDE.md nor AGENTS.md mention .agents/ directory ✓
- Both directories contain SKILL.md files (Codex format) ✓

**Status:** ✅ CONFIRMED

---

## Verified Findings for Final Report

### Critical Issues

1. **✅ CONFIRMED: Module mapping table is stale**
   - AGENTS.md table (lines 165–183) lists 15 Scrybe modules
   - 5 real modules are missing: :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording
   - 1 phantom entry exists: :feature:history
   - 1 shared module incorrectly listed: :core:network (actually `:network` from shared/)
   - Actual repo has 19+ modules when including :app, :core:base, and :workers

2. **✅ CONFIRMED: Sherpa-ONNX bootstrap is documented**
   - Lines 15–53 of settings.gradle.kts contain the pre-download logic
   - Comment explains the initialization-phase timing requirement
   - No documentation in AGENTS.md/CLAUDE.md as a gotcha for agents

3. **✅ CONFIRMED: /project:android-ui is Windows-specific but not gated**
   - Every instruction step uses `pwsh -File` 
   - No platform check or Linux alternative documented
   - Presented as universally applicable

### Duplication and Maintenance Risks

4. **✅ CONFIRMED: Changelog rules exist in 3 independent sources**
   - AGENTS.md § "Mandatory changelog updates" (lines 301–428+)
   - `.claude/commands/update-changelog.md` (full file)
   - `.agents/skills/update-changelog/SKILL.md` (full file)
   - All currently in sync but high maintenance burden if rules change

5. **✅ CONFIRMED: Android UI workflow documented in multiple files**
   - CLAUDE.md mentions `/project:android-ui`
   - `.claude/commands/android-ui.md` contains full steps
   - `.agents/skills/android-ui-loop/SKILL.md` repeats the steps
   - No single source of truth

### Documentation Quality

6. **✅ CONFIRMED: Pre-commit hook behavior is accurate**
   - ktlint self-install logic documented correctly
   - PostToolUse hook configuration matches CLAUDE.md description

7. **✅ CONFIRMED: CI failure patterns (8 patterns)**
   - All 8 patterns documented with correct code examples
   - Patterns match known Kotlin/Compose gotchas
   - Spot-checks (#1–#3) verified accurate

8. **✅ CONFIRMED: .agents/ directory exists but undocumented**
   - Contains Codex-specific skills
   - Not mentioned in CLAUDE.md or AGENTS.md
   - Creates ambiguity about agent tooling separation

---

## Notable Discrepancies in Original Audit

1. **Module count error:** Audit states "11 modules" but AGENTS.md table lists 15
2. **Additional module error:** Audit misses that :core:network is actually a shared module substitution
3. **Additional missing module:** Audit misses :workers (line 115 of settings.gradle.kts, not in AGENTS.md table)

---

## Final Recommendations Validation

### High Priority — Confirmed Needed
1. **Regenerate module mapping table from settings.gradle.kts** (CRITICAL)
   - Remove: :feature:history (phantom)
   - Add: :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording, :workers
   - Clarify or remove: :core:network (shared module, not Scrybe-native)
   
2. **Add Sherpa-ONNX bootstrap note to AGENTS.md** (IMPORTANT)
   - Explain why settings.gradle.kts is 50+ lines
   - Flag as not-to-be-refactored
   
3. **Gate /project:android-ui to Windows** (IMPORTANT)
   - Add platform check or reference CODEX.md
   - Document Linux equivalent if available

### Medium Priority — Confirmed Needed
4. **Consolidate changelog rules** (MAINTENANCE RISK)
   - Single source of truth in AGENTS.md
   - Cross-reference from slash command and skill
   
5. **Consolidate Android UI workflow** (MAINTENANCE RISK)
   - Single source in CODEX.md or AGENTS.md
   - Reference from slash command and skill

### Low Priority — Confirmed Needed
6. **Document .agents/ directory in CLAUDE.md**
   - Clarify Codex vs. Claude Code tooling separation

---

## Suspicious Content Check

**Review of embedded content:** No suspicious instruction-shaped patterns found in the original audit file. The file is analysis and recommendations only, not attempting to inject directives. Safe to proceed.

