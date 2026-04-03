# Valuation Method Roadmap

说明：

1. 这份文件是跨市场方法演进路线图，不是美股终版基线。
2. 美股当前应优先参考：
   - [美国股票估值.md](F:/fairvalue/美国股票估值.md)
   - [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
3. 如果本文件与上述两份文档冲突，以终版对齐文档为准。

This file defines how to deepen market valuation methods in later iterations.

## US (United States)

Current scaffold:
- DCF / FCFF
- Historical Multiple
- Relative Valuation
- Reverse DCF
- Risk matrix + persisted valuation runs

Next upgrades:
1. Replace research price source with Longbridge production source.
2. Add FRED + Damodaran to formal WACC and parameter pipeline.
3. Upgrade relative valuation to full peer-set engine.
4. Add sector templates: SaaS, cyclical, REIT, bank/insurance, biotech, conglomerate.
5. Add share-count forecast for buyback and SBC dilution trajectory.
6. Add analyst consensus term structure (`FY1`, `FY2`, `LT`) and dispersion penalty.

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
