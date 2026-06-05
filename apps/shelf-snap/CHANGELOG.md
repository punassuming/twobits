# Changelog

## Unreleased

### Features

* redesign CameraScreen — overlay close/flash controls on viewfinder, teal L-bracket corner guides, AI tip pill, white ring shutter in dark bottom panel, Analyse pill, gallery thumbnail, full-screen animated AnalysingView with 5-step progress
* add AI configuration navigation card to Settings — prominent primaryContainer card opens AI config screen (key, models, Pro)
* update InventoryScreen — app name as TopAppBar title, Sort button, SummaryBanner showing item count + total estimate between filter chips and list, refactored InventoryItemCard with category/brand layout and confidence badge
* Scrybe: update speaker colour palette to design tokens (signal blue, glow green, ember, purple); restructure PlaybackCard with centred transport controls and no section header

### Improvements

* ItemDetailScreen — brand · model subtitle uses middle-dot separator; AI confidence badge replaced with primaryContainer pill + "GPT-4o analysis" annotation
* fail release workflow before commit/tag if keystore secret is invalid — early `Validate keystore secret` step and `rebuild_for_tag` dispatch input added to Shelf Snap release workflow

### Fixes

## 1.1.2 (2026-06-04)

### Features

### Improvements

* add duplicate release prevention — both release workflows now use `has-new-unreleased-since-tag` to skip when all `## Unreleased` bullets are already present at the last tag

### Fixes


## 1.1.1 (2026-06-04)

### Features

### Improvements

* align settings page visual style — wrap each settings section in a card with icon + title header, matching the Scrybe settings design pattern; spacing standardised to 14dp between sections
* consolidate CI/CD — `shelf-snap-build.yml` renamed to `shelf-snap-ci.yml`; `shelf-snap-release.yml` and `shelf-snap-tag-release.yml` merged into single `shelf-snap-release.yml` with `workflow_run` trigger; version computation upgraded to `mathieudutour/github-tag-action` matching Scrybe; signing secrets standardised to `SIGNING_*` convention

### Fixes



## 1.1.0 (2026-06-04)

### Features

### Improvements

* align settings page visual style — wrap each settings section in a card with icon + title header, matching the Scrybe settings design pattern; spacing standardised to 14dp between sections
* consolidate CI/CD — `shelf-snap-build.yml` renamed to `shelf-snap-ci.yml`; `shelf-snap-release.yml` and `shelf-snap-tag-release.yml` merged into single `shelf-snap-release.yml` with `workflow_run` trigger; version computation upgraded to `mathieudutour/github-tag-action` matching Scrybe; signing secrets standardised to `SIGNING_*` convention

### Fixes




## 1.0.2 (2026-06-03)

### Features

* add vision model selector for BYOK users — choose from GPT-4o, GPT-4o mini, GPT-5.4, GPT-5.4 mini, or GPT-4.1 mini for item photo analysis; selection persists across sessions; Pro users use the managed API default

### Improvements

### Fixes





## 1.0.1 (2026-06-03)

### Features

* add Shelf-Snap product page to twobits GitHub Pages site — shelf-snap.html with phone mockup, "Snap. Analyze. List or donate." how-it-works steps, tabbed item detail (Details/Market/List), feature grid, cross-listing platform chips, market research price example, and Google Play store listing mockup

### Improvements

### Fixes





## 1.0.0 (2026-06-02)

_Maintenance release._





## 1.0.0 (2026-06-01)

### Features

* Shared `ItemThumb` puts each item's photo front-and-center with a category-specific icon fallback (clothing, appliances, games, furniture, books, electronics).
* Photo thumbnails on every inventory and donation-summary row.
* Item detail: numbered photo gallery with an Add photo slot, a color-coded condition selector, a market price-range bar with platform filter chips, and an AI listing-preview card.
* Camera: numbered photo strip, framing grid + reticle, capture flash, dynamic hint text, and a working flash toggle.
* Settings: auto-analyze-on-capture and keep-original-photos toggles, a storage breakdown, an about/version footer, and this What's new screen.
* Automated releases: merging to main auto-tags the next patch version, updates this changelog, and publishes a GitHub Release.

### Improvements

* Listed/Sold status pills now carry icons; inventory cards use a larger radius.
