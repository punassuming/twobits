# Changelog

## Unreleased

### Features

### Improvements

### Fixes

## 0.14.0 (2026-05-03)

### Features

### Improvements

* remove swipe-right (Transform) gesture from recording rows; only swipe-left (Archive/Restore) is active — Transform is complex enough to warrant an intentional tap from the row menu
* remove labels from bottom navigation bar and increase icon size to 28 dp for a cleaner, more compact chrome
* increase waveform visualiser height on Capture screen from 72 dp to 120 dp for better amplitude detail
* change playback position playhead colour to a neutral `onSurface` grey so it does not compete visually with the coloured waveform bars
* move filter and search out of the always-visible section card; filter is now a badge icon button in the TopAppBar (badge shows active filter count); search is a toggle that slides down an `OutlinedTextField` above the list
* replace `Checkbox` widget in multi-select with an in-place icon swap (CheckCircle / RadioButtonUnchecked) so row content does not shift when selection mode activates
* replace folder drill-in navigation with inline expand/collapse in the history list; tapping a folder row expands its sessions inline with a chevron rotation animation; no separate navigation state required
* replace SwipeToDismissBox with a custom SwipeRevealRow that holds revealed action buttons until tapped (iOS-style); swipe left reveals Archive/Restore, swipe right reveals Transform; buttons snap into place with a spring animation
* add Transform picker bottom sheet: tapping Transform (via swipe-right, row ⋮ menu, or multi-select overflow) opens a ModalBottomSheet listing all transform profiles with Run buttons, a progress indicator while running, and a result preview with Copy/Done actions
* rename "Run Default Transform" row ⋮ menu item to "Transform…" and route it through the picker sheet
* add "Save to session" button to the Session Detail transform result dialog; saves the transformed text as the session's edited transcript
* add `maxLines = 1` + ellipsis overflow to Profiles screen "New Profile" and "AI Draft" buttons to prevent text wrapping on narrow screens
* add Whisper vs Gemma explanation paragraph to the Local provider card in Settings so users understand which model handles transcription vs AI features

### Fixes


## 0.13.0 (2026-05-03)

### Features

### Improvements

* use minor as the default version bump so any logged CHANGELOG entry releases as a minor version rather than a patch

### Fixes



## 0.12.1 (2026-05-02)

### Features

### Improvements

* move Settings from CaptureScreen top bar icon to a permanent fourth bottom NavigationBar item, making it reachable in one tap from any top-level screen
* remove History, Profiles, and Settings icon buttons from CaptureScreen top bar; all top-level navigation is now handled exclusively by the bottom bar
* add slide-in/slide-out page transitions for hierarchical navigation (SessionDetail, FileManager) and fade transitions for peer tab switches
* replace edge-only swipe gestures with M3 `SwipeToDismissBox`; swipe right to transform, swipe left to archive or restore; completed swipes show a Snackbar with Undo instead of a blocking confirmation dialog
* collapse HistoryScreen top bar actions into a three-icon layout (two primary + overflow menu) in both normal and selection mode; selection mode bar adopts the M3 contextual app bar background colour
* remove the Record FAB from HistoryScreen; recording is initiated from the Capture tab accessible via the bottom bar

### Fixes

* fix Local provider card in Settings permanently hiding the Whisper download button; the download section is now always visible so users can download Whisper before selecting the Local provider
* fix CaptureScreen build failure caused by stale `onNavigateToHistory` reference left in `RecentRecordingsSection` after nav refactor; removed redundant View All button (History tab in bottom nav replaces it)
* delete `RecordSwipeSafetyTest` which tested the removed swipe confirmation dialog system




## 0.12.0 (2026-05-01)

### Features

* add dedicated File Manager screen accessible from Settings with recording inventory, orphan detection and one-tap import, per-session bundle export (audio + markdown transcript named after recording title), and saved-copy/export management

### Improvements

* split release APK by ABI into arm64-v8a (modern phones) and armeabi-v7a (Android 7-era devices); both APKs are uploaded to GitHub Releases, cutting per-device download size roughly in half compared to a fat universal APK

### Fixes

* fix exported files and saved audio copies using UUID-based machine filenames; all exports and saved copies now use the user-visible recording title via `sanitizeFileName(session.title)`





## 0.11.0 (2026-04-30)

### Features

* add on-device transcription using Whisper tiny (via Sherpa-ONNX) and on-device transforms, rename, clustering, and tag suggestions using Gemma (via MediaPipe); all AI capabilities route to local or remote based on the active provider selection
* add Gemma model picker in Settings → Provider → Local with GPU (INT4, ~1.3 GB) and CPU (INT8, ~2.3 GB) variants; selected model is persisted and used across all local AI operations
* add dedicated File Manager screen accessible from Settings with recording inventory, orphan detection and one-tap import, per-session bundle export (audio + markdown transcript), and saved-copy/export management

### Improvements

* enable Local provider selection in Settings once the Whisper model is downloaded; provider card shows per-model download progress, size, and delete controls
* split release APK by ABI into arm64-v8a (modern phones) and armeabi-v7a (Android 7-era devices); both APKs are uploaded to GitHub Releases, cutting per-device download size roughly in half compared to a fat universal APK
* switch sherpa-onnx-android to v1.13.0 prebuilt AAR downloaded via curl from GitHub Releases at build time; the library is not published to JitPack or Maven Central
* harden pre-commit hook: add external dependency URL reachability check, detekt on staged `.kt` sources, and Java stdlib import completeness for `.kts` build scripts

### Fixes

* fix sherpa-onnx dependency for library modules: download AAR to a local Maven directory in `settings.gradle.kts` and declare as a Maven coordinate (`com.k2fsa:sherpa-onnx-android:1.13.0`); AGP forbids direct local `.aar` file deps in library modules
* fix exported files and saved audio copies using UUID-based machine filenames; all exports and saved copies now use the user-visible recording title via `sanitizeFileName(session.title)`





## 0.10.0 (2026-04-27)

### Features

* add AI auto-rename to suggest recording titles from transcripts; available per-recording via the row menu (when a transcript exists) and in bulk via the selection toolbar
* scope AI auto-folder clustering to selected recordings when a selection is active, leaving unselected recordings untouched

### Improvements

* remove duplicate CI runs by restricting the push trigger to main only; pull_request handles feature branches, push handles post-merge verification on main
* show a LinearProgressIndicator during AI workloads (clustering, bulk rename) and a spinner inside the AI profile draft button while generating
* fix profile editor dialog losing typed content and save button on landscape rotation by lifting state to ViewModel and switching to a scrollable full-width Dialog
* pre-commit hook now verifies that coroutine flow extension functions (asStateFlow, asSharedFlow, etc.) have matching imports in staged Kotlin files, catching missing-import compile errors before they reach CI
* pre-commit hook now enforces a changelog update whenever tracked code files are staged, matching the CI changelog gate so the check fires locally before push

### Fixes

* fix CI compile failure caused by missing `import kotlinx.coroutines.flow.asStateFlow` in HistoryViewModel
* restore `as ProfilesUiState` cast in ProfilesViewModel; the cast is required to widen the map-block return type so the downstream `.catch { emit(ProfilesUiState.Error(...)) }` type-checks — the earlier "No cast needed" compiler warning was misleading







## 0.9.1 (2026-04-25)

### Features

* add folder context menu (long-press ⋮ button) with Rename, Delete, and Move to… actions; rename and delete were already implemented in the ViewModel but had no UI entry points
* add move-folder capability with cycle-detection guard so a folder cannot be reparented into itself or any of its descendants

### Improvements

* Extend Android CI to run on `claude/**` branches in addition to `copilot/**`
* Strengthen pre-commit hook to run standalone ktlint on staged files without requiring an Android SDK installation; add import-ordering rule to all agent instruction files
* Add tracked `.githooks/pre-commit` with self-installing ktlint so Copilot and other agents enforce Kotlin formatting on every commit without needing an Android SDK; document `git config core.hooksPath .githooks` session-setup step in AGENTS.md, CLAUDE.md, and copilot-instructions.md
* Add a bottom navigation bar for faster switching between Record, History, and Profiles
* Add folder management actions in History for rename, move, and delete
* Add swipe edge hint icons on record rows to improve gesture discoverability
* Streamline Settings by splitting recording automation/feedback sections and folding transform model controls into provider configuration

### Fixes

* fix ColumnScope.AnimatedVisibility implicit-receiver ambiguity in ScrybeApp that caused a compile error when AnimatedVisibility was used inside a Box nested within a Column
* fix KtLint `multiline-expression-wrapping` violation in ScrybeApp that was breaking Android CI after the bottom nav bar addition








## 0.9.0 (2026-04-18)

### Features

* add hierarchical recording folders with breadcrumb navigation, move-to-folder actions, and AI-powered clustering to organize sessions

### Improvements

* polish capture and settings interactions with richer recording start/stop feedback, inline audio format controls, and configurable external app intent integration
* refine session workflows with transform result copy/share dialogs, clearer loading/status indicators, and an upgraded saved-files browser

### Fixes

* restore main deployment by aligning release tags with the committed changelog and app version metadata
* restore Android CI by fixing `CaptureScreen` coroutine launch usage and declaring vibration permission for recording feedback
* fix a `CaptureScreen` compilation issue that affected record-button animation handling in Android CI









## 0.8.3 (2026-04-12)

### Features

### Improvements

* align capture, records, profiles, settings, and session review screens around a shared spacing and card layout system to reduce dead space and make navigation feel more consistent

### Fixes










## 0.8.2 (2026-04-11)

### Features

### Improvements

* refresh the capture and records screens with a shorter home header subtitle, icon-based recent recording badges, a top-level filters card, a true single-line search field, and a Record button that returns to the home recorder
* default new recordings to skip the automatic rename prompt now that titles can be edited directly from the session header
* speed up GitHub Android CI by consolidating verification into one job and keeping release APK assembly in the release workflow

### Fixes

* make records swipe actions less sensitive with narrower edge-only gestures, one-way swipe directions, and full-card action overlays instead of sliding row content sideways











## 0.8.1 (2026-04-07)

### Features

* add import recordings button in the records screen and top bar to bring in audio files from external storage
* display clickable file path link in session detail overview card so users can quickly locate or copy the audio file path

### Improvements

* auto-recover orphaned recording files on startup by scanning the recordings directory for audio files that lost their database entries

### Fixes

* fix compile error caused by referencing non-existent `MediaMetadataRetriever.METADATA_KEY_CHANNEL_COUNT`; channel count is now extracted via `MediaExtractor` and `MediaFormat.KEY_CHANNEL_COUNT`
* replace destructive database migration with a proper schema migration so upgrading no longer silently deletes all previous recordings












## 0.8.0 (2026-04-06)

### Features

* consolidate multiple selected transcripts into one default-profile transform output so related recordings can be summarized together
* add recording tags with manual editing, AI tag suggestions, and tag-aware search across Records and session details

### Improvements

### Fixes













## 0.7.0 (2026-04-02)

### Features

* allow renaming a recording by tapping its title in the session detail view; an edit icon next to the title signals the affordance and opens the rename dialog
* add "Share Transcript" option to the recordings list item menu; tap to send the transcript text to any app via the Android share sheet
* add "Show recording information in list" toggle in Settings → Recording Behavior to hide or reveal the metadata line (format, sample rate, bitrate, channels) in the recordings list

### Improvements

* add Kotlin, Min SDK, Target SDK, and License badges to the README

### Fixes














## 0.6.0 (2026-03-30)

### Features

* consolidate multiple selected transcripts into one default-profile transform output and add searchable recording tags with AI tag suggestions

### Improvements

* add a repo-root PowerShell helper for emulator, build, install, run, lint, test, format, detekt, and full local verification commands
* switch AI profile draft suggestions to `gpt-5-mini` while leaving the normal transform pipeline unchanged
* let Settings choose and test the OpenAI model used for AI profile drafts before generation starts
* streamline the capture home screen with a Material-style top app bar, a persistent waveform above the record button, and scrollable recent recordings that stay visible again
* reshape the Settings usage section into a full-width metric grid and add active, archived, average-length, export, and saved-copy stats
### Fixes

* restrict recordings swipe gestures to the left and right edge lanes so center drags no longer trigger transform or archive actions
* raise OpenAI upload timeouts, chunk oversized recordings before transcription, and surface short-recording failures instead of crashing on a fast stop
* skip release promotion cleanly when `CHANGELOG.md` has no unreleased bullets instead of failing the release workflow
* retry CI Gradle tasks when Maven Central denies a transient dependency fetch instead of failing the workflow on the first 403















## 0.5.1 (2026-03-29)

### Features

### Improvements

### Fixes















## 0.4.0 (2026-03-29)

### Features

* require `CHANGELOG.md` updates before pull requests or pushes targeting `main`, and automatically promote those notes into the next tagged release

### Improvements

* replace the local Windows Gradle helper flow with a repo-root environment bootstrap that keeps direct `gradlew.bat` runs visible on stdout
* refine the recording and playback waveforms with denser samples, slimmer bars, a mirrored live capture view, and direct waveform scrubbing in session review
* streamline the records screen with a more compact filter bar, dedicated left and right swipe actions, and tighter playback and transcript layouts

### Fixes

* restore the Android verification suite on the latest head by wiring KtLint and Detekt into the Gradle build and resolving the compile regressions they exposed
* separate manual profile editing from AI-generated profile drafting so profile creation has clearer, more informative flows

















## 0.3.1 (2026-03-26)

### Features

* reorganize provider settings so the active transcription provider owns its credentials and setup guidance directly in Settings
* add clearer option summaries for recording format, sample rate, bitrate, and channel layout in Settings

### Fixes

* require confirmation before swipe-to-transform, archive, or restore actions so record gestures are harder to trigger accidentally

















## 0.3.0 (2026-03-26)

### Features

* add bulk transcription actions in History so multiple eligible recordings can be queued together
* expose archived recordings more clearly and support restoring them from History and session detail

### Improvements

* show status-specific icons across the records list so pending, running, completed, and failed transcription states are easier to scan

### Fixes

* add retry and reset recovery actions for recordings stuck in a transcription state
* restore the missing transcription module dependency that was breaking Android builds

















## 0.2.0 (2026-03-26)

### Features

* add OpenAI-powered profile suggestions to speed up transform profile creation
* refresh the capture, history, profiles, and session detail flows with broader next-generation UI and workflow updates

### Improvements

* improve session playback and review interactions so recordings and derived outputs are easier to inspect
* document the local Gradle runner workflow for Windows development

### Fixes

* resolve Android build regressions caused by small UI and project wiring issues
* harden the release workflow so mainline releases are more reliable

















## 0.1.0 (2026-03-25)

### Improvements

* optimize Android CI and release automation so validation and release steps run more predictably
* add a local PowerShell Gradle helper to make Windows builds easier to run outside CI

















## 0.0.10 (2026-03-25)

### Fixes

* clean up foreground recording notification refresh logic to resolve the build issues introduced after 0.0.9

















## 0.0.9 (2026-03-25)

### Fixes

* centralize live recording notification refreshes behind a dedicated helper so Android 13+ permission checks stay lint-clean and safe at runtime
* keep elapsed-time and input-level notification updates working when notification permission is available without tripping `MissingPermission` warnings

















## 0.0.8 (2026-03-25)

### Fixes

* add an Android 13+ `POST_NOTIFICATIONS` runtime permission check before refreshing the foreground recording notification
* prevent recording notification updates from failing lint or unsafe notification calls on devices that have not granted notification permission

















## 0.0.7 (2026-03-24)

### Features

* expand the home recording flow with a reactive waveform, richer active-recording visuals, and a recent records section directly on the capture screen
* add smarter post-recording navigation so Scrybe can return home or jump straight into session review after a recording is saved
* upgrade the records screen with re-recording, multi-select actions, swipe shortcuts, archiving, restore, and one-tap default transforms
* allow transcript editing from session review while preserving the original machine transcript for comparison

### Improvements

* enrich the foreground recording notification with live elapsed time and simple input-level feedback
* surface session status, archive state, transcript previews, and clearer metadata across the records list and session detail screens
* add estimated transcription cost visibility in session detail and aggregate transcription spend reporting in Settings

















## 0.0.6 (2026-03-23)

### Features

* validate OpenAI API keys before saving and show live connection status in Settings
* expose the recorded audio asset in session review and allow sharing the original file
* upgrade the recording notification and launcher icon with a more deliberate AI-plus-audio visual treatment

### Improvements

* increase recorder quality with a higher sample rate and bitrate for cleaner captures
* organize transcripts into transcription and transformation sections with expandable cards
* add more ambient motion and richer product copy to the capture screen

### Fixes

* keep only one canonical raw transcript per session instead of stacking duplicate retranscriptions
* make release notes surface the latest update as a concise in-app summary for version 0.0.6

















## 0.0.5 (2026-03-23)

### Features

* add a custom Scrybe launcher icon that combines recording, AI, and signal motifs
* overhaul the home screen recording control with layered circular states and centered quick actions
* add concise release notes with automatic update popup behavior when the app version changes

### Improvements

* switch session review actions and transform choices to organized list-style controls instead of pill-heavy layouts
* tighten profile list density with truncated descriptions and prompt previews
* keep default profiles visually highlighted with a badge instead of tinting the whole card

### Fixes

* keep auto-transcription alive after the recording service shuts down
* send valid typed content payloads to the OpenAI responses API for transforms

















## [1.1.0](https://github.com/punassuming/scrybe/compare/scrybe-v1.0.0...scrybe-v1.1.0) (2026-03-10)


### Features

* add Android project skeleton for scrybe-android ([d7cd14f](https://github.com/punassuming/scrybe/commit/d7cd14febfa40258df2c8881baa199f826d82396))
* add automated semantic release workflow using Release Please ([a72bbdc](https://github.com/punassuming/scrybe/commit/a72bbdc3980c3cdc3cf4791e867e3b6723dca584))
* add dockerized android dev environment ([b1adef6](https://github.com/punassuming/scrybe/commit/b1adef6cc7e3911ca285677fe453cba6dd271442))
* automated semantic versioning and GitHub releases via Release Please ([901d932](https://github.com/punassuming/scrybe/commit/901d932ef7a13ac9af6c53196b60280b76134d2f))


### Bug Fixes

* add explicit permissions: contents: read to all CI jobs ([6a23faa](https://github.com/punassuming/scrybe/commit/6a23faa30a869412a974c088b17444fcae9b5346))
* add missing xmlns:android namespace to recording service AndroidManifest.xml ([a3af022](https://github.com/punassuming/scrybe/commit/a3af022d61c01b0ebb6626f094ff6fe655ef35dd))
* add missing xmlns:android namespace to recording service manifest and add CI validation ([6ca8fb1](https://github.com/punassuming/scrybe/commit/6ca8fb128cfa32d606fac6ca2d9a9e01cd3945fd))
* fall back to github.token when RELEASE_TOKEN secret is absent ([d322ff5](https://github.com/punassuming/scrybe/commit/d322ff5ec97bb1f4fc5753a7f50d04f1e32cf4da))
* fall back to github.token when RELEASE_TOKEN secret is absent ([1e3a617](https://github.com/punassuming/scrybe/commit/1e3a6175f62b86de365efc2f5194fda9fef072db))
* provision Android SDK dependencies in Docker and CI ([b818043](https://github.com/punassuming/scrybe/commit/b818043b072fbfcf8a3abf1d1542f835a400cd69))
* use classic PAT for release-please to allow PR creation ([26da0ef](https://github.com/punassuming/scrybe/commit/26da0efa5140b361bf61b90ad757ce304baec871))
* use classic PAT for release-please to unblock PR creation ([d6190a6](https://github.com/punassuming/scrybe/commit/d6190a6a94a88a5613ce77b8329829d038c33d02))
* use coroutines-core in JVM module and disable Room exportSchema ([277eeb5](https://github.com/punassuming/scrybe/commit/277eeb5f54c2ec40196941408bb7cf87379b0934))
* use GITHUB_TOKEN for release-please to resolve PAT git-trees permission error ([d01372e](https://github.com/punassuming/scrybe/commit/d01372e8dcec9455ce2dc04f32016e95f4a34c7f))
* use GITHUB_TOKEN instead of PAT for release-please to resolve git trees permission error ([0eed47a](https://github.com/punassuming/scrybe/commit/0eed47a14c7ab26463fd0bc4f2f95afc77d46060))
* use PAT to unblock release-please PR creation ([e97c63a](https://github.com/punassuming/scrybe/commit/e97c63a234cc879090a5f6dfc215c16332460a09))
* use PAT token for release-please to allow PR creation ([55d6910](https://github.com/punassuming/scrybe/commit/55d6910fa3eda7335abbcaf7e28d99aa81e9840e))
