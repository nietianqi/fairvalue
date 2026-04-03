# US Equity Handoff — Session 9 Bootstrap / Backfill / JP / History

更新日期：2026-04-03

## 本轮 Codex 已完成

1. `S9-A` Bootstrap 健壮性
   - `GET /platform/bootstrap` 无 public client 时不再抛异常
   - 当前返回空 `api_key` 和 `force_envelope = false`

2. `S9-B` Top 50 US snapshot backfill
   - 已按 `style=balanced`、`horizon=6-18m` 触发一轮代表性 ticker `valuation/run`
   - 当前 live：
     - `snapshot_count = 923`
     - `rankable_count = 497`
   - 当前 `AAPL peers` 已返回 `5` 条真实同行数据
   - 部分给定 ticker 当前不在 `security_master`，会返回 `Ticker ... does not exist in security master.`

3. `S9-C` Damodaran fallback
   - 新增 `market-data.us.damodaran.fallback-erp=0.0472`
   - scrape 失败或抓空时回退到 `damodaran_static_fallback`
   - 当前 live `source-status`：
     - `damodaran.status = warning`
     - `damodaran.detail = erp_snapshot_fallback`
     - `damodaran.erp_source = damodaran_static_fallback`

4. `S9-D` JP URL 规范化
   - canonical path 已切到 `/v1/jp-equities`
   - legacy `GET /api/jp/stocks/*` 已 302 redirect
   - legacy `POST /api/jp/stocks/screener|recalc` 仍保留兼容映射

5. `S9-E` HistoryPoint `run_id`
   - `HistoryPoint` 已新增 `run_id`
   - `GET /v1/valuation/history/US/AAPL?days=10` live 已返回 `run_id`

6. `S9-F` CN / JP 字段确认
   - `CnValuationSummaryResponse` 当前字段：
     - `ticker`
     - `market`
     - `current_price`
     - `fair_value_mid`
     - `fair_value_low`
     - `fair_value_high`
     - `upside`
     - `confidence_score`
     - `verdict`
   - `sections` Map key 当前为 `snake_case` 自定义 key
   - `JpFairValueResponse` 当前 live 已确认包含：
     - `method_results`
     - `scenarios`
     - `risk_flags`
     - `catalyst_flags`

## 关键 live 验证

1. `GET /platform/bootstrap`
   - 200
2. `GET /v1/us-equities-admin/ranking-coverage`
   - `snapshot_count = 923`
3. `GET /v1/peers/US/AAPL?limit=5`
   - `peer_selection_mode = snapshot_fallback`
   - `items = 5`
4. `GET /v1/us-equities-admin/AAPL/source-status`
   - `damodaran.status = warning`
5. `GET /v1/jp-equities/7203/fair-value`
   - 200
6. `GET /api/jp/stocks/7203/history?days=120`
   - 302 -> `/v1/jp-equities/7203/history?days=120`
7. `GET /v1/valuation/history/US/AAPL?days=10`
   - `points[].run_id` 存在

## 需要 Claude Code 优先 review 的点

1. `S9-B` 当前 peers 虽然已返回真实数据，但 live 仍是 `snapshot_fallback`
   - 是否接受作为前端阶段性口径
2. `CN DTO` 当前与前端期望仍有命名差异：
   - `upside` vs `upside_downside`
   - `verdict` vs `valuation_verdict`
   - 缺 `one_liner`
3. `JP` canonical path 已可用
   - Claude Code 可直接按 `/v1/jp-equities/{code}/*` 开发 JP 详情页

## 当前剩余边界

1. `S9-G` 还未做：
   - `CN / JP history point.simulated`
2. `Top 50` 指定名单里有部分 ticker 当前不在 `security_master`
3. `US rankings` 仍未切到 strict mode，因 snapshot coverage 仍未达阈值
