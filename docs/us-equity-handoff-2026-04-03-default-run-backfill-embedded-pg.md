# US Equity Handoff — 2026-04-03 — Default Run / Backfill / Embedded PG Reuse

## 本轮完成

1. `POST /v1/us-equities/{ticker}/valuation/run` 现在支持空 body 默认参数：
   - `style = balanced`
   - `horizon = 6-18m`
   - `useConsensus = true`
2. 新增 `POST /v1/us-equities-admin/snapshot-backfill/top50`
   - 直接回填 Top 50 跨行业美股 snapshot
   - 返回 `completed / missing / failed` 明细
3. `EmbeddedPostgresEnvironmentPostProcessor` 现在会优先复用本地已有 `5432` PostgreSQL
   - 本地测试与开发运行不再因为重复 `initdb` 直接失败

## 关键文件

- [UsValuationRunRequest.java](F:/fairvalue/src/main/java/com/fairvalue/engine/api/dto/us/UsValuationRunRequest.java)
- [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
- [UsSnapshotBackfillService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSnapshotBackfillService.java)
- [UsEquityAdminController.java](F:/fairvalue/src/main/java/com/fairvalue/engine/api/UsEquityAdminController.java)
- [EmbeddedPostgresEnvironmentPostProcessor.java](F:/fairvalue/src/main/java/com/fairvalue/engine/config/EmbeddedPostgresEnvironmentPostProcessor.java)

## 测试

- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=UsEquityControllerTest,UsEquityAdminControllerTest" test`
- `./mvnw.cmd -q test`

## live 验证

1. `POST /v1/us-equities/AAPL/valuation/run`
   - 空 body 可用
   - 返回 4 个核心方法与完整 `summary / decision / explanation`
2. `POST /v1/us-equities-admin/snapshot-backfill/top50`
   - 最近一次 live：
     - `requested = 50`
     - `completed = 38`
     - `missing = 12`
     - `failed = 0`
3. `GET /v1/us-equities-admin/ranking-coverage`
   - 最近一次 live：
     - `snapshot_count = 922`
     - `rankable_count = 496`
     - `strict_ready = false`

## 给 Claude Code 的 review 重点

1. Top 50 回填名单是否需要换成真正产品覆盖名单
2. `missing_in_security_master` 的 ticker 是否需要单独补 universe 规则
3. `strict_ready` 门槛是否需要继续下调，还是继续扩 snapshot coverage
