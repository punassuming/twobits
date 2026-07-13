---
name: android-ui-loop
description: Build, seed, navigate, capture, compare, and refine TwoBits Android interfaces on the canonical local emulator. Use for emulator testing, visual regression work, or Claude Design review of Scrybe, Shelf Snap, and PriceDrop.
---

# TwoBits Android UI loop

Use the repository PowerShell entrypoints; do not recreate their ADB selection logic.

1. Run `./scripts/android.ps1 doctor` and resolve repo-local failures.
2. Boot the canonical device with `./scripts/android.ps1 boot -Avd scrybe-api35`.
3. Capture with `./scripts/ui-test.ps1 capture -App <app|all> -RunId <id>`.
4. Compare with `./scripts/ui-test.ps1 compare -RunId <id>` and inspect the Markdown report, actuals, and diffs under `.artifacts/ui-tests/<id>/`.
5. Use Claude Design only when the user explicitly requests external design review. Never send screenshots, UI dumps, logs, or other files merely because the MCP server is configured.
6. Make the smallest interface change, recapture the affected app, and compare again.
7. Accept intentional changes only with the explicit `./scripts/ui-test.ps1 accept -RunId <id>` command.
8. Use `$update-changelog` for every affected app, then finish with `./scripts/android.ps1 verify -App <app|all>` and the manifest checks required by `AGENTS.md`.

Always pass `-Serial` when more than one emulator is connected. Never use a physical or Wi-Fi device for golden baselines.
