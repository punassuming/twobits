---
name: update-changelog-before-main
description: Update the repo-root `CHANGELOG.md` before any commit, pull request, or push that is headed to `main` in this repository so the in-app release notes, Settings release history, and GitHub releases stay in sync. Use when preparing Android changes for review, merge, or release, especially when files under `apps/android-whispering/`, release workflows, or other shipped behavior changed.
---

# Update Changelog Before Main

Keep the root changelog aligned with shipped behavior before anything lands on `main`. This repo ships `CHANGELOG.md` into the Android app, parses it for the in-app "What's New" dialog and the Settings release history, and promotes `## Unreleased` into a versioned release section during the GitHub release workflow.

## Workflow

1. Edit the top `## Unreleased` section in [`CHANGELOG.md`](/C:/drive/dev/android/scrybe/CHANGELOG.md).
   Keep bullets short, user-facing, and grouped under `### Features`, `### Improvements`, and `### Fixes`.
   Do not add commit hashes, PR numbers, or internal implementation detail.

2. Never guess the next release number in the changelog.
   The release workflow converts `## Unreleased` into `## <version> (<date>)` automatically.
   Leave historical versioned sections alone unless you are correcting a factual mistake.

3. Validate the changelog structure after editing it.

```bash
cd apps/android-whispering
python3 scripts/manage-changelog.py validate --changelog ../../CHANGELOG.md
```

4. When checking whether a branch or PR aimed at `main` updated the changelog, use the diff check helper.

```bash
python3 apps/android-whispering/scripts/manage-changelog.py check-updated --base-ref <base-sha> --head-ref <head-sha> --changelog CHANGELOG.md
```

5. If the change touches shipped Android behavior, release automation, or anything surfaced to users, require a changelog bullet before proceeding with the commit or PR.
   In this repo, default to updating the changelog unless the change is purely local tooling noise that will never ship.

## Notes

- The Android app ignores the `Unreleased` section when rendering release history.
- The GitHub release workflow promotes `Unreleased` and uses the promoted section as the release body.
- CI enforces that changes headed to `main` include a `CHANGELOG.md` update.
