# Global Product Handoff (2026-04-02 Rankings / Peers Review Fixes)

## Basis
This handoff reflects the follow-up fixes after product review of:

- `GET /v1/rankings/{market}/undervalued`
- `GET /v1/rankings/{market}/overvalued`
- `GET /v1/peers/{market}/{symbol}`

The review focused on five items labeled `G1-A ~ G1-E`.

## What Codex fixed

### 1. P0 fixed: `US peers GET` is now read-only

Previous issue:
- `MarketDiscoveryService.usPeers()` called `usEquityValuationService.runValuation(...)`
- That caused every `GET /v1/peers/US/AAPL` call to write:
  - `valuation_runs`
  - `valuation_method_results`
  - `scenario_results`
  - `report_blocks`

Current behavior:
- The endpoint now reads the latest persisted `relative_valuation` assumptions from `valuation_method_results`
- It only reuses that payload when the latest run is fresh
- If no fresh run exists, it falls back to read-only peer discovery
- It does not create a new valuation run

Current source:
- `src/main/java/com/fairvalue/engine/service/MarketDiscoveryService.java`
- `src/main/java/com/fairvalue/engine/repository/ValuationMethodResultsRepository.java`

### 2. P0 fixed: rankings no longer leak CN DTOs into global API

Previous issue:
- `MarketRankingResponse.items` used `List<CnDiscoveryItem>`
- That leaked CN-specific fields and semantics into `US` rankings

Current behavior:
- `MarketRankingResponse.items` now uses `List<MarketRankingItem>`
- `MarketRankingItem` is market-agnostic
- Verdict is now stable enum-like text:
  - `UNDERVALUED`
  - `OVERVALUED`
  - `FAIR`

Current source:
- `src/main/java/com/fairvalue/engine/api/dto/MarketRankingItem.java`
- `src/main/java/com/fairvalue/engine/api/dto/MarketRankingResponse.java`

### 3. P1 fixed: rankings envelope extended

`MarketRankingResponse` now includes:
- `generated_at`
- `data_as_of`
- `ranking_basis`
- `disclaimer`

Current default:
- `ranking_basis = upside_pct`

Special note:
- `CN rankings` are now explicitly labeled as `page-scoped-ranking`
- The disclaimer also states that the result is not yet a full-market ranking

### 4. P1 fixed: generic peer numeric fields no longer fake `0.0`

`MarketPeerItem` numeric fields are now nullable `Double`, not primitive `double`.

This matters because:
- `null` now means "not available"
- `0.0` is no longer used as a fake placeholder

In particular:
- generic peer `marketCap` is no longer hardcoded to `0.0`

### 5. P1 partially fixed: generic peer sorting no longer uses PE-distance

Previous issue:
- generic peers were sorted using `abs(pe - target_pe)`
- That is not meaningful across industries

Current behavior:
- sorting now prefers:
  - same industry
  - market-cap similarity if available
  - liquidity fallback

Important remaining constraint:
- the generic `StockSnapshot / StockFundamentals` model still does not expose real `market_cap`
- so for `CN / JP / HK`, the current implementation often falls back to liquidity ordering
- the ranking logic is safer than before, but generic market-cap proximity is not fully real yet

## Validation status

Most recent successful test run in workspace history:
- `58` tests passing

Key regression coverage now includes:
- `US peers GET` does not create new `valuation_runs`
- `US rankings` payload does not expose CN-only fields
- rankings envelope fields exist

## What Claude Code should review next

1. Whether the current "fresh latest relative run" reuse window should remain `1 hour`
2. Whether `CN rankings` should stay page-scoped until full persisted ranking infrastructure exists
3. Whether generic peers should remain liquidity-heavy until `market_cap` is added to `StockSnapshot`
4. Whether `MarketRankingItem` should add more commercial product fields now or wait for the envelope refactor
5. Whether `source attribution` should reference these endpoints explicitly in the final product docs

## Suggested next engineering step

1. Extend the generic market snapshot model to expose real `market_cap`
2. Then upgrade `CN / JP / HK peers` from:
   - `industry -> liquidity`
   to:
   - `industry -> market_cap_similarity -> liquidity`
