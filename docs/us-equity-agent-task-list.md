# US Equity Agent Task List

更新日期：2026-04-01  
当前分支：`codex/java-backend-foundation`  
上一稳定提交：`6cb9dc1`  
当前状态说明：本文件反映仓库当前美股代码基线；`Longbridge` 已完成 `AAPL / MSFT / NVDA` 新一轮真实 live 验证，`Summary / Decision / Explanation` 三层结构已落地；`Relative Valuation` 已进一步收紧为严格 peer set 规则，并把 `收入增长区间` 接进了 peer 过滤；仓库中仍存在与 CN/JP/前端有关的未整理本地改动。  
终版对齐说明：任务优先级已按 [美股估值系统_完整终版方案_v2.docx](F:/fairvalue/美股估值系统_完整终版方案_v2.docx) 和 [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md) 重新理解。

## 1. 硬约束

1. 至少 3 种估值方法参与 blended value，`Reverse DCF` 必跑。
2. 所有估值方法都必须保留 `assumptions_json`。
3. 标准化财务结果必须可追溯到 `source_document_id`。
4. 禁止用 synthetic 数据伪装真实生产结果。

## 2. 状态定义

- `已完成`：代码、测试、最小真实验证都已完成。
- `进行中`：主链路已落地，但还没达到 PRD 最终态。
- `待做`：尚未正式落地。

## 3. 当前任务状态

| ID | 任务 | 状态 | 当前说明 |
| --- | --- | --- | --- |
| US-01 | 数据库接入与迁移框架 | 已完成 | PostgreSQL / Flyway / embedded Postgres 已稳定运行。 |
| US-02 | schema 初版 | 已完成 | 核心估值表已迁移。 |
| US-03 | 初始化字典数据 | 已完成 | `source_registry`、`valuation_method_catalog`、`sector_template_config` 已入库。 |
| US-04 | Security Master / Identifier Map | 已完成 | 支持 `ticker / symbol_full / CIK -> security_id`。 |
| US-05 | SEC Ticker Universe | 已完成 | 支持全量 universe 同步与 upsert。 |
| US-06 | SEC 文档元数据接入 | 已完成 | `submissions -> source_documents`。 |
| US-07 | SEC raw facts 接入 | 已完成 | `companyfacts -> source_document_facts_raw`。 |
| US-08 | 公司 IR 文档元数据 | 已完成 | 已覆盖 `AAPL / MSFT / NVDA` 官方 feed。 |
| US-09 | 市场数据层接入 | 进行中 | 已正式写入 `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`，`Longbridge` 主源 SDK、行情抓取和落表链路已接入并完成 `AAPL / MSFT / NVDA` live 验证；默认仍需通过环境变量显式打开，当前 fallback 仍保留 `Stooq + SEC enrich`，这轮 live 因未提供 `FRED_API_KEY` 维持 `FRED disabled`。 |
| US-10 | 财务标准化管道 | 已完成 | `financial_standardized` 已支持 `FY / Q / TTM`。 |
| US-11 | 派生指标引擎 | 已完成 | `ROIC / FCF margin / accruals / leverage / book value per share` 已落库。 |
| US-12 | 数据质量审计引擎 | 已完成 | `data_quality_audit` 已正式落库，`data-quality` 接口优先读取审计表。 |
| US-13 | company_type / sector_template 选择器 | 已完成 | 已配置化并写回 `security_master`；`AAPL` 在当前真实规则下会落到 `general_quality / us_general_quality`。 |
| US-14 | Relative Valuation | 进行中 | 已正式聚合为 `relative_valuation` 并落到 `valuation_method_results`；当前已升级为“行业优先 -> sector_template -> company_type -> sector”的分层 peer set，并叠加市值/收入增长/FCF margin/ROIC/可用倍数过滤；行业专属 rule 仍待补齐。 |
| US-15 | Historical Multiple | 进行中 | 已接到 `market_price_daily + market_snapshot`，并写入 `valuation_method_results`；当前历史样本仍偏稀疏，属于 PRD 的最小落地版。 |
| US-16 | DCF / FCFF | 已完成 | 已升级成 `sector_template_config + valuation_parameter_set` 驱动的可配置 FCFF 模型，并支持 `custom_assumptions` 覆盖。 |
| US-17 | Reverse DCF | 已完成 | 已升级成独立可解释引擎，支持隐含增长求解、`reverse_dcf_results` 落库和 explanation block 输出。 |
| US-18 | 估值编排器与 blended value | 进行中 | 现已由行业模板驱动 4 个核心方法统一编排并写库；`FRED + Damodaran` 参数层已接到 `WACC / terminal growth / relative targets`，风险矩阵和 explanation 模板仍需继续细化。 |
| US-19 | 风险矩阵引擎 | 已完成 | 已把风险矩阵正式接到 `wacc / scenario_weight / margin_of_safety` 修正链路，并落 `risk_scores`；后续仍可继续扩展风险因子颗粒度。 |
| US-20 | 情景引擎与 Margin of Safety | 进行中 | `bear / base / bull` 已结构化落库，后续还需接更细的增长/利润率驱动。 |
| US-21 | Explanation Blocks | 进行中 | 已落成 12 个固定 blocks，并接入 `run/report` 输出与 `report_blocks`；仍需 Claude Code 继续打磨措辞和 PRD 对齐。 |
| US-22 | Profile / Data Quality / Financial Quality API | 进行中 | 三类接口均已接真实库表；仍需补更多 PRD 字段。 |
| US-23 | Valuation Run / Summary / Report API | 进行中 | `run` 已返回结构化 `summary / decision / explanation`，`summary/report` 可用并已完成 `AAPL / MSFT / NVDA` live 验证；`source attribution v2` 已返回 `peer_candidate_count / peer_selection_rule_version / peer_filter_summary / peer_filter_metrics / effective_target_multiple_sources`，完整 PRD 字段仍待继续细化。 |
| US-24 | 任务调度 | 待做 | 目前有手动 sync 入口，尚无完整 ingestion / normalization / valuation job orchestration。 |
| US-25 | 单测与契约测试 | 进行中 | 当前 `42` 个测试通过，已覆盖估值持久化最小链路与 peer set/source attribution 回归。 |
| US-26 | 文档回写与验收报告 | 进行中 | progress / task list / handoff 已建立，需持续维护。 |

## 4. 这一批新增的关键事实

1. `valuation_runs` 已开始真实写入。
2. `valuation_method_results` 现在会稳定产出至少这 4 行：
   - `dcf`
   - `reverse_dcf`
   - `relative_valuation`
   - `historical_multiple`
3. `DCF / FCFF` 已从旧启发式策略切换到 `sector_template_config + valuation_parameter_set` 驱动的可配置模型。
4. `Reverse DCF` 已从“单纯落表”升级为独立引擎，输出隐含增长、隐含利润率、解释 notes，并始终参与方法编排。
5. `scenario_results / reverse_dcf_results / risk_scores / report_blocks` 已随 `valuation run` 一并落库。
6. `Historical Multiple` 已明确依赖 `market_price_daily` 与 `market_snapshot`，当历史样本不足时会保留 `insufficient/sparse` 注释，而不是伪装成完整历史引擎。
7. `Longbridge` SDK 已接到 `MarketDataService`，并优先覆盖价格、总市值、PE/PB、股息率和 OHLCV 落库。
8. `FRED + Damodaran` 已接到 `UsExternalValuationParameterService`，并覆盖 `wacc.rf / wacc.erp / wacc.beta / wacc.base / terminal_growth.base / relative.target_pe / relative.target_ev_ebitda`。
9. `run` 与 `report` 现已输出三层结构：
   - `summary`
   - `decision`
   - `explanation`
10. `explanation` 现已固定输出 12 个 blocks，并带 `key / title / display_order`。
11. `Relative Valuation` 当前已接入 `peer_set_source / peer_selection_basis / peer_selection_breakdown / peer_set_tickers / industry_multiple_source / target_multiple_sources`。
12. peer set 当前规则：
   - 候选池放大后再筛选
   - 优先级：`industry -> sector_template -> company_type -> sector`
   - 严格过滤：市值区间、收入增长区间、FCF margin、ROIC、可用倍数
   - 无干净 peer 时回退 `template_only`
13. `source attribution` 当前已补齐：
   - `peer_candidate_count`
   - `peer_selection_rule_version`
   - `peer_filter_summary`
   - `peer_filter_metrics`
   - `effective_target_multiple_sources`
   - `source_attribution_version = v2`

## 5. 当前最顺的下一步

1. 继续把 `Relative Valuation peer set` 从“严格过滤”升级到“终版 peer universe”，重点补行业专属规则和 peer 准入白名单。
2. 继续补 `AAPL` 这类 case 的标准化财务覆盖，让 `financial_standardized / derived_metrics` 更完整，而不只是依赖 `SEC profile` 托底。
3. `US-22 / US-23`：继续把 `Summary / Decision / Explanation` 三层里的 source attribution、macro source、peer set 解释补齐到终版结构。
