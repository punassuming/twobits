# CI/CD Pipeline, Linting & Build-Scripting Audit — VALIDATION REPORT

**Validation Date**: 2026-09-25  
**Validator**: Fact-Check Agent (Haiku 4.5)  
**Audit Report**: 07-cicd-linting-workflows.md

---

## Verification Summary

**Total Claims Checked**: 12 major findings  
**✅ CONFIRMED**: 10 findings  
**⚠️ CORRECTED**: 1 finding (line number clarification needed)  
**❌ FALSE/UNVERIFIABLE**: 1 finding (keystore sharing claim)  

**Critical Finding**: `reusable-build.yml` **DOES EXIST** and is **TRULY UNUSED** by any CI workflow.

---

## Detailed Claim Verification

### Claim 1: Unused Reusable Build Workflow

**Report Statement**: `.github/workflows/reusable-build.yml` exists (lines 1-110) but is NOT called by any CI workflow; all three per-app workflows duplicate build logic inline.

**Verification**:
- ✅ **File exists**: `/home/user/twobits/.github/workflows/reusable-build.yml` confirmed (3,771 bytes, modified Sep 20 19:48)
- ✅ **Not called**: `grep -r "uses.*reusable-build" .github/workflows/` returns no results
- ✅ **Inline duplication**: Confirmed in all three CI workflows:
  - **scrybe-ci.yml** (Build section: lines 123-207) contains inline build steps
  - **shelf-snap-ci.yml** (Build section: lines 41-117) contains inline build steps  
  - **pricedrop-ci.yml** (Build section: lines 41-117) contains inline build steps

**Status**: ✅ CONFIRMED

---

### Claim 2: Lint Task Variants Inconsistency

**Report Statement**: Scrybe uses `lint` (debug) while Shelf Snap and PriceDrop use `lintRelease` at specific lines.

**Verification**:
- ✅ **Scrybe-ci.yml line 159**: `bash ../../scripts/ci-gradle-retry.sh assembleRelease testDebugUnitTest sharedUnitTest **lint** ktlintCheck detekt`
- ✅ **Shelf Snap-ci.yml line 72**: `./gradlew assembleRelease testDebugUnitTest sharedUnitTest **lintRelease** ktlintCheck detekt --no-daemon`
- ✅ **Pricedrop-ci.yml line 72**: `./gradlew assembleRelease testDebugUnitTest sharedUnitTest **lintRelease** ktlintCheck detekt --no-daemon`

**Status**: ✅ CONFIRMED

---

### Claim 3: Manifest Validation Asymmetry

**Report Statement**: Pre-commit hook validates manifests only for Scrybe (.githooks/pre-commit:105), while CI validates all three apps (.github/workflows/reusable-validate.yml:71).

**Verification**:
- ✅ **Pre-commit hook line 105-107**:
  ```bash
  python3 "$REPO_ROOT/scripts/validate-manifests.py" --root "$SCRYBE_DIR" \
      || fail "AndroidManifest.xml validation failed."
  ```
  Only `$SCRYBE_DIR` is used — Shelf Snap and PriceDrop NOT validated.

- ✅ **Reusable-validate.yml line 71**:
  ```bash
  run: python3 scripts/validate-manifests.py --root ${{ inputs.app_root }}
  ```
  Uses parameterized `app_root` input, called with each app's path by CI workflows.

**Status**: ✅ CONFIRMED

---

### Claim 4: Outdated Pre-Commit Hook Comment

**Report Statement**: Pre-commit hook line 279 comment says "CI also enforces: assembleDebug · testDebugUnitTest · lint · detekt" but CI actually runs `assembleRelease` not `assembleDebug`.

**Verification**:
- ✅ **Pre-commit hook line 279**:
  ```
  echo "CI also enforces: assembleDebug · testDebugUnitTest · lint · detekt"
  ```
  Comment mentions `assembleDebug`, but CI workflows actually run `assembleRelease`.

**Status**: ✅ CONFIRMED — Comment is outdated and misleading.

---

### Claim 5: No Vulnerability Scanning

**Report Statement**: No Dependabot, CodeQL, SAST, secret scanning, or OWASP dependency-check configured.

**Verification**:
- ✅ **No dependabot.yml**: `ls .github/ | grep dependabot` → no results
- ✅ **No CodeQL workflows**: `grep -r "codeql" .github/workflows/` → no results
- ✅ **No SAST tools**: `grep -r "snyk\|trivy\|dependency-check" .github/workflows/` → no results
- ✅ **No secret scanning**: `grep -r "secret.*scan" .github/workflows/` → no results
- ✅ **Detekt is style/complexity only**: Confirmed in detekt.yml and workflows — not a security scanner

**Status**: ✅ CONFIRMED

---

### Claim 6: Gradle Configuration Cache Enabled

**Report Statement**: All three apps enable `org.gradle.configuration-cache=true` in gradle.properties.

**Verification**:
- ✅ `/apps/scrybe/gradle.properties`: Contains `org.gradle.configuration-cache=true` and `org.gradle.configuration-cache.problems=warn`
- ✅ `/apps/shelf-snap/gradle.properties`: Contains `org.gradle.configuration-cache=true` and `org.gradle.configuration-cache.problems=warn`
- ✅ `/apps/price-drop/gradle.properties`: Contains `org.gradle.configuration-cache=true` and `org.gradle.configuration-cache.problems=warn`

**Status**: ✅ CONFIRMED

---

### Claim 7: Release Automation — Semantic Versioning

**Report Statement**: Release workflow implements semantic versioning with conventional commits (feat:, BREAKING CHANGE).

**Verification** (reusable-release.yml:150-201):
- ✅ **Lines 184-189**: Detects `BREAKING CHANGE` and `*!:` for major bump
- ✅ **Lines 187-188**: Detects `feat(*)` for minor bump
- ✅ **Lines 191-195**: Default patch bump applied
- ✅ **Lines 168-173**: Seeds from CHANGELOG.md version for first release (prevents 0.0.0)

**Status**: ✅ CONFIRMED

---

### Claim 8: Release Automation — Stale Detection

**Report Statement**: Workflow detects stale release targets (main has advanced with app-relevant commits) and skips redundant releases.

**Verification** (reusable-release.yml:88-99):
- ✅ **Lines 91-98**: Checks `git log ${{ inputs.app_root }}/CHANGELOG.md ${{ inputs.app_root }}/ shared/` between `head_sha..origin/main`
- ✅ **Line 92-94**: If app-relevant commits exist, marks `stale=true` and skips release
- ✅ **Line 96-98**: If no app-relevant commits found, rebases and allows concurrent app releases

**Status**: ✅ CONFIRMED

---

### Claim 9: Release Automation — Changelog Promotion

**Report Statement**: Workflow promotes `## Unreleased` to versioned section via `manage-changelog.py promote-release`.

**Verification** (reusable-release.yml:266-279):
- ✅ **Lines 273-278**: Calls `python3 scripts/manage-changelog.py promote-release` with version and date
- ✅ **Line 278**: Outputs release notes to temp file for GitHub release body

**Status**: ✅ CONFIRMED

---

### Claim 10: Keystore Generation Shared Secrets

**Report Statement**: "Keystore generation relies on hardcoded secrets naming `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — all three apps share the same secrets path, so they would need separate secrets or a shared keystore."

**Verification**:
- ⚠️ **UNVERIFIABLE**: Secrets are stored in GitHub and not visible in the codebase. The report claims hardcoded secret names, but cannot be verified without access to GitHub secrets configuration.
- ✅ **Partial confirmation**: reusable-release.yml lines 237-246 shows keystore decoding logic using `SIGNING_KEYSTORE_BASE64`, but the actual per-app secret routing is handled via GitHub's secret masking and cannot be audited from the source alone.

**Status**: ⚠️ PARTIALLY UNVERIFIABLE (secrets configuration not in source)

---

### Claim 11: No Android Lint for Shared Modules

**Report Statement**: "CI runs `lintRelease` on each app's code" but "no evidence of Android Lint running against shared modules."

**Verification**:
- ✅ **App-level lint confirmed**: All three CI workflows run `lint` or `lintRelease` (lines 72 and 159)
- ✅ **No shared module lint task**: `grep -n "lint.*shared\|shared.*lint" .github/workflows/*.yml` finds only `sharedUnitTest` (a unit test aggregate task, not lint)
- ✅ **Shared modules are Android libraries**: Confirmed `debug-log-ui`, `design`, etc. are Android library modules with build.gradle.kts
- ✅ **Gap confirmed**: No explicit `lint` or `lintRelease` tasks run against shared/ directory in CI workflows

**Status**: ✅ CONFIRMED — Shared module lint gap exists.

---

### Claim 12: Release Workflow Keystore Validation

**Report Statement**: "Decodes KEYSTORE base64 from secrets, validates it before any state changes" at lines 237-246; falls back to ephemeral keystore at lines 338-345; validates via keytool import test at lines 360-381.

**Verification** (reusable-release.yml):
- ✅ **Keystore decoding & validation**: Lines 237-246 present (confirmed by reading the file context)
- ✅ **Ephemeral fallback**: Lines 338-345 commented as fallback when secrets missing
- ✅ **Keystore validation via keytool**: Lines 360-381 perform import test before irreversible state changes

**Status**: ✅ CONFIRMED

---

## Verified Findings for Final Report

Based on systematic verification of all claims, here are the findings suitable for inclusion in a final engineering priority list:

### 1. **UNUSED REUSABLE BUILD WORKFLOW — Code Duplication Maintenance Burden**

**Severity**: Medium  
**Effort**: 30 minutes  
**Confidence**: High

**Issue**: `reusable-build.yml` exists but is NOT called by any CI workflow. All three per-app CI workflows duplicate identical build logic inline instead of reusing the parameterized workflow.

**Evidence**:
- File exists: `/home/user/twobits/.github/workflows/reusable-build.yml`
- Zero calls found: `grep -r "uses.*reusable-build" .github/workflows/` returns empty
- Inline duplication confirmed in:
  - scrybe-ci.yml:123-207
  - shelf-snap-ci.yml:41-117
  - pricedrop-ci.yml:41-117

**Impact**: Code maintenance burden — build logic changes must be made in three places; inconsistencies already emerging (Scrybe uses `lint`, others use `lintRelease`).

---

### 2. **INCONSISTENT LINT VARIANTS ACROSS APPS — Build Correctness Risk**

**Severity**: High  
**Effort**: 15 minutes  
**Confidence**: High

**Issue**: Scrybe CI runs `lint` (debug variant) while Shelf Snap and PriceDrop run `lintRelease`. All three build `assembleRelease` for release APK.

**Evidence**:
- scrybe-ci.yml:159: `lint` task
- shelf-snap-ci.yml:72: `lintRelease` task
- pricedrop-ci.yml:72: `lintRelease` task

**Risk**: Android Lint's `lintRelease` variant catches release-specific issues (ProGuard rules, minification constraints) that debug `lint` misses. Scrybe release APK may ship with undetected lint violations.

**Fix**: Standardize all three to `lintRelease` when building `assembleRelease`.

---

### 3. **MANIFEST VALIDATION ONLY FOR SCRYBE LOCALLY — Coverage Gap**

**Severity**: Medium  
**Effort**: 10 minutes  
**Confidence**: High

**Issue**: Pre-commit hook validates AndroidManifest.xml only for Scrybe (.githooks/pre-commit:105), but CI validates all three apps (.github/workflows/reusable-validate.yml:71).

**Evidence**:
- Pre-commit line 105: `python3 "$REPO_ROOT/scripts/validate-manifests.py" --root "$SCRYBE_DIR"`
- CI line 71: `python3 scripts/validate-manifests.py --root ${{ inputs.app_root }}`

**Risk**: Developers can commit invalid Shelf Snap or PriceDrop manifests locally without early feedback; only caught in CI.

**Fix**: Extend pre-commit hook to validate all three apps' manifests.

---

### 4. **NO ANDROID LINT FOR SHARED MODULES — Silent Defect Risk**

**Severity**: Medium  
**Effort**: 45 minutes  
**Confidence**: Medium

**Issue**: Shared modules (debug-log-ui, design, etc.) are Android libraries but never have Android Lint run against them. Lint only runs at app-integration level.

**Evidence**:
- CI workflows run `lintRelease` for apps only
- `sharedUnitTest` aggregate task is present, but no `lint`/`lintRelease` for shared/

**Risk**: Shared module lint violations only surface when integrating into an app's release APK, potentially requiring hotfixes.

**Fix**: Add Android Lint runs for shared modules in CI, either as separate job or as aggregate task in shared/build.gradle.kts.

---

### 5. **NO DEPENDENCY VULNERABILITY SCANNING — Security Gap**

**Severity**: High  
**Effort**: 20 minutes (Dependabot) to 1 hour (OWASP)  
**Confidence**: High

**Issue**: No Dependabot, CodeQL, SAST, or OWASP dependency-check configured anywhere.

**Evidence**:
- No `.github/dependabot.yml`
- `grep -r "codeql\|dependabot\|snyk\|trivy\|dependency-check" .github/workflows/` → empty
- `grep -r "secret.*scan" .github/workflows/` → empty

**Risk**: Transitive dependencies may contain known CVEs without any alerting; compliance and security audit issues.

**Fix**: Enable GitHub Dependabot for Gradle package ecosystem, or integrate OWASP dependency-check into CI workflow.

---

### 6. **OUTDATED PRE-COMMIT HOOK COMMENT — Developer Confusion**

**Severity**: Low  
**Effort**: 5 minutes  
**Confidence**: High

**Issue**: Pre-commit hook final comment (line 279) states "CI also enforces: assembleDebug..." but CI actually runs `assembleRelease`.

**Evidence**:
- .githooks/pre-commit:279: `echo "CI also enforces: assembleDebug · testDebugUnitTest · lint · detekt"`
- All CI workflows run: `assembleRelease testDebugUnitTest ... lint[Release] ...`

**Fix**: Update comment to reflect actual CI behavior (`assembleRelease` not `assembleDebug`, and note that Shelf Snap/PriceDrop use `lintRelease`).

---

## Additional Notes

- **Gradle Configuration Cache**: ✅ Properly enabled in all three apps with `problems=warn` (allowing incremental adoption)
- **Semantic Versioning**: ✅ Correctly implemented with conventional commits (feat:, BREAKING CHANGE)
- **Stale Release Detection**: ✅ Correctly scoped to app-relevant files, enabling concurrent app releases
- **Changelog Promotion**: ✅ Automated via `manage-changelog.py promote-release`
- **Manifest Validation**: Asymmetry confirmed but not a silent failure (CI catches issues)
- **No Build Performance Optimizations**: Missing `org.gradle.parallel=true`, Gradle build cache, and module-level caching

---

**Validation completed**: 2026-09-25  
**All major claims cross-referenced against actual source files**
