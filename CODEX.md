# Codex local setup — TwoBits

`AGENTS.md` remains the authoritative repository instruction source. This file documents the local Codex/Windows additions only; the Claude Code setup in `CLAUDE.md` remains supported.

## First use

```powershell
git config core.hooksPath .githooks
pwsh -File .\scripts\android.ps1 doctor
pwsh -File .\scripts\android.ps1 boot -Avd scrybe-api35
```

Trust the checked-in `.codex/hooks.json` when Codex prompts. The hook formats Kotlin modules after Codex edits using repository-local Gradle and Android user homes; the existing user AVD directory remains the emulator source.

Set `ANTHROPIC_API_KEY` in your user environment before starting Codex when Claude Design access is required. The key is read by `.codex/config.toml` and is never stored in the repository.

## Common commands

```powershell
pwsh -File .\scripts\android.ps1 build -App all
pwsh -File .\scripts\android.ps1 run -App scrybe
pwsh -File .\scripts\android.ps1 verify -App all
pwsh -File .\scripts\ui-test.ps1 capture -App all -RunId local-review
pwsh -File .\scripts\ui-test.ps1 compare -RunId local-review
```

The canonical visual baseline is `scrybe-api35` at 1080×2400, API 35. Physical and Wi-Fi devices are valid for manual smoke testing but never for baseline acceptance.
