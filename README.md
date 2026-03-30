# Fairvalue Engine (Java)

Global Stock Valuation Engine backend built with Spring Boot.

## What is implemented now

- Unified valuation API format for four markets: `US`, `CN`, `JP`, `HK`
- Market-specific strategy modules with independent logic and model mix
- Multi-model weighted valuation output with:
  - intrinsic value
  - tradable fair value
  - fair value range
  - confidence score
  - risk flags
  - market adjustments
- Endpoints:
  - `GET /v1/valuation/{market}/{symbol}`
  - `POST /v1/valuation/batch`
  - `GET /v1/valuation/{market}/{symbol}/explain`
  - `POST /v1/valuation/scenario`
  - `GET /v1/valuation/history/{market}/{symbol}`
  - `POST /v1/screener/valuation`

## Architecture

- `com.fairvalue.engine.api`: REST controller and error handling
- `com.fairvalue.engine.service`: valuation orchestration and sample market data
- `com.fairvalue.engine.valuation.strategy`: per-market valuation strategy implementation
- `com.fairvalue.engine.valuation`: shared valuation result models
- `com.fairvalue.engine.domain`: market/fundamental/snapshot domain types

## Run

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

## Test

```bash
./mvnw test
```

## Notes

- Current data layer uses in-memory sample data + deterministic synthetic fallback.
- The valuation formulas are production-friendly scaffolding, not final investment models.
- Detailed method-upgrade plan is in `docs/valuation-method-roadmap.md`.
