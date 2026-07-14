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

## Worker workspace

The managed Pro proxy remains an independent repository. Codex treats the Android and Worker checkouts as sibling workspace roots; the Worker is not a submodule, subtree, or nested checkout.

```text
C:\drive\source\android\twobits
C:\drive\source\android\twobits-worker
```

From the Android repository, use the tracked helper to locate and manage the sibling checkout:

```powershell
pwsh -File .\scripts\worker.ps1 doctor
pwsh -File .\scripts\worker.ps1 setup
pwsh -File .\scripts\worker.ps1 status
pwsh -File .\scripts\worker.ps1 sync
pwsh -File .\scripts\worker.ps1 test
pwsh -File .\scripts\worker.ps1 contract
pwsh -File .\scripts\worker.ps1 dev
```

Pass `-WorkerRoot D:\path\to\twobits-worker` when the sibling lives elsewhere. Add both folders as Codex workspace roots locally. Do not add the Worker checkout to this repository or change Claude's existing shared-root setup.

PriceDrop discovery work must be coordinated across both repositories. BYOK calls provider adapters directly; Pro calls `/v2/products/discover`. Update the v2 schema and golden fixture in both test suites whenever the canonical contract changes. Deploy the Worker route before releasing the matching Android client.

## Repository skills

- `$android-ui-loop` runs the shared emulator capture and comparison workflow.
- `$update-changelog` applies the same changelog parser rules as Claude's `/project:update-changelog` command.
