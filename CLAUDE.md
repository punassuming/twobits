# Claude Code — TwoBits monorepo

> Full project reference: **[AGENTS.md](AGENTS.md)** — read it in full at the start of every session. This file contains only Claude Code-specific setup that supplements it.

---

## Session start checklist

```bash
# 1. Activate pre-commit hooks (once per clone — already set in this container)
git config core.hooksPath .githooks

# 2. Rebase onto current main before writing any code
git fetch origin main
git rebase origin/main
```

---

## Hooks already active in this session

`.claude/settings.json` configures a **PostToolUse** hook: after every `Edit` or `Write` on a `.kt` file inside `apps/scrybe`, the hook runs `ktlintFormat` on the owning Gradle module and re-stages the file if it was already staged. Import-order errors, trailing blank lines, and unused imports are fixed automatically as you write — you do not need to run `ktlintFormat` manually for those classes of violation.

The `multiline-expression-wrapping` rule cannot be auto-fixed. See AGENTS.md §"Known CI failure patterns" for the correct pattern.

---

## Custom slash commands

| Command | What it does |
|---|---|
| `/project:update-changelog` | Inline instructions for updating the `## Unreleased` section with the correct format |
| `/project:android-ui` | Runs the shared emulator capture, comparison, and design-review workflow |

---

## Changelog rule — enforced by pre-commit hook

The `.githooks/pre-commit` hook **blocks the commit** if code files in `apps/scrybe/`, `apps/shelf-snap/`, or `apps/price-drop/` are staged but the corresponding `CHANGELOG.md` is not. Do not use `SKIP_KTLINT=1` to bypass this — update the changelog instead.

Use `/project:update-changelog` when you need a reminder of the exact format.
