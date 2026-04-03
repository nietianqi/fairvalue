# US Equity Handoff 2026-03-31 US-14 US-15

任务编号：US-14 / US-15 / US-18(部分) / US-19(部分) / US-20(部分) / US-21(部分) / US-23(部分)  
任务名称：Relative Valuation 落库、Historical Multiple 接 market_*、valuation 持久化最小闭环  
交接日期：2026-03-31  
交接人：Codex  
接手人：Claude Code  
当前分支：`codex/java-backend-foundation`  
当前基线提交：`5b7124e`  
说明：本批实现尚未单独提交，以下内容对应当前本地工作区状态。

## 1. 本批目标

1. 把 `Relative Valuation` 正式落到 `valuation_method_results`
2. 把 `Historical Multiple` 接到 `market_price_daily + market_snapshot`
3. 让一次 `valuation run` 同时写入：
   - `valuation_runs`
   - `valuation_method_results`
   - `scenario_results`
   - `reverse_dcf_results`
   - `risk_scores`
   - `report_blocks`
4. 真实跑通 `AAPL / MSFT / NVDA`

## 2. 本批不包含

1. PRD 最终态 `Longbridge` 主源切换
2. PRD 最终态 peer-set Relative Valuation 引擎
3. PRD 最终态 FCFF/DCF 编排器
4. PRD 最终态 Reverse DCF 独立解释引擎

## 3. 关键改动文件

### 3.1 新增

1. [UsValuationPersistenceService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationPersistenceService.java)
2. [ValuationRunsRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ValuationRunsRepository.java)
3. [ValuationMethodResultsRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ValuationMethodResultsRepository.java)
4. [ScenarioResultsRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ScenarioResultsRepository.java)
5. [ReverseDcfResultsRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ReverseDcfResultsRepository.java)
6. [RiskScoresRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/RiskScoresRepository.java)
7. [ReportBlocksRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ReportBlocksRepository.java)
8. [UsValuationRunRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationRunRecord.java)
9. [UsValuationMethodResultRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationMethodResultRecord.java)
10. [UsScenarioResultRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsScenarioResultRecord.java)
11. [UsReverseDcfResultRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsReverseDcfResultRecord.java)
12. [UsRiskScoreRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsRiskScoreRecord.java)
13. [UsReportBlockRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsReportBlockRecord.java)
14. [UsMarketPriceDailyRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsMarketPriceDailyRecord.java)
15. [UsMarketSnapshotRecord.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsMarketSnapshotRecord.java)
16. [us-equity-handoff-2026-03-31-us14-us15.md](F:/fairvalue/docs/us-equity-handoff-2026-03-31-us14-us15.md)

### 3.2 修改

1. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
2. [MarketPriceDailyRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/MarketPriceDailyRepository.java)
3. [MarketSnapshotRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/MarketSnapshotRepository.java)
4. [UsEquityControllerTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/api/us/UsEquityControllerTest.java)
5. [us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
6. [us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)

## 4. 已完成内容

1. `Relative Valuation` 已作为独立方法名 `relative_valuation` 写入 `valuation_method_results`
   - 当前由 `ev_ebitda + peg` 聚合而来
2. `Historical Multiple` 已从 `market_price_daily + market_snapshot` 读取历史锚点
   - 当历史样本不足时会落 `sparse/fallback` 注释
   - 不会伪装成完整历史引擎
3. `valuation run` 已正式形成最小持久化闭环
   - `valuation_runs`
   - `valuation_method_results`
   - `scenario_results`
   - `reverse_dcf_results`
   - `risk_scores`
   - `report_blocks`
4. `forceMethods` 现在支持 `relative_valuation` 这个别名，会自动展开到相对估值组件
5. 已新增回归测试，验证：
   - `valuation_runs` 写入成功
   - `valuation_method_results` 包含 `relative_valuation` 与 `historical_multiple`
   - `Historical Multiple` 的 `assumptions_json` 确实包含 `market_price_daily`
6. 真实跑通 `AAPL / MSFT / NVDA`

## 5. 真实验证结果

服务地址：[http://localhost:18080](http://localhost:18080)

### 5.1 AAPL

1. `POST /v1/us-equities-admin/AAPL/sec-sync`
   - `submission_documents_upserted = 1000`
   - `raw_facts_inserted = 24579`
2. `POST /v1/us-equities-admin/AAPL/ir-sync`
   - `documents_upserted = 20`
3. `POST /v1/us-equities-admin/AAPL/market-sync`
   - `market_price_daily_count = 4`
   - `market_snapshot_count = 4`
4. `POST /v1/us-equities-admin/AAPL/standardize`
   - `standardized_row_count = 280`
   - `derived_metric_row_count = 280`
   - `financial_quality_row_count = 167`
5. `POST /v1/us-equities/AAPL/valuation/run`
   - `blended_intrinsic_value = 230.10`
   - `fair_value_range = 197.54 - 262.67`
   - `confidence_level = 0.82`
6. 最新 run 的方法行：
   - `dcf`
   - `reverse_dcf`
   - `relative_valuation`
   - `historical_multiple`

### 5.2 MSFT

1. `sec-sync`: `submission_documents_upserted = 1008`, `raw_facts_inserted = 31574`
2. `ir-sync`: `documents_upserted = 10`
3. `market-sync`: `market_price_daily_count = 1`, `market_snapshot_count = 1`
4. `standardize`: `standardized_row_count = 301`, `derived_metric_row_count = 301`, `financial_quality_row_count = 159`
5. `valuation/run`: `blended_intrinsic_value = 381.59`, `fair_value_range = 329.18 - 434.01`, `confidence_level = 0.83`

### 5.3 NVDA

1. `sec-sync`: `submission_documents_upserted = 1000`, `raw_facts_inserted = 26571`
2. `ir-sync`: `documents_upserted = 0`
3. `market-sync`: `market_price_daily_count = 1`, `market_snapshot_count = 1`
4. `standardize`: `standardized_row_count = 265`, `derived_metric_row_count = 265`, `financial_quality_row_count = 163`
5. `valuation/run`: `blended_intrinsic_value = 208.03`, `fair_value_range = 174.88 - 241.19`, `confidence_level = 0.81`

## 6. 直接查库结果

### 6.1 AAPL

1. `valuation_runs = 2`
2. `valuation_method_results = 8`
3. `scenario_results = 6`
4. `reverse_dcf_results = 2`
5. `risk_scores = 6`
6. `report_blocks = 16`

说明：AAPL 在这批之前已有 1 次真实 valuation 验证，因此累计条数为 2 组。

### 6.2 MSFT

1. `valuation_runs = 1`
2. `valuation_method_results = 4`
3. `scenario_results = 3`
4. `reverse_dcf_results = 1`
5. `risk_scores = 3`
6. `report_blocks = 8`

### 6.3 NVDA

1. `valuation_runs = 1`
2. `valuation_method_results = 4`
3. `scenario_results = 3`
4. `reverse_dcf_results = 1`
5. `risk_scores = 3`
6. `report_blocks = 8`

## 7. 未完成内容

1. `US-09` 还没切到 `Longbridge` 主源
2. `US-15` 还没有长周期历史样本回补，当前 `Historical Multiple` 仍常见 `sparse`
3. `US-16` DCF/FCFF 仍是启发式策略，不是 PRD 最终模型
4. `US-17` Reverse DCF 仍未形成独立可解释引擎
5. `US-19` 风险矩阵仍是轻量打分映射，不是 PRD 完整风险修正
6. `US-21` explanation blocks 已落表，但文案模板仍值得 Claude Code 继续完善

## 8. 风险与注意事项

1. 当前工作区是 dirty 的，且不止这一批改动；提交时一定要精准挑文件，不能整体提交。
2. `AAPL` 在当前真实规则下会落到 `general_quality / us_general_quality`，这不是 bug，而是规则阈值结果；如果产品上希望它回到 `compounder / us_tech_compounder`，需要调分类规则。
3. `MSFT / NVDA` 当前市场历史样本只有 1 个，因此 `Historical Multiple` 虽已接表，但仍属于稀疏样本阶段。
4. 运行中的服务 PID 为 `24592`，地址是 `http://localhost:18080`。
5. embedded PostgreSQL 当前由应用进程托管，停止服务会同时停止数据库实例。

## 9. 建议 Claude Code 接手的下一步

1. `US-16`：审校并补全 PRD 版 DCF/FCFF 规则与 assumptions 结构
2. `US-17`：定义 Reverse DCF 的独立解释模板、输入口径和验收标准
3. `US-19`：细化风险矩阵规则，把概率/影响/WACC/scenario weight 的规则文档写实
