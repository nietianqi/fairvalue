# Fairvalue Engine (Java)

Global Stock Valuation Engine backend built with Spring Boot and PostgreSQL.

## What is implemented now

- Unified valuation API for `US`, `CN`, `JP`, `HK`
- Market-specific strategy and controller modules
- US data pipeline:
  - SEC universe / submissions / companyfacts
  - IR metadata sync
  - financial standardization and derived metrics
  - financial quality and data quality scoring
  - market data persistence
- US live source clients:
  - Longbridge
  - FRED
  - Damodaran
  - SimFin (partial)
- CN live source integration with Eastmoney quote and discovery feed
- Multi-model valuation output with:
  - `fair_value_low / fair_value_mid / fair_value_high`
  - `blended_intrinsic_value`
  - `confidence`
  - `scenario_matrix`
  - `risk_matrix`
  - `source_attribution`
- US persistence layers:
  - `valuation_runs`
  - `valuation_method_results`
  - `scenario_results`
  - `reverse_dcf_results`
  - `risk_scores`
  - `report_blocks`
  - `valuation_latest_snapshot`
  - `valuation_jobs`
- US read path:
  - `summary / report / rankings` now prefer database reads
  - `history` now prefers persisted price + valuation history
  - `screener` now prefers latest snapshot and lightweight fallback logic
  - `peers` now uses industry-aware read-only peer discovery with snapshot fallback
- US scheduled refresh foundation:
  - every 30 minutes for the `US security_master` universe
  - `valuation_jobs` queue semantics, claim/retry/recover flow
  - `valuation_alerts` summary endpoint
- Platform layer now enabled in main runtime:
  - forced success envelope
  - API key authentication
  - per-client rate limiting
  - entitlement checks
  - usage metering

## Main endpoints

- `GET /v1/valuation/{market}/{symbol}`
- `POST /v1/valuation/batch`
- `GET /v1/valuation/{market}/{symbol}/explain`
- `POST /v1/valuation/scenario`
- `GET /v1/valuation/history/{market}/{symbol}`
- `POST /v1/screener/valuation`
- `GET /v1/discovery/{market}`
- `GET /v1/rankings/{market}/undervalued`
- `GET /v1/rankings/{market}/overvalued`
- `GET /v1/peers/{market}/{symbol}`

US-specific:

- `POST /v1/us-equities/{ticker}/valuation/run`
- `GET /v1/us-equities/{ticker}/valuation/summary`
- `GET /v1/us-equities/{ticker}/valuation/report`
- `GET /v1/us-equities/{ticker}/profile`
- `GET /v1/us-equities/{ticker}/data-quality`
- `GET /v1/us-equities/{ticker}/financial-quality`
- `GET /v1/us-equities-admin/{ticker}/overview`
- `GET /v1/us-equities-admin/{ticker}/source-status`
- `GET /v1/us-equities-admin/valuation-jobs`
- `GET /v1/us-equities-admin/valuation-jobs/summary`
- `GET /v1/us-equities-admin/{ticker}/valuation-jobs`
- `POST /v1/us-equities-admin/valuation-jobs/retry-failed`
- `POST /v1/us-equities-admin/{ticker}/valuation-jobs/retry-failed`
- `POST /v1/us-equities-admin/valuation-jobs/recover-stale`
- `GET /v1/us-equities-admin/valuation-alerts`
- `GET /platform/bootstrap`
- `GET /v1/platform/me`
- `GET /v1/platform/usage`

## Architecture

- `com.fairvalue.engine.api`: REST controllers and DTOs
- `com.fairvalue.engine.service`: valuation orchestration, discovery, history, screener
- `com.fairvalue.engine.us`: US ingestion, normalization, quality scoring, valuation composition, persistence, read service, scheduling
- `com.fairvalue.engine.cn`: CN rule engine and Eastmoney live integration
- `com.fairvalue.engine.jp`: JP APIs and valuation services
- `com.fairvalue.engine.platform`: API clients, entitlements, usage metering
- `com.fairvalue.engine.repository`: PostgreSQL repositories with JdbcClient
- `src/main/resources/db/migration`: Flyway migrations (`V1` to `V10`)
- `src/main/resources/static`: global rankings page, US detail page, US admin page
  - `cn-undervalued-stocks.html`
  - `valuation-screener.html`
  - `us-stock-detail.html`
  - `us-equities-admin.html`

## Current read/write model

US now uses a split model:

1. Write path
   - `POST /v1/us-equities/{ticker}/valuation/run`
   - computes valuation methods
   - persists historical run tables
   - upserts `valuation_latest_snapshot`
2. Read path
   - `summary / report / rankings` prefer `valuation_latest_snapshot`
   - `history` prefers `market_price_daily + valuation_runs`
   - `peers` uses industry-aware read-only peer discovery and snapshot fallback
   - `screener` prefers latest snapshot and lightweight valuation fallback

Current `US full-universe` behavior:

- `GET /v1/discovery/US` now reads from full `security_master` pagination
- `GET /v1/rankings/US/*` now uses full-universe pagination too
- until `valuation_latest_snapshot` covers the full universe, `rankings` remain `page-scoped`
- recent live `SEC universe sync` brought active `US` universe size to `8072`

Current `US peers` behavior:

- strict peer set first
- read-only `snapshot_fallback` if no clean peer set is available
- no new `valuation_runs` are created by `GET /v1/peers/US/{symbol}`
- `snapshot_fallback` now groups/sorts by `industry / sector_template / company_type / market_cap / growth / margins / liquidity`
- partial real enrichment is available for `market_cap` and `roic` when peer-side persisted data exists
- live `AAPL` peer calls now return non-empty peer sets

Current platform API layer behavior:

- main runtime now enables:
  - forced envelope
  - API key auth
  - per-client rate limiting
  - entitlement checks
  - usage metering
- frontends bootstrap themselves via `/platform/bootstrap`
- tests keep auth / rate-limit / force-envelope disabled unless a test turns them on explicitly

## Config

Important runtime config is in:

- [application.properties](F:/fairvalue/src/main/resources/application.properties)
- [application-secrets.properties](F:/fairvalue/application-secrets.properties)

Key safety rule:

- Real keys must only live in environment variables or `application-secrets.properties`
- Do not commit secrets into source files

US schedule config:

- `app.valuation.us.schedule.enabled`
- `app.valuation.us.schedule.fixed-delay-ms`
- `app.valuation.us.schedule.initial-delay-ms`
- `app.valuation.us.schedule.max-symbols`
  - `0` means schedule against the full `security_master` universe
- `app.valuation.us.schedule.max-retries`
- `app.valuation.us.schedule.retry-lookback-hours`

Platform API config:

- `app.api.envelope-enabled`
- `app.api.force-envelope`
- `app.api.envelope-header`
- `app.api.auth.enabled`
- `app.api.auth.header-name`
- `app.api.auth.valid-keys`
- `app.api.rate-limit.enabled`
- `app.api.rate-limit.requests-per-minute`
- `app.api.rate-limit.identity-header`

US worker / alerting config:

- `app.valuation.us.worker.fixed-delay-ms`
- `app.valuation.us.worker.initial-delay-ms`
- `app.valuation.us.worker.batch-size`
- `app.valuation.us.worker.backlog-alert-threshold`
- `app.valuation.us.worker.failed-alert-threshold`

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

## Test

```powershell
.\mvnw.cmd test
```

## Current limitations

- `valuation_jobs` now has queue semantics (`priority / available_at / worker_id`), retry/recover flow, and alert summaries, but is not yet a full worker-pool engine
- US `peers` now has industry-aware read-only discovery plus `snapshot_fallback`, but fallback still lacks consistently available `roic`
- CN / JP / HK are not yet on the same `latest snapshot + schedule + read-db` architecture as US
- Global API envelope, API key, rate limit, entitlement checks, and usage metering now exist, but billing, client lifecycle management, and watchlist/alert features are not complete

## Current frontend pages

- Global rankings: [http://localhost:18080/cn-undervalued-stocks.html](http://localhost:18080/cn-undervalued-stocks.html)
- Global screener: [http://localhost:18080/valuation-screener.html](http://localhost:18080/valuation-screener.html)
- US detail: [http://localhost:18080/us-stock-detail.html?ticker=AAPL](http://localhost:18080/us-stock-detail.html?ticker=AAPL)
- US admin console: [http://localhost:18080/us-equities-admin.html](http://localhost:18080/us-equities-admin.html)
  - includes source status, valuation alerts, platform usage, and recent job controls

## Primary docs

- [全球股票估值系统_统一产品文档.md](F:/fairvalue/全球股票估值系统_统一产品文档.md)
- [美国股票估值.md](F:/fairvalue/美国股票估值.md)
- [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
- [docs/claude-code-collaboration-guide.md](F:/fairvalue/docs/claude-code-collaboration-guide.md)
