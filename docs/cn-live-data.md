# CN Live Data Integration

## Scope

This project now supports live CN quote ingestion for the A-share valuation flow.

Covered in this iteration:

- Single-stock live quote overlay for `GET /v1/valuation/CN/{symbol}`
- CN discovery feed for the front-end under-valued page
- Automatic fallback to seeded/synthetic data when the upstream live source is unavailable
- Short-lived in-memory caching to reduce upstream pressure

Not covered yet:

- Full financial statement ingestion
- Full-market asynchronous recalculation
- Persistent local quote cache or database snapshots
- Institutional-grade rate-limit / retry orchestration

## Upstream Source

Current live CN market data source:

- Eastmoney `push2` quote endpoint
- Eastmoney `push2` market list endpoint

The implementation is intentionally isolated in:

- `src/main/java/com/fairvalue/engine/cn/CnEastmoneyClient.java`

This keeps the valuation engine decoupled from the external provider so we can replace it later.

## Runtime Flow

### Single-stock valuation

1. Request hits `GET /v1/valuation/CN/{symbol}`.
2. `MarketDataService` tries to load live quote data from Eastmoney.
3. Live fields such as `price`, `name`, `pe`, `pb`, `roe`, `market cap` are merged into the local CN snapshot template.
4. The CN valuation strategy runs on the merged snapshot.
5. If live retrieval fails, the engine falls back to seeded/synthetic CN data.

### CN discovery feed

1. Request hits `GET /v1/cn-equities/discovery?page=1&size=30`.
2. `CnEastmoneyClient` loads a real-time CN stock page.
3. `CnStockValuationService` converts each item into a CN snapshot and computes fair value.
4. The API returns a front-end friendly payload containing price, fair value, upside, confidence, ratings, and market metrics.

## Config

Relevant properties in `src/main/resources/application.properties`:

- `market-data.cn.live.enabled=true`
- `market-data.cn.live.base-url=https://push2.eastmoney.com`
- `market-data.cn.live.quote-cache-ttl-seconds=20`
- `market-data.cn.live.universe-cache-ttl-seconds=30`
- `market-data.cn.live.default-page-size=30`
- `market-data.cn.live.max-page-size=60`

## Testing

To keep tests stable and offline-safe, live CN data is disabled under:

- `src/test/resources/application.properties`

This means CI and local tests still run against deterministic fallback data.

## Current Limitations

- Live CN integration is quote-driven, not full-financial-statement-driven yet.
- Some valuation factors still come from local templates or heuristics when upstream financial depth is missing.
- Discovery feed is optimized for front-end browsing, not yet a complete exchange-grade full-universe screener.

## Next Recommended Steps

1. Add persistent CN universe storage and background refresh.
2. Replace heuristic CN financial factors with normalized financial statements from a dedicated source.
3. Add paging + filter + sorting semantics for the CN discovery endpoint.
4. Add request-level metrics, retry policy, and rate-limit protection for the upstream client.
