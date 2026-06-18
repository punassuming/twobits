# Changelog

## Unreleased

### Features

**PriceDrop** — initial release:
* watch screen with live price watchlist, alert status badges, and coupon chips
* drops feed showing price drops, coupons found, and historical lows
* search screen for adding products by URL, keyword, or barcode
* barcode scan via device camera
* product detail with price history chart and multi-retailer offer comparison
* ask AI screen for shopping recommendations
* Pro tier and BYOK configuration
* onboarding flow for new users

### Improvements

**Live data** — wired the app to the TwoBits Worker (Pro passthrough):
* product search now calls the real `/v1/pricedrop/search` endpoint instead of placeholder results
* the Ask assistant now answers via the managed `/v1/chat/completions` proxy with a shopping-scoped prompt
* added a network layer (OkHttp + Gson) reusing `shared/network`, with friendly error mapping and bearer auth from the RevenueCat user id
* repository can now refresh current price, backfill Keepa price history, fetch coupons, and resolve scanned barcodes

**Tooling** — PriceDrop now has its own CI workflow (build + unit tests + Android lint on every PR) and a ktlint `.editorconfig`, matching Scrybe and Shelf Snap.

**Billing** — purchase/restore logic now runs through a shared `PurchaseDelegate` in the shared billing module, removing duplicated billing orchestration across the apps.

**Data model** — expanded local schema for the full tracking experience:
* new `Offer`, `Coupon`, and `Activity` entities plus DAOs; `WatchedProduct` gains shipping/fees/seller/source/confidence and `PriceEvent` gains effective price
* added effective-price math (`item + shipping + fees − coupon`, never negative) with unit tests
* added a per-provider settings store (Off/BYOK/Pro modes + encrypted key storage) as the foundation for provider management

### Fixes

* Pro entitlement now checks `pricedrop_pro` (was defaulting to the shared `pro` entitlement)
* Pro upgrade CTA now initiates a real RevenueCat purchase (annual or monthly based on the selected plan) instead of a no-op; BYOK CTA routes to AI Config
* annual plan selection is now correctly passed through to the purchase flow — previously always purchased the monthly package regardless of selection
