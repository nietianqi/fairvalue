# Global Product Alignment Handoff (2026-04-02)

## Basis
This round was aligned against:
- `全球股票估值系统_功能清单与优先级文档_重新生成.md`
- `全球股票估值系统_商业化产品文档 (1).md`
- `全球股票估值系统_商业化产品文档_重新生成.md`

## What Codex completed
The codebase now exposes two product-facing capabilities that were clearly called out in the global product docs but were missing as formal APIs:

1. Rankings API
- `GET /v1/rankings/{market}/undervalued`
- `GET /v1/rankings/{market}/overvalued`
- Implemented in `ValuationController` + `MarketDiscoveryService`
- Returns market-aware ranking payloads using existing discovery / valuation logic

2. Peers API
- `GET /v1/peers/{market}/{symbol}`
- For `US`, the endpoint reuses the existing strict peer-set selection and source-attribution chain
- For `CN/JP/HK`, it provides a lightweight snapshot-universe peer fallback so the API surface is already present for all four markets

## Files changed in this round
- `src/main/java/com/fairvalue/engine/api/ValuationController.java`
- `src/main/java/com/fairvalue/engine/service/MarketDiscoveryService.java`
- `src/main/java/com/fairvalue/engine/api/dto/MarketRankingResponse.java`
- `src/main/java/com/fairvalue/engine/api/dto/MarketPeersResponse.java`
- `src/main/java/com/fairvalue/engine/api/dto/MarketPeerItem.java`
- `src/test/java/com/fairvalue/engine/api/ValuationControllerTest.java`

## Validation
- `./mvnw.cmd test`
- Result: `56` tests passing

## Important implementation notes
- `US peers` is the stronger implementation path in this round.
  - It no longer triggers a full `runValuation()` inside `GET /v1/peers/US/{symbol}`.
  - It now reuses the latest persisted `relative_valuation` assumptions when the latest run is fresh.
  - If no fresh run exists, it falls back to read-only peer discovery without writing a new `valuation_run`.
  - It exposes `selection_basis`, `source_mode`, `peer_set_source`, `peer_candidate_count`, `peer_selection_rule_version`, `peer_filter_summary`, `selection_breakdown`, and `peer_filter_metrics`
- `CN/JP/HK peers` is intentionally a temporary product-surface fallback.
  - It is based on industry-preferred snapshot similarity
  - It should be upgraded later to market-specific peer logic
- `CN rankings` currently wrap the existing discovery page and sort within the returned page.
  - This gives the formal API now
  - But a true full-universe CN ranking engine is still a follow-up task
- `rankings` now return a market-agnostic DTO rather than leaking `CnDiscoveryItem` into the global API surface.
  - `items` now use `MarketRankingItem`
  - verdict values are now stable enum-like labels such as `UNDERVALUED / OVERVALUED / FAIR`
- `rankings` also now include:
  - `generated_at`
  - `data_as_of`
  - `ranking_basis`
  - `disclaimer`
- `CN rankings` explicitly mark the current implementation as `page-scoped-ranking`.
- `generic peers` no longer fake `market_cap = 0.0`; nullable fields are used when generic market cap is unavailable in the current snapshot model.

## What Claude Code should review next
1. Whether the global rankings payload should be wrapped into a stricter commercial API envelope
2. Whether `CN rankings` must be upgraded immediately from page-ranked to full-universe ranked
3. Whether the new `peers` response fields are sufficient for the commercial product layer
4. Whether `CN/JP/HK peers` should keep the current fallback or move to market-specific logic next
5. Whether the global product docs should now explicitly list:
   - `/v1/rankings/{market}/undervalued`
   - `/v1/rankings/{market}/overvalued`
   - `/v1/peers/{market}/{symbol}`
