# Changelog

All notable changes to Shelf Snap are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
auto-incrementing patch versions: each merge to `main` publishes the next
`vMAJOR.MINOR.PATCH` release, moving the **Unreleased** entries below into a
dated, versioned section.

The in-app **What's new** screen (Settings → What's new) renders this file, so
keep entries user-facing and concise.

## [Unreleased]

## [1.0.0] - 2026-06-01


### Added
- Shared `ItemThumb` that puts each item's photo front-and-center, with a
  category-specific icon fallback (clothing, appliances, games, furniture,
  books, electronics) instead of a generic camera glyph.
- Photo thumbnails on every inventory and donation-summary row.
- Item detail: numbered photo gallery with an "Add photo" slot, a color-coded
  condition selector, a market price-range bar with platform filter chips, and
  an AI listing-preview card.
- Camera: numbered photo strip, framing grid + reticle, capture flash, dynamic
  hint text, and a working flash toggle.
- Settings: auto-analyze-on-capture and keep-original-photos toggles, a storage
  breakdown, an about/version footer, and this **What's new** screen.
- Automated releases: merging to `main` auto-tags the next patch version,
  updates this changelog, and publishes a GitHub Release.

### Changed
- Listed/Sold status pills now carry icons; inventory cards use a larger radius.
