# US Equity Handoff — Universe Backfill And Ranking Coverage

## 本轮 Codex 已完成

1. 新增 `POST /v1/us-equities-admin/snapshot-backfill/universe`
   - 参数：
     - `page`
     - `size`
     - `mode=run|queue`
     - `priority`
   - `run` 会立即执行估值并落 `valuation_latest_snapshot`
   - `queue` 会写入 `valuation_jobs`，由 worker 异步消费

2. `UsSnapshotBackfillService` 已支持：
   - `top50_cross_industry_v1`
   - `us_universe_page_{page}_size_{size}`

3. `US admin` 前端已新增：
   - `Top 50 Backfill`
   - `Top 100 Queue`
   - `严格榜单覆盖率` 卡片

4. `ranking-coverage` 现在已经接入 admin 前端
   - 显示：
     - `universe_size`
     - `snapshot_count`
     - `rankable_count`
     - `snapshot_coverage / rankable_coverage`
     - `ranking_mode / strict_ready`

## 关键文件

- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSnapshotBackfillService.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationJobService.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/api/UsEquityAdminController.java`
- `F:/fairvalue/src/main/resources/static/us-equities-admin.html`
- `F:/fairvalue/src/main/resources/static/assets/us-admin-console.js`
- `F:/fairvalue/src/main/resources/static/assets/us-admin-console.css`

## 已验证

- `UsEquityAdminControllerTest` 通过
- 新增 `snapshot-backfill/universe` 测试通过
- `node --check` 校验 `us-admin-console.js` 通过

## 建议 Claude Code Review

1. `Top 100 Queue` 文案是否要改为更明确的“批量补快照”
2. `strict_ready=false` 时，admin 页面是否应加 warning banner
3. `mode=run|queue` 是否需要更明显的风险说明
