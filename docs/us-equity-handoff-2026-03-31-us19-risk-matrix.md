# US Equity Handoff 2026-03-31 (US-19 / AAPL FCFF Coverage)

## 1. 本批任务

1. `US-19`：把风险矩阵接成 PRD 版修正规则
2. 补 `AAPL` 的结构化 FCFF 覆盖，减少 fallback
3. 精确挑选本批相关文件，准备单独提交

## 2. 本批核心实现

### 2.1 风险矩阵正式接线

1. 新增 [UsRiskMatrixService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsRiskMatrixService.java)
   - 输出 `UsRiskMatrixResult`
   - 把风险拆成 `wacc / scenario_weight / margin_of_safety`
   - 当前已覆盖：
     - `fcff_structure_gap`
     - `data_quality_gap`
     - `leverage`
     - `dilution`
     - `governance_execution`
     - `multiple_compression`
     - `execution_variance`
     - `value_trap`
2. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
   - 在配置化估值前先计算 `riskMatrixResult`
   - 把 `waccAdjustment` 真正接进 `effective_wacc`
   - 把 `marginOfSafetyAdjustment` 写进 drivers / explanation
   - 把 `confidencePenalty` 接进 `confidenceBase`
3. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
   - `scenario_matrix` 现在直接吃 `UsRiskMatrixResult`
   - `margin_of_safety` 改成 `raw_margin - required_margin`
   - explanation blocks 增加：
     - `risk_commentary`
     - `margin_of_safety`
4. [UsValuationPersistenceService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationPersistenceService.java)
   - `risk_scores.adjustment_type` 不再写死
   - API 的 `margin_of_safety` 会映射成库里的 `mos`
   - `valuation_runs.confidence_level` 改写入风险修正后的有效置信度

### 2.2 AAPL 结构化 FCFF 覆盖补强

1. [UsValuationModelContext.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationModelContext.java)
   - 新增 `secProfile`
   - 新增 `latestQualityScore`
2. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
   - `revenue` 优先取 `TTM -> FY -> Q` 的正值
   - `shares` 优先取 `diluted/basic shares`
   - 如果标准化财务不完整，回退到 `SEC profile`：
     - `annualRevenue`
     - `sharesOutstanding`
     - `annualOperatingCashFlow`
     - `annualCapex`
     - `cash`
     - `totalDebt`
     - `ebitdaProxy`
   - `current_fcf_margin` 支持从 `free_cash_flow / revenue` 和 `SEC profile FCFF` 推导
3. 结果：
   - `AAPL` 的 `reverse_dcf.notes.structured_inputs` 已从 `false` 变成 `true`
   - 说明 FCFF 不再完全依赖 snapshot fallback

## 3. 测试

1. 命令：`./mvnw.cmd test`
2. 结果：`37` 个测试全部通过
3. 新增校验点：
   - `risk_scores.adjustment_type` 同时出现 `wacc / scenario_weight / mos`
   - `risk_matrix` 至少能返回 3 类以上风险

## 4. 真实运行验证

服务地址：

1. [http://localhost:18080](http://localhost:18080)
2. [http://localhost:18080/actuator/health](http://localhost:18080/actuator/health)
3. 当前运行 PID：`32552`

### 4.1 AAPL

1. `POST /v1/us-equities-admin/AAPL/sec-sync`
   - `submission_documents_upserted = 1000`
   - `raw_facts_inserted = 24579`
2. `POST /v1/us-equities-admin/AAPL/standardize`
   - `standardized_row_count = 2`
   - `derived_metric_row_count = 2`
   - `financial_quality_row_count = 2`
3. `POST /v1/us-equities/AAPL/valuation/run`
   - `blended_intrinsic_value = 156.57`
   - `fair_value_range = 135.96 - 181.90`
   - `confidence_level = 0.80`
   - `margin_of_safety = -0.85`
4. `reverse_dcf`
   - `implied_expectation = aggressive`
   - `structured_inputs = true`
   - `growth_gap = -0.19`
5. `risk_matrix`
   - `data_quality_gap -> wacc +0.01`
   - `governance_execution -> margin_of_safety +0.02`
   - `multiple_compression -> scenario_weight +0.03`
   - `execution_variance -> scenario_weight +0.03`
6. 当前分类：`general_quality / us_general_quality`

## 5. 当前边界

1. `AAPL` 虽然已经从 `structured_inputs=false` 收口到 `true`，但主要还是靠 `SEC profile` 托底，不代表标准化财务层已经足够完整。
2. `standardized_row_count=2` 说明 `US-10 / US-11` 在这只股票上的近期结构化覆盖仍偏薄，尤其是 `TTM / FCF / debt / tax rate`。
3. 风险矩阵现在已经是“会改估值”的版本了，但风险因子仍然是第一版规则，不是最终完整版 PRD taxonomy。
4. 价格主源仍是 `Stooq + SEC enrich`，还没切到 `Longbridge`。

## 6. 建议下一步

1. 继续补 `AAPL` 的标准化财务覆盖，让 `financial_standardized / financial_derived_metrics` 更厚，不再主要依赖 `SEC profile` 托底
2. 把 `Relative Valuation` 升级成 peer set 引擎
3. 把 `US-22 / US-23` 的 explanation 和 API 输出细节继续补齐
