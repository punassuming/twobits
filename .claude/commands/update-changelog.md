Update the `## Unreleased` section of the relevant app's `CHANGELOG.md` before this commit lands on `main`.

> **Write the entries yourself.** Nothing auto-generates them from git commits. `manage-changelog.py` only validates structure; the release workflow only promotes `## Unreleased` → a versioned section.

## Which file to edit

- Scrybe changes → `apps/scrybe/CHANGELOG.md`
- Shelf Snap changes → `apps/shelf-snap/CHANGELOG.md`
- PriceDrop changes → `apps/price-drop/CHANGELOG.md`
- Changes to `shared/` that affect app behaviour → update all three

## Format

Add entries under `### Features`, `### Improvements`, or `### Fixes` inside `## Unreleased`.

**Format A — user-visible change** (Features and Improvements):
```
**[Screen or component]** — [short one-line description]:
* detail bullet one
* detail bullet two
```
- Bold text = collapsed title shown at all times
- Em-dash `—` (U+2014, not a hyphen) separates component from description
- Sub-bullets expand when the user taps the row
- Blank line required between consecutive bold-title items

**The bold title line has NO leading `* `.** The parser (`ReleaseNotesParser.parseGroupItems`) only recognizes a new titled item when the line starts with `**` directly — `* **Topic** — description` is parsed as a plain untitled bullet (title == description, which then renders as flat undifferentiated text with no bold, defeating the whole point of Format A). This is an easy mistake to make since every *other* line in a changelog is bullet-prefixed — double-check every bold-title line starts with `**`, not `* **`.

Correct:
```
**Item title field** — items now have an editable Title field.
```
Wrong (looks almost identical, parses as a flat untitled bullet):
```
* **Item title field** — items now have an editable Title field.
```

**Format B — invisible/infra change** (plain bullet anywhere):
```
* short description of what changed and why
```

## Rules

- Never invent a version number — `## Unreleased` gets promoted automatically by the release workflow
- Keep bullets user-facing; omit commit hashes, PR numbers, internal class names
- One em-dash per title line, both sides required

## Validate after editing

```bash
python3 scripts/manage-changelog.py validate --changelog apps/scrybe/CHANGELOG.md
python3 scripts/manage-changelog.py validate --changelog apps/shelf-snap/CHANGELOG.md
python3 scripts/manage-changelog.py validate --changelog apps/price-drop/CHANGELOG.md
```
