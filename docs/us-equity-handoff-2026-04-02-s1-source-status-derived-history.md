# US Equity Handoff 2026-04-02 - S1 Source Status / ERP Fallback / History Meta

## 1. 基线

1. 分支：`codex/java-backend-foundation`
2. 运行地址：[http://localhost:18080](http://localhost:18080)
3. 当前测试：`./mvnw.cmd test` -> `44` passing

## 2. 本轮已完成

### 2.1 S1-B `source-status` 安全与统一 envelope

已完成：

1. 根层新增 `as_of`
2. `source-status` 现统一返回 4 个源块：
   - `fred`
   - `longbridge`
   - `damodaran`
   - `simfin`
3. `simfin.dataset_url` 已彻底移除，不再暴露内部 bulk-download 地址
4. `status` 已收口为枚举：
   - `ok`
   - `error`
   - `disabled`
   - `unauthenticated`
   - `timeout`

当前 AAPL live 结果：

1. `fred.status = ok`
2. `longbridge.status = disabled`
3. `damodaran.status = error`
4. `simfin.status = ok`

说明：

- `longbridge` 当前是 disabled，因为本地这轮只启用了 `FRED + SimFin`
- `damodaran` 当前页面抓取返回空，因此落为 `error`

### 2.2 S1-D `erp_source` 必返

已完成：

1. `decision.source_attribution.erp_source` 禁止返回 `null`
2. 当前实现回退顺序：
   - `parameter_sources.wacc_erp`
   - `configured_template:damodaran_ref`
   - `configured_template`

当前 AAPL live 结果：

1. `risk_free_rate_source = fred_api`
2. `erp_source = configured_template:damodaran_ref`
3. `parameter_sources.wacc_erp = configured_template:damodaran_ref`

### 2.3 S1-C `valuation_run_date`

已完成：

1. `HistoryPoint` 新增 `valuation_run_date`
2. `GET /v1/valuation/history/US/{ticker}` 现在返回每个点对应的估值运行日期

当前 AAPL live 结果：

1. `2026-03-27 -> valuation_run_date=2026-03-27`
2. `2026-03-28 -> valuation_run_date=2026-03-27`
3. `2026-03-31 -> valuation_run_date=2026-03-31`

### 2.4 S1-A `SimFin derived`

已完成：

1. `UsSimfinClient.fetchDerivedMetrics(ticker)` 已新增
2. 当前默认配置：
   - `derived dataset = derived`
   - `variant = ttm`

当前 live 结论：

1. 真实探针返回：
   - `Premium dataset selected, please upgrade to at least a BASIC subscription`
2. 所以客户端入口已经具备，但当前 key 还不能把 `derived` 正式接入 peer set

## 3. 这轮改动的文件

1. [UsSimfinProperties.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSimfinProperties.java)
2. [application.properties](F:/fairvalue/src/main/resources/application.properties)
3. [UsSimfinClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSimfinClient.java)
4. [UsExternalSourceStatusService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsExternalSourceStatusService.java)
5. [UsValuationRunHistoryRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationRunHistoryRecord.java)
6. [HistoryPoint.java](F:/fairvalue/src/main/java/com/fairvalue/engine/valuation/HistoryPoint.java)
7. [ValuationRunsRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ValuationRunsRepository.java)
8. [ValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/service/ValuationService.java)
9. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
10. [UsEquityAdminControllerTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/api/us/UsEquityAdminControllerTest.java)
11. [UsValuationHistoryControllerTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/api/UsValuationHistoryControllerTest.java)
12. [UsEquityControllerTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/api/us/UsEquityControllerTest.java)
13. [us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
14. [us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)

## 4. 建议 Claude Code 优先审

1. `source-status` 四源 envelope 是否还要继续标准化字段
2. `erp_source` 的 `configured_template:damodaran_ref` 规则是否需要更严格判断
3. `valuation_run_date` 是否还需补 `run_id`
4. `SimFin derived` 当前订阅受限时，peer set 应否先接 `income/cashflow`
