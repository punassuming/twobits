---
name: update-changelog-before-main
description: Update the app-specific CHANGELOG.md before any commit, pull request, or push that is headed to `main` in this repository so the in-app release notes, Settings release history, and GitHub releases stay in sync. Use when preparing Android changes for review, merge, or release, especially when files under `apps/scrybe/` or `apps/shelf-snap/`, release workflows, or other shipped behavior changed.
---

# Update Changelog Before Main

Keep the relevant app changelog aligned with shipped behavior before anything lands on `main`. The Scrybe app ships `CHANGELOG.md` for the in-app "What's New" dialog and Settings release history, and the release workflow promotes `## Unreleased` into a versioned release section during the GitHub release workflow.

Each app has its own changelog:
- **Scrybe:** `apps/scrybe/CHANGELOG.md`
- **Shelf Snap:** `apps/shelf-snap/CHANGELOG.md`

## Workflow

1. Edit the top `## Unreleased` section in the relevant changelog.
   Keep bullets short, user-facing, and grouped under `### Features`, `### Improvements`, and `### Fixes`.
   Do not add commit hashes, PR numbers, or internal implementation detail.

2. Never guess the next release number in the changelog.
   The release workflow converts `## Unreleased` into `## <version> (<date>)` automatically.
   Leave historical versioned sections alone unless you are correcting a factual mistake.

3. Validate the changelog structure after editing it.

For Scrybe changes:
```bash
python3 apps/scrybe/scripts/manage-changelog.py validate --changelog apps/scrybe/CHANGELOG.md
```

For Shelf Snap changes:
```bash
python3 apps/scrybe/scripts/manage-changelog.py validate --changelog apps/shelf-snap/CHANGELOG.md
```

4. When checking whether a branch or PR aimed at `main` updated the changelog, use the diff check helper.

```bash
python3 apps/scrybe/scripts/manage-changelog.py check-updated --base-ref <base-sha> --head-ref <head-sha> --changelog apps/scrybe/CHANGELOG.md
```

5. If the change touches shipped Android behavior, release automation, or anything surfaced to users, require a changelog bullet before proceeding with the commit or PR.
   In this repo, default to updating the changelog unless the change is purely local tooling noise that will never ship.

## Notes

- The Android app ignores the `Unreleased` section when rendering release history.
- The GitHub release workflow promotes `Unreleased` and uses the promoted section as the release body.
- CI enforces that changes headed to `main` include a changelog update for the affected app.
