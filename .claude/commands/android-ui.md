# Android UI design loop

Use the shared local harness rather than ad-hoc ADB commands:

1. `pwsh -File ./scripts/android.ps1 doctor`
2. `pwsh -File ./scripts/android.ps1 boot -Avd scrybe-api35`
3. `pwsh -File ./scripts/ui-test.ps1 capture -App all -RunId <descriptive-id>`
4. `pwsh -File ./scripts/ui-test.ps1 compare -RunId <descriptive-id>`
5. Review `.artifacts/ui-tests/<descriptive-id>/report.md` and its actual/diff images.
6. Use Claude Design only when the user explicitly asks to transmit screenshots for design review.
7. After intentional UI changes, recapture and use `pwsh -File ./scripts/ui-test.ps1 accept -RunId <id>` only when the new baselines are approved.
8. Run `pwsh -File ./scripts/android.ps1 verify -App all` before committing.

The harness deliberately rejects ambiguous ADB device selection. Pass `-Serial emulator-####` when needed.
