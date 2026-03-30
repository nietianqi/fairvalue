# Valuation Method Roadmap

This file defines how to deepen market valuation methods in later iterations.

## US (United States)

Current scaffold:
- DCF + EV/EBITDA + PEG
- Growth/expectation/SBC adjustments

Next upgrades:
1. Add reverse DCF to estimate implied growth from market price.
2. Add sector templates: SaaS (Rule of 40), semis (cycle + capex), REIT/financial models.
3. Add share-count forecast for buyback and SBC dilution trajectory.
4. Add analyst consensus term structure (`FY1`, `FY2`, `LT`) and dispersion penalty.

## CN (China A-share)

Current scaffold:
- Relative multiple + PEG + quality anchor
- Policy and sentiment adjustments

Next upgrades:
1. Use ex-non-recurring earnings pipeline as default profit base.
2. Add style-cycle engine (growth/value/dividend) and regime classifier.
3. Build policy sensitivity score from event taxonomy.
4. Add sector-specific valuation templates (consumer, cyclicals, financials, TMT).

## JP (Japan)

Current scaffold:
- PB-ROE + net-cash adjustment + dividend anchor
- Governance and buyback adjustments

Next upgrades:
1. Add cross-shareholding adjustment and treasury stock normalization.
2. Add TSE capital efficiency reform signal tracking.
3. Add payout policy path forecast (dividend + buyback combined yield).
4. Add asset revaluation module for low-PB asset-heavy companies.

## HK (Hong Kong)

Current scaffold:
- Dividend valuation + NAV anchor + A/H parity
- Liquidity and USD-rate adjustments

Next upgrades:
1. Add micro-liquidity model from turnover and float concentration.
2. Add dynamic A/H spread model by sector and regime.
3. Add China-exposure factor decomposition (consumer/internet/financial/property).
4. Add NAV quality discount for holdings structures and conglomerates.

## Cross-market upgrades

1. Model registry and versioned formula metadata.
2. Confidence score decomposition persisted with historical snapshots.
3. Historical valuation percentile and peer-relative z-score.
4. Async recalculation pipeline for earnings-event refresh.
5. Replace sample data with real ingestion + standardized financial schema.
