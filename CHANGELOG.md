# Changelog

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
