# US Equity Handoff — 2026-04-03 — Ranking / Peer / Platform Control Plane

## 本轮完成

1. `US strict ranking engine`
   - 新增 `valuation_latest_snapshot` 严格排名查询
   - 新增 `ranking-coverage` 指标
   - `US rankings` 现在会按 coverage 自动决定：
     - `strict persisted ranking`
     - `page_scoped_ranking`

2. `US peers`
   - 行业规则优先 + 只读 fallback 已统一到同一套选择链路
   - 响应新增：
     - `peer_selection_mode`
     - `peer_quality_score`
     - `peer_data_completeness`
   - `AAPL` live 当前可返回非空 peers，但模式仍常是 `snapshot_fallback`

3. `valuation_jobs`
   - 新增：
     - `priority`
     - `available_at`
     - `worker_id`
     - backoff
     - `dead_letter`
   - 后台接口新增：
     - `GET /v1/us-equities-admin/valuation-jobs/dead-letter`
     - `GET /v1/us-equities-admin/ranking-coverage`

4. 平台 control-plane
   - 新增：
     - `GET /v1/platform/clients`
     - `POST /v1/platform/clients`
     - `POST /v1/platform/clients/{id}/rotate-key`
     - `POST /v1/platform/clients/{id}/disable`
     - `GET /v1/platform/plans`
   - `daily_quota` 已生效，超额返回 `quota_exceeded`

## 关键 live 验证

1. `POST /v1/us-equities-admin/universe/sync`
   - `total_fetched = 10433`
   - `inserted = 8069`
   - `updated = 2364`
   - `failed = 0`

2. `GET /v1/us-equities-admin/ranking-coverage`
   - `universe_size = 8072`
   - `snapshot_count = 0`
   - `rankable_count = 0`
   - `strict_ready = false`
   - `ranking_mode = page_scoped_ranking`

3. `GET /v1/rankings/US/undervalued?page=1&size=5`
   - `total = 8072`
   - `source = us_security_master_page_scoped_ranking|full-universe-paged`

4. `GET /v1/peers/US/AAPL?limit=5`
   - `peer_selection_mode = snapshot_fallback`
   - `peer_candidate_count = 5`
   - `items = 5`

5. `GET /v1/platform/usage` with envelope
   - `daily_quota = 25000`
   - `remaining_quota = 24996`
   - route usage 已真实记账

## 测试

1. 定向测试：
   - `ValuationControllerTest`
   - `UsEquityAdminControllerTest`
   - `UsScheduledValuationRefreshServiceTest`
   - `ApiPlatformIntegrationTest`
   - 已通过
2. 全量测试：
   - `./mvnw.cmd -q test`
   - 已通过

## 仍需 Claude Code review 的点

1. `strict ranking` 的阈值口径：
   - freshness
   - min confidence
   - min/max market cap
   - required coverage

2. `US peers` 的产品文案：
   - `strict_peer_set`
   - `relaxed_peer_set`
   - `snapshot_fallback`
   - `template_only`

3. 平台层最小产品模型：
   - plan / entitlement / quota 是否足够
   - `rotate-key / disable` 的产品文案和审计要求

## 下一步建议

1. 把 `valuation_latest_snapshot` 覆盖率跑起来，让 `strict_ready = true`
2. 继续收紧 `US peers`，减少 `snapshot_fallback`
3. 把 `valuation_jobs` 从基础 worker/alerting 升级成真正 multi-worker 模式
