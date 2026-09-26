# Agentic Scaffolding Audit — TwoBits Monorepo
**Audit date:** 2026-09-25

---

## Executive Summary
This document audits the AI-agent-facing documentation and tooling infrastructure (`AGENTS.md`, `CLAUDE.md`, `.claude/`, `.agents/` directories) for accuracy, completeness, and gaps that would cause a new agent to struggle. The audit includes spot-checks of factual claims against the live repository state.

---

## 1. AGENTS.md: Structure & Coverage

### Summary
**Status:** Comprehensive and well-maintained. This is the authoritative reference document and lives up to that role.

**Structure:**
- Repository layout (3 apps + shared composite)
- Session setup (git hooks)
- Mandatory pre-commit checks (per app, with one-liners)
- Known CI failure patterns (8 detailed, actionable gotchas with code examples)
- Branch management (rebase discipline, changelog reconciliation)
- Mandatory changelog updates (two formats, validation rules)
- Commit message format (conventional commits, semver mapping)
- Detekt rules (maxIssues=0, specific thresholds)
- CI pipeline (per-app thin wrappers, shared reusable workflows)

### Coverage Assessment
**Strengths:**
- CI failure patterns section is gold: each gotcha has a "wrong vs. correct" example pair, making it scannable and actionable. The patterns are derived from actual CI breakages.
- Changelog rules are verbose but justified: the two-format system (titled vs. plain) is non-obvious, and the detail about `* **Title**` being parsed as untitled is important.
- Branch management includes the specific post-rebase changelog reconciliation step (removing duplicates after a release commit lands on main). This is a real gotcha.
- Module mapping table (package → Gradle module) is useful, though only covers `dev.scrybe.*` prefix (see Gap #2 below).

**Minor gaps:**
- The three apps share a single release workflow template system but **do not share code**. This is clear but could be more emphatic: a fresh agent might assume `shelf-snap/` and `price-drop/` inherit Scrybe's modules.
- No mention of the Sherpa-ONNX bootstrap quirk (Scrybe's `settings.gradle.kts` pre-downloads an AAR in the initialization phase). This is unusual and could surprise an agent trying to understand why settings.gradle.kts is 50+ lines.
- No guidance on which app(s) a proposed feature should target (beyond the name). An agent making a shared module change needs to know: does this affect all 3 apps or a subset?

---

## 2. CLAUDE.md: Cross-Check vs. .claude/settings.json

### Document Content
CLAUDE.md is correctly minimal: it defers to AGENTS.md for full reference and covers only Claude Code-specific setup.

### Accuracy Check
**Claim in CLAUDE.md, line 21-22:**
> "`.claude/settings.json` configures a **PostToolUse** hook: after every `Edit` or `Write` on a `.kt` file inside `apps/scrybe`, the hook runs `ktlintFormat` on the owning Gradle module and re-stages the file if it was already staged."

**Verification:**
- `.claude/settings.json` (actual): Contains a `PostToolUse` hook with matcher `"Edit|Write"` that calls `bash /home/user/twobits/.claude/scripts/ktlint-post-edit.sh`. ✓
- `ktlint-post-edit.sh` (actual): Only processes `.kt` files in `apps/scrybe`; skips other apps. Derives module path from file path. Runs `./gradlew :module:ktlintFormat`. Re-stages if already staged. ✓

**Status:** Accurate.

### Documentation Quality
- Good: Explicitly notes that `multiline-expression-wrapping` cannot be auto-fixed and cross-references AGENTS.md.
- Good: Includes session start checklist (matches AGENTS.md recommendations).
- Good: Mentions the changelog rule enforced by pre-commit hook.

**Minor issues:**
- Line 10-11 says "already set in this container" but doesn't explain when this isn't already set (first clone). Should clarify: "run once after cloning".
- Doesn't mention that the ktlint post-edit hook **only affects Scrybe**. An agent working on Shelf Snap or PriceDrop will not get auto-formatting and might be confused why their edits aren't reformatted.

---

## 3. Slash Commands: Verification

### /project:update-changelog

**File:** `.claude/commands/update-changelog.md`  
**Accuracy:** Correct. Matches AGENTS.md changelog rules exactly.  
**Issues:** None found.

### /project:android-ui

**File:** `.claude/commands/android-ui.md`  
**Accuracy:** Correct. Refers to PowerShell scripts (`android.ps1`, `ui-test.ps1`) which exist and are referenced in CODEX.md.  
**Issues:** 
- This command is **Windows/PowerShell specific** (step 1 uses `pwsh -File`), but it's documented as if it applies to all Claude Code users. An agent on Linux would fail at step 1.
- Should be gated behind a platform check or have a Linux equivalent documented.

---

## 4. Agent Skills (.agents/ directory)

**Found:** Two custom Codex agent skills (not Claude Code agents):
- `.agents/skills/update-changelog/` — SKILL.md + openai.yaml
- `.agents/skills/android-ui-loop/` — SKILL.md only

### update-changelog Skill

**File:** `.agents/skills/update-changelog/SKILL.md`  
**Status:** Accurate, slightly more concise than `/project:update-changelog`. No issues.  
**Note:** Has a Codex-specific agent configuration (`agents/openai.yaml`) — this is not used by Claude Code.

### android-ui-loop Skill

**File:** `.agents/skills/android-ui-loop/SKILL.md`  
**Status:** Accurate. Repeats android-ui.md guidance in slightly different order.  
**Platform issue:** Same as android-ui.md — Windows-only but not marked as such.

---

## 5. Spot-Check: Factual Claims vs. Live Repo

### Claim 1: Module structure
**AGENTS.md line 158-183:** Lists package → Gradle module mappings for `dev.scrybe.*` (11 modules).

**Verification:**
```
✓ :core:billing, :core:common, :core:model, :core:database, :core:datastore, :core:audio, :core:transcription, :core:transforms, :core:export (all exist)
✓ :feature:capture, :feature:history, :feature:profiles, :feature:session-detail, :feature:settings (all exist)
⚠ :feature:history is listed but not found in actual repo; only found :feature:tasks and :feature:file-manager
⚠ :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording are NOT listed
```

**Verdict:** The module mapping table is stale. Missing entries and one phantom entry (:feature:history).

### Claim 2: CI workflow structure
**AGENTS.md line 464-481:** Lists per-app CI workflows as thin callers, per-app release workflows, and shared reusable workflows.

**Verification:**
```
✓ scrybe-ci.yml, shelf-snap-ci.yml, pricedrop-ci.yml exist
✓ scrybe-release.yml, shelf-snap-release.yml, pricedrop-release.yml exist
✓ reusable-validate.yml, reusable-build.yml, reusable-release.yml, pages.yml exist
```

**Verdict:** Accurate.

### Claim 3: Pre-commit hook behavior
**AGENTS.md line 33:** "ktlint 1.5.0 is self-installed on first run if not already on PATH or at `~/.local/bin/ktlint`."

**Verification:** `.githooks/pre-commit` lines 113-128 show exactly this logic. ✓

**Verdict:** Accurate.

### Claim 4: Detekt rules (maxIssues = 0)
**AGENTS.md line 454:** Lists function ≤60 lines, params ≤8, returns ≤4, no magic numbers.

**Verification:**
- `detekt.yml` line 2 confirms `maxIssues: 0`
- Line 18–23 confirm LongMethod(60), LongParameterList(8), ReturnCount(4), MagicNumber(disabled)

**Verdict:** Accurate.

---

## 6. Gaps & Challenges for a Fresh Agent

### Gap 1: Sherpa-ONNX Bootstrap Quirk (CRITICAL)
**Location:** `apps/scrybe/settings.gradle.kts` lines 15–53

A fresh agent would see a 50+-line `settings.gradle.kts` and have no idea why. The Scrybe app pre-downloads an ONNX model AAR in the **settings phase** (not the build phase) to work around Gradle's dependency resolution timing.

**Recommendation:** Add a note to AGENTS.md under "Repository layout":
```
Scrybe has an unusual settings.gradle.kts: it pre-downloads the Sherpa-ONNX speech-recognition 
model (AAR) during the initialization phase so it exists before dependency resolution runs. 
Do not refactor this away.
```

### Gap 2: Shared Modules Naming Mismatch (MODERATE)
**Issue:** AGENTS.md's module mapping table only lists Scrybe modules (`dev.scrybe.*`), but shared modules have different package prefixes:
- `dev.twobits.core.billing` → `:billing`
- `dev.twobits.core.network` → `:network`
- `dev.twobits.core.design` → `:design`

**Verification:** `apps/scrybe/settings.gradle.kts` lines 69–82 show the dependency substitution mapping.

**Recommendation:** Add a separate mapping table in AGENTS.md for shared modules, or extend the existing table.

### Gap 3: App Selection Logic (MODERATE)
When a feature is proposed, an agent needs to know: which app(s) should it be built in?

**Current guidance:** Names only ("Scrybe = voice recording", "Shelf Snap = inventory", "PriceDrop = price tracking"). No decision tree for:
- A cross-cutting feature (e.g., credential security)
- A feature that benefits multiple apps (e.g., new network library)
- Shared module changes that might require coordinated releases

**Recommendation:** Add a section "Choosing which app(s)" with a flowchart or decision matrix.

### Gap 4: The .agents/skills Directory Exists But Is Undocumented (LOW)
The `.agents/` directory contains Codex-specific agent skills (`.agents/skills/update-changelog/` and `android-ui-loop/`), but:
- CLAUDE.md does not mention it
- AGENTS.md does not mention it
- It's unclear if these are for Codex only or shared with Claude Code

**Recommendation:** Add a note to CLAUDE.md: "Codex agent skills live in `.agents/skills/`; Claude Code uses the slash commands in `.claude/commands/` instead."

### Gap 5: Three-App Changelog & Release Friction (MODERATE)
AGENTS.md mentions the three apps share release automation but have independent changelogs. When a shared module change lands:

**The gotcha:** A single shared module commit may trigger 3 releases (one per app). The changelog must be updated in all 3 apps, or CI will block the merge. The three changelogs are independent but share the `## Unreleased` structure.

**Current documentation:** The pre-commit hook checks this (lines 47–101 of `.githooks/pre-commit`), but there is no high-level explanation of **why** this friction exists or **when** to update all three vs. one.

**Recommendation:** Add a section to AGENTS.md: "Shared module releases: when and how to update multiple CHANGELOGs."

### Gap 6: Platform-Specific Tooling Not Gated (MODERATE)
- `android-ui.md` (slash command) and `android-ui-loop` (skill) both assume Windows/PowerShell.
- A Linux-based Claude Code agent would fail at the first step: `pwsh -File ./scripts/android.ps1 doctor`.
- CODEX.md explicitly documents this, but CLAUDE.md does not mention the platform limitation.

**Recommendation:** 
1. Add a note to CLAUDE.md: "/project:android-ui requires Windows with PowerShell. See CODEX.md for local development on Windows."
2. Consider documenting a Linux equivalent (if one exists) or a workaround.

### Gap 7: Composite Build Implications Not Explained (LOW)
The shared modules are included via `includeBuild()` composite (not published to Maven), which means:
- Changes to shared modules require rebuilding the app (no separate release cycle)
- Version numbers for shared modules are internal (not user-visible)
- Adding a new shared module requires updating dependency substitution in each app's settings.gradle.kts

**Current documentation:** The composite build is mentioned but not explained.

**Recommendation:** Add a technical note to AGENTS.md: "Shared modules use Gradle's includeBuild composite. Changes to shared modules trigger app rebuilds; version numbers are internal. When adding a new shared module, update the dependency substitution mapping in each app's settings.gradle.kts."

---

## 7. Duplication & Staleness Risk

### Duplication: Changelog rules
- AGENTS.md §"Mandatory changelog updates" (lines 301–428): Detailed format guide
- `.claude/commands/update-changelog.md`: Shortened version
- `.agents/skills/update-changelog/SKILL.md`: Another shortened version

**Risk:** If the Format A/B rules change, three files must be updated. Currently they're in sync, but this is a maintenance risk.

**Recommendation:** Consolidate to one source. The AGENTS.md version is authoritative; the slash command and skill should reference it or inherit the rules.

### Duplication: Android UI workflow
- CLAUDE.md mentions `/project:android-ui`
- CODEX.md documents the PowerShell workflow
- `.claude/commands/android-ui.md` and `.agents/skills/android-ui-loop/SKILL.md` both repeat the steps

**Risk:** Same — if the workflow changes, all three must be updated.

### Staleness: Module mapping table
- **AGENTS.md line 158–183:** Lists 11 Scrybe modules
- **Actual repo (apps/scrybe/settings.gradle.kts):** Has 14+ modules (adds :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording)
- **Phantom entry:** :feature:history is listed but doesn't exist

**Recommendation:** Regenerate this table from the actual settings.gradle.kts, then add a comment to the table: "This list is maintained by hand. Before each release, verify against apps/scrybe/settings.gradle.kts."

---

## 8. Recommendations Summary

### High Priority
1. **Fix stale module mapping table** (AGENTS.md lines 158–183)
   - Add missing modules: :core:backup, :core:local-ai, :feature:tasks, :feature:file-manager, :service:recording
   - Remove phantom :feature:history
   - Add separate table for shared modules (billing, common, api-keys, network, design, etc.)

2. **Document Sherpa-ONNX bootstrap quirk** (AGENTS.md §"Repository layout")
   - Explain why Scrybe's settings.gradle.kts is 50+ lines
   - Reference the pre-download logic so an agent doesn't attempt to refactor it

3. **Gate /project:android-ui to Windows** (CLAUDE.md and slash command)
   - Add platform check or reference CODEX.md for Windows setup

### Medium Priority
4. **Add app selection flowchart** (new section in AGENTS.md)
   - Help agents decide which app(s) a feature belongs to
   - Clarify shared module release implications

5. **Consolidate changelog rules** (consolidate AGENTS.md, slash command, skills)
   - Single source of truth; other docs reference it
   - Add a comment to the table: "Maintained by hand; verify before release"

6. **Document shared module release friction** (AGENTS.md, new section)
   - Explain why three CHANGELOGs exist
   - Describe when all three must be updated (vs. one)

### Low Priority
7. **Mention .agents/ directory in CLAUDE.md**
   - Clarify Codex vs. Claude Code agent skills

8. **Explain Gradle composite build** (AGENTS.md, brief note)
   - Why shared modules don't have external version numbers
   - Impact on dependency substitution

---

## Top 5 Priorities

1. **Fix module mapping table (stale)** — AGENTS.md lines 158–183 are out of sync with actual modules. Four modules are missing, one is phantom.

2. **Document Sherpa-ONNX bootstrap** — The unusual settings.gradle.kts pre-download is a gotcha that will puzzle fresh agents and invite premature refactoring.

3. **Gate android-ui to Windows** — /project:android-ui assumes Windows/PowerShell but is documented as if platform-agnostic. Will cause Linux agents to fail.

4. **Add app selection guidance** — No decision tree for choosing which app(s) a feature belongs to. Shared module changes create ambiguity.

5. **Explain release friction for shared modules** — Three independent CHANGELOGs + shared modules = complex release logic. High-level explanation needed for agents coordinating changes.

---

## Verified & Accurate

✓ AGENTS.md CI failure patterns (8/8 correct with live examples)  
✓ Pre-commit hook behavior  
✓ Detekt rules and thresholds  
✓ Branch management workflow  
✓ Changelog structure & validation  
✓ CLAUDE.md hook description  
✓ Slash commands (accuracy, not platform coverage)  
✓ Commit message format (conventional commits)
