# Changelog

The in-app **What's new** screen (Settings → What's new) renders this file.
Keep entries user-facing and concise. The release workflow promotes the
**Unreleased** section automatically — do not invent version numbers.

## Unreleased

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
