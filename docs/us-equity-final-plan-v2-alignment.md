# US Equity Final Plan V2 Alignment

更新日期：2026-03-31  
参考基线：[美股估值系统_完整终版方案_v2.docx](F:/fairvalue/美股估值系统_完整终版方案_v2.docx)  
当前仓库：`F:\fairvalue`  
当前技术栈：`Java + Spring Boot + PostgreSQL + Flyway`

## 1. 文档目的

这份文档不重复写一遍 PRD，而是回答 4 个更实际的问题：

1. 终版方案 v2 的核心要求是什么。
2. 当前仓库已经实现到哪一层。
3. 当前 Java 代码分别落在哪些目录和类上。
4. 接下来应该按什么顺序继续收敛，而不是推翻重来。

一句话结论：

**产品规则按终版方案 v2 对齐，工程实现继续沿用当前 Java 基线，按层补齐，不做技术栈重写。**

## 2. 终版方案 v2 的硬基线

### 2.1 主链路

终版方案要求整个系统围绕这条链路组织：

**原始事实 -> 标准化财务 -> 市场价格 -> 多模型估值 -> 风险修正 -> 解释输出**

### 2.2 数据源终版

生产版推荐：

1. `SEC EDGAR`
2. `公司 IR`
3. `长桥 API`
4. `FRED`
5. `Damodaran`

研究/增强源：

1. `FINRA`
2. `SimFin`
3. `yfinance / Stooq`

### 2.3 方法终版

Always-On 4 方法：

1. `DCF / FCFF`
2. `Historical Multiple`
3. `Relative Valuation`
4. `Reverse DCF`

按公司类型切专用方法：

1. `Compounder / Profitable Growth`
2. `Hyper-growth SaaS`
3. `Cyclical`
4. `Bank / Insurance`
5. `REIT`
6. `Biotech pre-revenue`
7. `Conglomerate / HoldCo`

### 2.4 输出终版

输出必须固定分成 3 层：

1. `Summary`
2. `Decision`
3. `Explanation`

Explanation 必须固定成 12 个 block：

1. `one_line_verdict`
2. `executive_summary`
3. `data_quality_audit`
4. `business_and_moat`
5. `financial_quality_scorecard`
6. `growth_and_catalysts`
7. `risk_matrix`
8. `valuation_breakdown`
9. `scenario_matrix`
10. `final_fair_value`
11. `actionable_framework`
12. `uncertainty_and_error_sources`

## 3. 当前项目总结构

当前顶层 Java 包：

1. `com.fairvalue.engine.api`
2. `com.fairvalue.engine.cn`
3. `com.fairvalue.engine.config`
4. `com.fairvalue.engine.domain`
5. `com.fairvalue.engine.jp`
6. `com.fairvalue.engine.repository`
7. `com.fairvalue.engine.service`
8. `com.fairvalue.engine.us`
9. `com.fairvalue.engine.valuation`

当前与美股终版最相关的目录：

1. [src/main/java/com/fairvalue/engine/us](F:/fairvalue/src/main/java/com/fairvalue/engine/us)
2. [src/main/java/com/fairvalue/engine/api](F:/fairvalue/src/main/java/com/fairvalue/engine/api)
3. [src/main/java/com/fairvalue/engine/repository](F:/fairvalue/src/main/java/com/fairvalue/engine/repository)
4. [src/main/java/com/fairvalue/engine/valuation](F:/fairvalue/src/main/java/com/fairvalue/engine/valuation)
5. [src/main/resources/db/migration](F:/fairvalue/src/main/resources/db/migration)
6. [src/main/resources/rules](F:/fairvalue/src/main/resources/rules)
7. [docs](F:/fairvalue/docs)

## 4. 六层架构与当前代码映射

| 终版层 | 目标职责 | 当前代码位置 | 当前状态 | 主要差距 |
| --- | --- | --- | --- | --- |
| L1 原始事实层 | SEC / IR / 原始文档与原始 facts 落库 | `UsSecClient`, `UsSecDocumentSyncService`, `UsCompanyIrClient`, `UsCompanyIrSyncService`, `source_documents`, `source_document_facts_raw` | 已落地 | IR 仍是有限 feed，不是全市场抓取 |
| L2 标准化财务层 | FY/Q/TTM、标准化财务、派生指标 | `UsFinancialStandardizationService`, `UsFinancialDerivedMetricsService`, `financial_standardized`, `financial_derived_metrics` | 已落地但不均衡 | `AAPL` 等 case 覆盖仍偏薄，需要继续补厚 |
| L3 市场与宏观层 | 价格、快照、历史价格、Rf、ERP、行业参数 | `UsMarketDataPersistenceService`, `UsStooqClient`, `market_*` 表 | 部分落地 | 正式主价格源仍不是 Longbridge；FRED / Damodaran 还没接入 |
| L4 估值计算层 | DCF、Historical、Relative、Reverse DCF、专用模板 | `UsConfiguredValuationModelsService`, `UsValuationConfigService`, `UsEquityValuationService` | 主链路已跑通 | Relative 还不是完整 peer set；Bank/REIT/Biotech 等专用模板未完成 |
| L5 风险修正层 | 风险矩阵、WACC 修正、情景权重、value trap | `UsRiskMatrixService`, `risk_scores` | 已落地 | 风险因子和外部输入还可继续加深 |
| L6 解释输出层 | Summary / Decision / Explanation 三层输出 | `report_blocks`, `UsValuationPersistenceService`, API DTO | 已具雏形 | 12 个固定 explanation blocks 还没全部标准化 |

## 5. 当前已经做成的能力

### 5.1 数据链路

已经具备：

1. `SEC ticker universe -> security_master / security_identifier_map`
2. `submissions -> source_documents`
3. `companyfacts -> source_document_facts_raw`
4. 公司 IR 元数据 -> `source_documents`
5. `financial_standardized`
6. `financial_derived_metrics`
7. `financial_quality_scores`
8. `data_quality_audit`
9. `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`

### 5.2 估值与持久化

已经具备：

1. `valuation_runs`
2. `valuation_method_results`
3. `scenario_results`
4. `reverse_dcf_results`
5. `risk_scores`
6. `report_blocks`

### 5.3 已接通的方法

当前主链路已经跑通：

1. `dcf`
2. `reverse_dcf`
3. `relative_valuation`
4. `historical_multiple`

## 6. 当前和终版 v2 的真实差距

### 6.1 数据源差距

1. 价格主源仍是 `Stooq + SEC enrich`，不是终版要求的 `Longbridge`。
2. `FRED` 尚未接入，因此 `risk-free rate` 还没有正式宏观源。
3. `Damodaran` 尚未接入，因此 `ERP / 行业 beta / 行业倍数` 还没有正式参数源。
4. `FINRA` 和 `SimFin` 仍未进入正式链路。

### 6.2 财务标准化差距

1. 标准化能力已经有，但个股覆盖不均衡。
2. `AAPL` 等案例仍存在“标准化层偏薄、需要 profile fallback 托底”的问题。
3. 要达到终版要求，标准化层需要比当前更稳定地覆盖 `FCFF / ROIC / leverage / diluted shares / tax rate / debt stack`。

### 6.3 方法体系差距

1. Always-On 4 方法已接通。
2. `Relative Valuation` 仍偏模板倍数，不是完整 `peer set` 引擎。
3. `Historical Multiple` 已接表，但还不是正式长周期区间引擎。
4. `Bank / Insurance / REIT / Biotech / Conglomerate` 的专用模板还没落完。

### 6.4 输出层差距

1. `summary / report / run` 已可用。
2. `Explanation` 还没有完整收敛到终版要求的 12 个固定 blocks。
3. 仍需把 `Summary / Decision / Explanation` 三层明确固化到 API 契约和报告模板。

### 6.5 工程层差距

1. 终版文档原始建议是 `Python + FastAPI`，但当前仓库已经是 `Java + Spring Boot`。
2. 这里不建议做技术栈重写。
3. 正确做法是：继续保留 Java 基线，在职责划分、数据链路和输出结构上对齐终版。

## 7. 当前代码应该怎么理解

如果只看当前 Java 项目，可以这样理解：

### 7.1 `com.fairvalue.engine.us`

这是美股主战场，里面已经同时承载了：

1. ingestion
2. normalization
3. classification
4. quality
5. valuation
6. risk
7. report persistence

这意味着：

1. 当前代码不是“没架构”，而是“职责已经成形，但目录还偏聚合”。
2. 后面如果要继续整理，优先做职责边界清晰化，不要先大规模移动类文件。

### 7.2 `com.fairvalue.engine.repository`

这里是表结构和持久化边界，已经能直接映射终版方案里的核心表。

### 7.3 `com.fairvalue.engine.api`

这里承接外部接口和后台管理入口。终版里要求的 `profile / data-quality / financial-quality / valuation run / summary / report`，都应该继续在这里稳定暴露。

### 7.4 `com.fairvalue.engine.valuation`

这里适合继续沉淀跨市场、跨模板的策略层；但美股专属复杂逻辑仍应优先留在 `us` 下逐步收敛。

## 8. 推荐重构顺序

### 第一批：先补终版主数据源

1. `Longbridge`
2. `FRED`
3. `Damodaran`

理由：

1. 这是终版 v2 和当前实现差距最大的地方。
2. 不补主源，后面的 WACC、Relative、Historical 很难真正达到产品版。

### 第二批：补方法质量

1. `Relative Valuation -> peer set`
2. `Historical Multiple -> 长周期历史区间`
3. `AAPL / MSFT / NVDA` 标准化财务补厚

### 第三批：补输出层

1. `Summary / Decision / Explanation` 三层 API 固化
2. 12 个 explanation blocks 固化
3. 报告模板统一

### 第四批：补 orchestration

1. ingestion jobs
2. normalization jobs
3. valuation jobs
4. 定时调度和重算策略

## 9. Codex 与 Claude Code 的分工建议

### 9.1 Codex 更适合负责

1. Java 实现
2. Repository / Service / Controller
3. 数据入库链路
4. Flyway migration
5. 启动服务、联调接口、跑测试、做真实验证

### 9.2 Claude Code 更适合负责

1. 终版规则校对
2. peer set 设计
3. 风险 taxonomy 细化
4. explanation blocks 规范
5. API 字段验收
6. 文档和 handoff 回写

## 10. 给后续协作的一句话

**不要把这次“重新梳理”理解成重写项目；正确的方向是用终版方案 v2 作为产品与规则基线，把当前 Java 代码按层补齐、按职责收敛、按数据源升级。**
