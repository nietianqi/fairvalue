# US Equity Progress

更新日期：2026-03-31  
当前分支：`codex/java-backend-foundation`  
当前基线提交：`5b7124e`  
当前本地状态：含未提交的 `US-19 / AAPL FCFF 覆盖补强 / 文档回写` 相关实现。

## 1. 当前运行状态

1. 服务地址：[http://localhost:18080](http://localhost:18080)
2. 后台页面：[http://localhost:18080/us-equities-admin.html](http://localhost:18080/us-equities-admin.html)
3. 健康检查：`GET /actuator/health = UP`
4. 当前运行 PID：`32552`
5. 启动方式：Spring Boot + embedded PostgreSQL + Flyway

## 2. 已完成的真实能力

### 2.1 数据接入与标准化

1. SEC universe 同步
2. `submissions -> source_documents`
3. `companyfacts -> source_document_facts_raw`
4. 公司 IR feed 元数据 -> `source_documents`
5. `financial_standardized`
6. `financial_derived_metrics`
7. `financial_quality_scores`
8. `data_quality_audit`
9. `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`

### 2.2 估值落库

1. `valuation_runs`
2. `valuation_method_results`
3. `scenario_results`
4. `reverse_dcf_results`
5. `risk_scores`
6. `report_blocks`

### 2.3 当前估值方法落库形态

当前真实 `valuation run` 默认会落出 4 个核心方法：

1. `dcf`
2. `reverse_dcf`
3. `relative_valuation`
4. `historical_multiple`

说明：

- `dcf` 现已由 `sector_template_config + valuation_parameter_set` 驱动，并支持 `custom_assumptions` 覆盖。
- `reverse_dcf` 现已成为独立可解释引擎，会输出隐含增长、隐含利润率与 notes。
- `historical_multiple` 当前依赖 `market_price_daily + market_snapshot`；当历史样本不足时会明确标记 `sparse/fallback`，不会伪装成完整历史引擎。

## 3. 测试状态

1. 命令：`./mvnw.cmd test`
2. 结果：`37` 个测试全部通过
3. 新增覆盖点：
   - `valuation_runs` 写入
   - `valuation_method_results` 写入
   - `scenario_results` 写入
   - `reverse_dcf_results` 写入
   - `Historical Multiple` 确实读取 `market_*` 表

## 4. 2026-03-31 真实验证快照

### 4.1 AAPL

1. `POST /v1/us-equities-admin/AAPL/sec-sync`
   - `submission_documents_upserted = 1000`
   - `raw_facts_inserted = 24579`
   - `placeholder_documents_upserted = 26`
2. `POST /v1/us-equities-admin/AAPL/ir-sync`
   - `documents_upserted = 20`
3. `POST /v1/us-equities-admin/AAPL/market-sync`
   - `market_price_daily_count = 4`
   - `market_snapshot_count = 4`
4. `POST /v1/us-equities-admin/AAPL/standardize`
   - `standardized_row_count = 2`
   - `derived_metric_row_count = 2`
   - `financial_quality_row_count = 2`
5. `POST /v1/us-equities/AAPL/valuation/run`
   - `blended_intrinsic_value = 156.57`
   - `fair_value_range = 135.96 - 181.90`
   - `confidence_level = 0.80`
6. 当前 `Reverse DCF`
   - `implied_expectation = aggressive`
   - `structured_inputs = true`
   - `growth_gap = -0.19`
7. 当前分类：`general_quality / us_general_quality`
8. 当前 `guidance_status = filings_only`
9. 当前风险矩阵修正
   - `wacc_adjustment = 0.01`
   - `required_margin_of_safety = 0.27`
   - `bear/bull shift = +0.06 / -0.04`

### 4.2 MSFT

1. `POST /v1/us-equities-admin/MSFT/sec-sync`
   - `submission_documents_upserted = 1008`
   - `raw_facts_inserted = 31574`
2. `POST /v1/us-equities-admin/MSFT/ir-sync`
   - `documents_upserted = 10`
3. `POST /v1/us-equities-admin/MSFT/market-sync`
   - `market_price_daily_count = 1`
   - `market_snapshot_count = 1`
4. `POST /v1/us-equities-admin/MSFT/standardize`
   - `standardized_row_count = 301`
   - `derived_metric_row_count = 301`
   - `financial_quality_row_count = 159`
5. `POST /v1/us-equities/MSFT/valuation/run`
   - `blended_intrinsic_value = 316.51`
   - `fair_value_range = 262.93 - 390.87`
   - `confidence_level = 0.86`
6. 当前 `Reverse DCF`
   - `implied_expectation = aggressive`
   - `structured_inputs = true`
   - `growth_gap = -0.20`
7. 当前分类：`compounder / us_tech_compounder`
8. 当前 `guidance_status = filings_only`

### 4.3 NVDA

1. `POST /v1/us-equities-admin/NVDA/sec-sync`
   - `submission_documents_upserted = 1000`
   - `raw_facts_inserted = 26571`
2. `POST /v1/us-equities-admin/NVDA/ir-sync`
   - `documents_upserted = 0`
3. `POST /v1/us-equities-admin/NVDA/market-sync`
   - `market_price_daily_count = 1`
   - `market_snapshot_count = 1`
4. `POST /v1/us-equities-admin/NVDA/standardize`
   - `standardized_row_count = 265`
   - `derived_metric_row_count = 265`
   - `financial_quality_row_count = 163`
5. `POST /v1/us-equities/NVDA/valuation/run`
   - `blended_intrinsic_value = 115.36`
   - `fair_value_range = 98.79 - 137.95`
   - `confidence_level = 0.84`
6. 当前 `Reverse DCF`
   - `implied_expectation = aggressive`
   - `structured_inputs = true`
   - `growth_gap = -0.13`
7. 当前分类：`compounder / us_tech_compounder`
8. 当前 `guidance_status = filings_only`

## 5. 直接查库验证结果

### 5.1 最新 valuation run 的方法行

当前最新 run 会写入：

1. `dcf`
2. `reverse_dcf`
3. `relative_valuation`
4. `historical_multiple`

### 5.2 真实库内计数

- `AAPL`
  - `valuation_runs = 2`
  - `valuation_method_results = 8`
  - `scenario_results = 6`
  - `reverse_dcf_results = 2`
  - `risk_scores = 6`
  - `report_blocks = 16`
- `MSFT`
  - `valuation_runs = 1`
  - `valuation_method_results = 4`
  - `scenario_results = 3`
  - `reverse_dcf_results = 1`
  - `risk_scores = 3`
  - `report_blocks = 8`
- `NVDA`
  - `valuation_runs = 1`
  - `valuation_method_results = 4`
  - `scenario_results = 3`
  - `reverse_dcf_results = 1`
  - `risk_scores = 3`
  - `report_blocks = 8`

说明：`AAPL` 在这批之前已经做过一次真实 valuation 验证，所以累计条数为 2 组。

## 6. 已知边界

1. `US-09` 虽已正式落表，但价格主源仍是 `Stooq + SEC enrich`，尚未切到 PRD 最终态 `Longbridge`。
2. `US-15` 虽已接上 `market_*` 表，但当前 `MSFT / NVDA` 只有 1 个市场快照样本，`Historical Multiple` 仍属于 `sparse` 状态。
3. `Relative Valuation` 当前仍以模板目标倍数为主，还不是完整 peer set 引擎。
4. `AAPL` 当前已从 `structured_inputs=false` 收口到 `true`，但主要依赖 `financial_standardized + SEC profile fallback`；标准化财务覆盖本身仍偏稀疏，后续还要继续补厚。
5. 工作区是 dirty 的，包含大量本轮之外的未提交文件，后续提交时必须只挑相关文件，不能整体提交或回滚。

## 7. 推荐下一步

1. 继续补 `AAPL` 这类 case 的标准化财务覆盖，让 `financial_standardized / financial_derived_metrics` 比 SEC profile fallback 更完整。
2. 把 `Relative Valuation` 升级成 peer set 版而不是模板目标倍数版。
3. 把 `US-22 / US-23` 的 explanation 和 API 输出继续补齐成 PRD 版。
