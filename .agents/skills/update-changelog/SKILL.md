---
name: update-changelog
description: Update TwoBits app CHANGELOG.md Unreleased sections using the repository parser's exact titled-entry and infrastructure-entry formats. Use whenever code, tests, build configuration, shared modules, or user-visible behavior changes require changelog coverage before a commit or pull request.
---

# Update the TwoBits changelog

Write the entries manually. `manage-changelog.py` validates structure but does not generate content.

## Select files

- Scrybe changes: `apps/scrybe/CHANGELOG.md`
- Shelf Snap changes: `apps/shelf-snap/CHANGELOG.md`
- PriceDrop changes: `apps/price-drop/CHANGELOG.md`
- Shared behavior changes: update every affected app

Edit only `## Unreleased`. Add entries under `### Features`, `### Improvements`, or `### Fixes`; never invent a version.

## Choose the parser format

Use Format A for anything users can see, feel, or configure:

```markdown
**Short component title** — one short sentence:
* one detail per bullet
* keep details concise
```

Start the title directly with `**`. Never write `* **Title**`; the parser treats that as an untitled flat row. Use an em dash (`—`), keep titles to 2–5 words, and leave a blank line between titled items.

Use Format B only for invisible tooling, tests, CI, build configuration, internal refactors, or `(no visual change)` work:

```markdown
* concise description of what changed and why
```

Do not use internal class names, commit hashes, PR numbers, or implementation jargon in user-visible entries.

## Validate

Run validation for every edited changelog:

```powershell
python scripts/manage-changelog.py validate --changelog apps/scrybe/CHANGELOG.md
python scripts/manage-changelog.py validate --changelog apps/shelf-snap/CHANGELOG.md
python scripts/manage-changelog.py validate --changelog apps/price-drop/CHANGELOG.md
```

Before committing, inspect the staged diff and confirm each affected app has genuinely new `## Unreleased` content.
