# US Equity Progress

更新日期：2026-03-31  
当前分支：`codex/java-backend-foundation`  
上一稳定提交：`6de2770`  
当前本地状态：`Longbridge + FRED + Damodaran` 接入与 `Summary / Decision / Explanation` 三层输出已推送；本轮继续完成了 `AAPL / MSFT / NVDA` 的 Longbridge live 验证，以及 `Relative Valuation peer set + source attribution` 字段补强；仓库中仍存在与 CN/JP/前端有关的未整理本地改动。  
终版对齐说明：当前进度需结合 [美股估值系统_完整终版方案_v2.docx](F:/fairvalue/美股估值系统_完整终版方案_v2.docx) 和 [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md) 一起阅读。

## 1. 当前运行状态

1. 服务地址：[http://localhost:18080](http://localhost:18080)
2. 后台页面：[http://localhost:18080/us-equities-admin.html](http://localhost:18080/us-equities-admin.html)
3. 健康检查：`GET /actuator/health = UP`
4. 当前运行端口：`18080`
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
10. `Longbridge` 主价格源代码路径
11. `FRED` 风险自由利率代码路径
12. `Damodaran` ERP / beta / 行业倍数代码路径

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
2. 结果：`38` 个测试全部通过
3. 新增覆盖点：
   - `valuation_runs` 写入
   - `valuation_method_results` 写入
   - `scenario_results` 写入
   - `reverse_dcf_results` 写入
   - `Historical Multiple` 确实读取 `market_*` 表
   - `FRED + Damodaran` 参数覆盖能写入 `wacc / terminal_growth / relative targets`

## 3.1 外部源接入状态

1. `Longbridge`
   - SDK 已接入：`io.github.longbridge:openapi-sdk:4.0.0`
   - 代码已接到 `MarketDataService -> UsMarketDataPersistenceService`
   - 默认通过环境变量开关启用，不在仓库中硬编码密钥
   - 2026-03-31 已完成真实 live 验证：`AAPL / MSFT / NVDA` 的 `price_source` 均已显示 `longbridge:2026-03-30`
2. `FRED`
   - 已支持官方 API
   - 已支持 `fredgraph.csv` fallback
   - 当前网络下直连 `fredgraph.csv` 在 `~8.2s` 超时；客户端已加超时并会自动回退，不阻塞估值接口
3. `Damodaran`
   - 已接 ERP / Beta / PE / EV/EBITDA 页面抓取
   - 当前默认开启
   - 已进入 `UsExternalValuationParameterService`

## 3.2 当前配置入口

1. `MARKET_DATA_US_LONGBRIDGE_ENABLED`
2. `LONGBRIDGE_APP_KEY`
3. `LONGBRIDGE_APP_SECRET`
4. `LONGBRIDGE_ACCESS_TOKEN`
5. `MARKET_DATA_US_FRED_ENABLED`
6. `FRED_API_KEY`
7. `MARKET_DATA_US_DAMODARAN_ENABLED`

## 4. 2026-03-31 真实验证快照

### 4.1 AAPL 本轮 live 验证

1. `POST /v1/us-equities-admin/AAPL/market-sync`
   - `market_data_raw_count = 3`
   - `market_price_daily_count = 255`
   - `market_snapshot_count = 4`
   - `market_intraday_snapshot_count = 2`
2. `GET /v1/us-equities/AAPL/valuation/summary`
   - `current_price = 246.63`
   - `fair_value_range = 164.47 / 187.34 / 215.13`
   - `data_version = longbridge:2026-03-30|stooq:2026-03-30|sec:2026-01-30`
   - `implied_expectation = aggressive`
3. `POST /v1/us-equities/AAPL/valuation/run`
   - `summary / decision / explanation` 三层结构已返回
   - `explanation.blocks = 12`
   - `decision.source_attribution.price_source = longbridge:2026-03-30`
   - `uncertainty_and_error_sources` 已修正，不再把 `Longbridge + Stooq` 误判成纯 fallback
4. `GET /v1/us-equities/AAPL/valuation/report`
   - 兼容保留旧顶层字段
   - 同时已返回结构化 `summary / decision / explanation`
5. `GET /v1/us-equities/AAPL/financial-quality`
   - `total_quality_score = 0.6631`
6. `GET /v1/us-equities/AAPL/data-quality`
   - `guidance_status = filings_only`
   - `confidence_level = 0.61`

### 4.2 AAPL 当前库内概览

1. `GET /v1/us-equities-admin/AAPL/overview`
   - `company_type = income_defensive`
   - `sector_template = us_general_quality`
   - `data_quality_audit_count = 1`
   - `market_data_raw_count = 3`
   - `market_price_daily_count = 255`
   - `market_snapshot_count = 4`
   - `market_intraday_snapshot_count = 2`

### 4.3 FRED 本轮 live 结论

1. 直接请求：`https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10`
2. 当前网络下结果：`~8197 ms timeout`
3. 当前系统行为：
   - `UsFredClient` 已配置连接超时与读取超时
   - `summary/run/report` 不会被 FRED 长时间阻塞
   - 本轮 AAPL 的 `parameter_sources / macro_sources` 仍主要来自 `valuation_parameter_set + Damodaran`

### 4.4 历史验证快照 - AAPL

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

### 4.5 MSFT

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

### 4.6 NVDA

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

1. `US-09` 的 `Longbridge` 主价格源已经完成真实 live 验证，但仍依赖环境变量显式开启。
2. `FRED` 代码链路已接通，当前网络对 `fredgraph.csv` 会超时；客户端已做快速失败和回退，但本轮还没有拿到稳定的 FRED live 数值进入 `parameter_sources`。
3. `Damodaran` 已真实参与 `beta / relative multiple` 参数归因，但 `wacc.rf / wacc.erp` 在本轮 AAPL 输出中还没有稳定压过库内种子参数。
4. `US-15` 虽已接上 `market_*` 表，但当前 `MSFT / NVDA` 的历史市场样本仍偏少，`Historical Multiple` 仍属于 `sparse` 状态。
5. `Relative Valuation` 当前已升级到 `peer set + template fallback`，但 peer set 规则仍偏宽，还不是终版严格筛选器。
6. `AAPL` 当前仍落在 `income_defensive / us_general_quality`，是否调回 `compounder / us_tech_compounder` 需要继续校准规则阈值。
7. `Summary / Decision / Explanation` 三层已经可用，12 个固定 explanation blocks 也已落地，但文案粒度和 PRD 终版措辞还值得继续打磨。
8. 工作区是 dirty 的，包含大量本轮之外的未提交文件，后续提交时必须只挑相关文件，不能整体提交或回滚。

## 7. 推荐下一步

1. 继续收紧 `Relative Valuation peer set` 的准入规则，让 peer 选择从“可用样本优先”升级到“质量过滤优先”。
2. 继续补 `AAPL` 这类 case 的标准化财务覆盖，让 `financial_standardized / financial_derived_metrics` 比 `SEC profile` fallback 更完整。
3. 继续把 `Relative Valuation peer set`、`FRED 稳定参数源` 和 `Summary / Decision / Explanation` 文案层补齐成终版结构。
