# US Equity Agent Task List

更新日期：2026-04-03  
当前分支：`codex/java-backend-foundation`  
上一稳定提交：`09b7504`  
当前状态说明：本文件反映仓库当前美股代码基线；`Longbridge` 已完成 `AAPL / MSFT / NVDA` 新一轮真实 live 验证，`FRED` 与 `SimFin` 现已通过本地安全配置完成 live 接入，`source-status` 已升级成四源统一安全 envelope，`US history` 也已改为优先读取真实持久化历史并返回 `valuation_run_date`；`rankings / peers` 的产品 API 已收口，`US peers GET` 误触发完整 `runValuation()` 的 P0 问题已修复；当前 `US discovery / rankings` 已切到 `security_master` 全量 universe 分页，最近一次真实 `SEC universe sync` 后 live `US active securities = 8072`，但 `US rankings` 仍处于 full-universe paged、page-scoped ordering 的过渡阶段；仓库中仍存在与 CN/JP/前端有关的未整理本地改动。  
终版对齐说明：任务优先级已按 [美股估值系统_完整终版方案_v2.docx](F:/fairvalue/美股估值系统_完整终版方案_v2.docx) 和 [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md) 重新理解。

## 1. 硬约束

1. 至少 3 种估值方法参与 blended value，`Reverse DCF` 必跑。
2. 所有估值方法都必须保留 `assumptions_json`。
3. 标准化财务结果必须可追溯到 `source_document_id`。
4. 禁止用 synthetic 数据伪装真实生产结果。
5. 所有密钥必须通过环境变量或 `application-secrets.properties` 注入，测试环境默认 `enabled=false`。

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
| US-09 | 市场数据层接入 | 进行中 | 已正式写入 `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`，`Longbridge` 主源 SDK、行情抓取和落表链路已接入并完成 `AAPL / MSFT / NVDA` live 验证；本地安全配置现已接入 `FRED / SimFin`，`source-status` 现已返回 `fred / longbridge / damodaran / simfin` 四源 envelope，但默认运行链路仍以 `Longbridge or Stooq + SEC enrich` 为核心。 |
| US-10 | 财务标准化管道 | 已完成 | `financial_standardized` 已支持 `FY / Q / TTM`。 |
| US-11 | 派生指标引擎 | 已完成 | `ROIC / FCF margin / accruals / leverage / book value per share` 已落库。 |
| US-12 | 数据质量审计引擎 | 已完成 | `data_quality_audit` 已正式落库，`data-quality` 接口优先读取审计表。 |
| US-13 | company_type / sector_template 选择器 | 已完成 | 已配置化并写回 `security_master`；`AAPL` 在当前真实规则下会落到 `compounder / us_tech_compounder`。 |
| US-14 | Relative Valuation | 进行中 | 已正式聚合为 `relative_valuation` 并落到 `valuation_method_results`；当前已升级为“行业优先 -> sector_template -> company_type -> sector”的分层 peer set，并叠加市值/收入增长/FCF margin/ROIC/可用倍数过滤；本轮新增 `peer_selection_mode / peer_quality_score / peer_data_completeness / peer_filter_summary`，但 live 仍常落到 `snapshot_fallback`。 |
| US-15 | Historical Multiple | 进行中 | 已接到 `market_price_daily + market_snapshot`，并写入 `valuation_method_results`；当前历史样本仍偏稀疏，属于 PRD 的最小落地版。 |
| US-16 | DCF / FCFF | 已完成 | 已升级成 `sector_template_config + valuation_parameter_set` 驱动的可配置 FCFF 模型，并支持 `custom_assumptions` 覆盖。 |
| US-17 | Reverse DCF | 已完成 | 已升级成独立可解释引擎，支持隐含增长求解、`reverse_dcf_results` 落库和 explanation block 输出。 |
| US-18 | 估值编排器与 blended value | 进行中 | 现已由行业模板驱动 4 个核心方法统一编排并写库；`FRED + Damodaran` 参数层已接到 `WACC / terminal growth / relative targets`，风险矩阵和 explanation 模板仍需继续细化。 |
| US-19 | 风险矩阵引擎 | 已完成 | 已把风险矩阵正式接到 `wacc / scenario_weight / margin_of_safety` 修正链路，并落 `risk_scores`；后续仍可继续扩展风险因子颗粒度。 |
| US-20 | 情景引擎与 Margin of Safety | 进行中 | `bear / base / bull` 已结构化落库，后续还需接更细的增长/利润率驱动。 |
| US-21 | Explanation Blocks | 进行中 | 已落成 12 个固定 blocks，并接入 `run/report` 输出与 `report_blocks`；仍需 Claude Code 继续打磨措辞和 PRD 对齐。 |
| US-22 | Profile / Data Quality / Financial Quality API | 进行中 | 三类接口均已接真实库表；仍需补更多 PRD 字段。 |
| US-23 | Valuation Run / Summary / Report API | 进行中 | `run` 已返回结构化 `summary / decision / explanation`，`summary/report` 可用并已完成 `AAPL / MSFT / NVDA` live 验证；`source attribution v2` 已返回 `peer_candidate_count / peer_selection_rule_version / peer_filter_summary / peer_filter_metrics / effective_target_multiple_sources`，本轮新增 `risk_free_rate_source = fred_api` 与 `erp_source` 非空三档回退；另已补 `GET /v1/rankings/{market}/*` 和 `GET /v1/peers/{market}/{symbol}` 的正式产品 API，但完整 PRD 字段仍待继续细化。 |
| US-24 | 任务调度 | 进行中 | 已切到 `US security_master` universe 调度，`app.valuation.us.schedule.max-symbols=0` 时代表按全量 universe enqueue；已有 `valuation_jobs` claim / retry / recover / alerts / ranking-coverage / dead-letter 查询；本轮新增 `priority / available_at / worker_id / backoff / dead_letter`，但仍缺真正 multi-worker pool 与更细的告警策略。 |
| US-25 | 单测与契约测试 | 进行中 | 最近一次成功测试记录为 `60` 个测试通过，已覆盖估值持久化最小链路、peer set/source attribution 回归、`source-status` 接口、真实 `US history`、`valuation_jobs` 调度回归，以及 `rankings / peers` 的产品 API 契约。 |
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
14. 本轮新增：
   - `FRED` 通过本地安全配置 live 接入
   - `SimFin` bulk-download 客户端与 `source-status` 管理接口
   - `source-status` 不再暴露 `simfin.dataset_url`
   - `US history` 优先读取 `market_price_daily + valuation_runs`，并返回 `valuation_run_date`
   - `erp_source` 现已禁止返回 `null`
15. `SimFin derived` 数据集客户端入口已新增，但当前 key 对该数据集返回 `Premium dataset selected`，后续接 peer set 前需确认订阅级别。
16. `GET /v1/peers/US/{symbol}` 已修复为只读路径：
   - 优先复用最新 `relative_valuation` assumptions
   - 无新鲜 run 时仅做 read-only peer discovery
   - 不再写入新的 `valuation_runs`
17. 全局 `rankings` 已改成使用 `MarketRankingItem`，不再把 `CnDiscoveryItem` 暴露给 `US` 榜单。
18. `rankings` 现已补齐：
   - `generated_at`
   - `data_as_of`
   - `ranking_basis`
   - `disclaimer`
19. `CN rankings` 当前明确标记为 `page-scoped-ranking`，避免误导成全市场完整排名。
20. `generic peers` 已移除跨行业 `PE distance` 排序，改为 `industry -> market_cap(if available) -> liquidity`，并将缺失数值字段改为 `nullable`，不再伪造 `0.0`。
21. `US discovery` 当前已改为 `security_master` 全量分页，当前 live `total = 8072`。
22. `US rankings` 当前也已改为 `security_master` 全量分页，但在 `valuation_latest_snapshot` 覆盖全 universe 前，仍是 `page-scoped ranking`。
23. 当前最近一次真实 `SEC universe sync` 返回：
   - `total_fetched = 10433`
   - `inserted = 8068`
   - `updated = 2365`
   - `failed = 0`

## 5. Session 8 新增任务（2026-04-03，给 Codex）

### S8-A（P0）— Bootstrap 鲁棒性 fix
**问题：** `GET /platform/bootstrap` 在 `APP_API_AUTH_VALID_KEYS` 未配置时抛 `NoSuchElementException`，导致前端筛选页初始化失败

**要求：**
- 无配置 client 时返回 `{ apiKey: “”, forceEnvelope: false }` 而非抛异常
- 或在 `application.properties` 增加 `APP_API_AUTH_VALID_KEYS=dev-local-key` 作为开发默认值

**涉及文件：** `PlatformController.java`，可能还有 `ApiClientRepository`

---

### S8-B（P1）— JP controller URL 规范化
**问题：** `JpEquityController` 当前路径 `/api/jp/stocks/{code}` 与 US/CN 的 `/v1/*/equities/{ticker}` 命名约定不一致，前端 JP 详情页无法按统一模式实现

**要求：**
- 将 `JpEquityController` base path 改为 `/v1/jp-equities`
- 新路径：
  - `GET /v1/jp-equities/{code}/overview`
  - `GET /v1/jp-equities/{code}/fair-value`
  - `GET /v1/jp-equities/{code}/history?days=180`
  - `GET /v1/jp-equities/{code}/events`
  - `POST /v1/jp-equities/screener`
  - `POST /v1/jp-equities/recalc`
- 旧路径 `/api/jp/stocks/*` 保留 302 redirect，兼容过渡期

**涉及文件：** `JpEquityController.java`

---

### S8-C（P1）— history HistoryPoint 补 run_id
**现状：** `HistoryPoint` 已有 `valuation_run_date`，但没有 `run_id`，前端 tooltip 无法展示具体哪次 run 的估值

**要求：**
- `HistoryPoint.java` 新增 `String runId`（允许 null）
- `ValuationRunsRepository` 对应查询补充 `run_id` 字段
- US history 接口响应中 `points[].run_id` 有值（non-null）

**涉及文件：** `HistoryPoint.java`、`ValuationRunsRepository.java`、`ValuationService.java`

---

### S8-D（P1）— US peers 种子数据
**现状：** US peers 接口因无同行业估值快照而返回空或只有 `snapshot_fallback`

**要求：**
- 对 AAPL / MSFT / NVDA / GOOGL / META / AMD / AVGO / QCOM / ADBE / MRVL 各触发一次 `POST /v1/us-equities/{ticker}/valuation/run`
- 确保 `valuation_latest_snapshot` 有上述 10 条数据
- 验证：`GET /v1/peers/US/AAPL?limit=5` 返回 ≥ 3 条真实 peer（非 snapshot_fallback）

---

### S8-E（P1）— Damodaran ERP 静态 fallback
**现状：** `damodaran.status = error`，page scrape 返回空，导致 source-status 页面显示异常

**要求（二选一）：**
- 修复 Damodaran 页面抓取逻辑
- 或在 `application.properties` / `application-secrets.properties` 中配置静态 ERP 值（例如 `app.damodaran.fallback-erp=0.0472`），让 `configured_template:damodaran_ref` 返回该静态值，`damodaran.status` 从 `error` 改为 `ok`（或 `configured`）

---

### S8-F（P2）— CN/JP history 真实数据标注
**现状：** CN/JP history 返回 sine wave 合成数据，无标注

**要求：**
- 至少在 `HistoryResponse` 或各 point 上增加 `”simulated”: true` 字段，让前端能区分
- CN：若 `market_price_daily` 有真实数据，优先使用；否则标注 simulated
- JP：`JpHistoryResponse.JpHistoryPoint` 增加 `boolean simulated` 字段

---

## 6. Session 9 Codex 任务（当前优先执行）

> **背景：** Session 8 前端重构已完成（screener 重构、CN 详情页、CN 榜单链接）。当前最大产品空洞是 `valuation_latest_snapshot` 为空 → rankings 是 page-scoped → screener/peers 无真实数据。本批任务目标：把空壳子填为真实数据。

### Session 9 当前进度（2026-04-03 晚）

1. `S9-A` 已完成：
   - `/platform/bootstrap` 无 public client 时不再抛异常
   - 返回空 `api_key` 和 `force_envelope = false`
2. `S9-B` 已完成最小验收：
   - Top 50 backfill 脚本已执行
   - 当前 live `snapshot_count = 923`
   - 当前 live `rankable_count = 497`
   - `GET /v1/peers/US/AAPL?limit=5` 已返回 `5` 条真实同行
   - 说明：部分指定 ticker 当前不在 `security_master` 中，返回 `Ticker ... does not exist in security master.`
3. `S9-C` 已完成：
   - `market-data.us.damodaran.fallback-erp=0.0472`
   - live 当前 `damodaran.status = warning`
   - `erp_source = damodaran_static_fallback`
4. `S9-D` 已完成：
   - JP canonical path 已切到 `/v1/jp-equities`
   - legacy `GET /api/jp/stocks/*` 已返回 `302`
   - legacy `POST` 仍保留兼容映射
5. `S9-E` 已完成：
   - `HistoryPoint` 已新增 `run_id`
   - `GET /v1/valuation/history/US/AAPL?days=10` live 已返回 `run_id`
6. `S9-F` 字段确认已完成，见本节对应说明。
7. `S9-B` 配套能力已补齐：
   - `POST /v1/us-equities/{ticker}/valuation/run` 允许空 body，使用默认参数
   - `POST /v1/us-equities-admin/snapshot-backfill/top50` 可直接执行 Top 50 snapshot 回填
8. 本地基础设施补强：
   - embedded PostgreSQL 现在会优先复用本地已存在的 `5432/fairvalue`
   - 避免测试与 `spring-boot:run` 因重复 `initdb` 失败

### S9-A（P0）— Bootstrap 端点健壮性

**问题：** `GET /platform/bootstrap` 调用 `apiClientRepository.findPublicClient().orElseThrow(...)` ，无 public client 时抛 `NoSuchElementException`。

**涉及文件：**
- `src/main/java/com/fairvalue/engine/api/PlatformController.java`
- `src/main/java/com/fairvalue/engine/platform/ApiPlatformService.java`（`bootstrap()` 方法）

**要求：**
- 若无 enabled public client → 返回 `{ "apiKey": "", "forceEnvelope": false }` 而不是抛异常
- 加单元测试：无 client 时返回空 key 对象

**验证：** `GET /platform/bootstrap` → 200（无 api_clients 记录时也正常）

---

### S9-B（P0）— Top 50 US 股票估值快照（数据任务）

**问题：** `valuation_latest_snapshot` 为空 → rankings page-scoped → screener/peers 无真实数据。

**方式：** 对以下 50 只 ticker 各调用一次 `POST /v1/us-equities/{ticker}/valuation/run`

**科技（10）：** AAPL, MSFT, NVDA, GOOGL, META, AMZN, AMD, AVGO, QCOM, ADBE

**各行业代表（40）：**
- 金融：JPM, BAC, GS, WFC, MS
- 医疗：JNJ, UNH, LLY, ABBV, PFE
- 消费：WMT, COST, MCD, NKE, HD
- 工业：CAT, DE, HON, RTX, GE
- 能源：XOM, CVX, COP, SLB, EOG
- 公用事业：NEE, DUK, SO, AEP, PCG
- 材料：LIN, APD, SHW, NEM, FCX
- 地产：PLD, AMT, EQIX, SPG, O

**注意：** 若外部 API（Longbridge/FRED）不通，需确保 `valuation/run` 对外部调用 graceful fallback（不 throw），否则所有 run 失败。

**验收：**
- `GET /v1/us-equities-admin/ranking-coverage` → `snapshot_count >= 50`
- `GET /v1/peers/US/AAPL?limit=5` → 返回 ≥ 3 个真实 peer

---

### S9-C（P1）— Damodaran ERP 静态 Fallback

**问题：** Damodaran page scrape 不稳定 → `erp_source = error` → source-status 显示红色告警 → DCF 参数不可信。

**要求：**
- `application.properties` 加：`market-data.us.damodaran.fallback-erp=0.0472`
- 抓取失败时使用 fallback，`erp_source` 改为 `"damodaran_static_fallback"`
- source-status 对 `damodaran_static_fallback` 显示 warning 而不是 error

**验证：** `GET /v1/us-equities-admin/AAPL/source-status` → `damodaran.status != "error"`

---

### S9-D（P1）— JP Controller URL 规范化

**问题：** `JpEquityController` base path `/api/jp/stocks` 与 US/CN（`/v1/*-equities`）不一致，阻塞 JP 详情页开发。

**涉及文件：** `src/main/java/com/fairvalue/engine/api/JpEquityController.java`

**路径映射：**

| 旧路径 | 新路径 |
|--------|--------|
| `GET /api/jp/stocks/{code}/overview` | `GET /v1/jp-equities/{code}/overview` |
| `GET /api/jp/stocks/{code}/fair-value` | `GET /v1/jp-equities/{code}/fair-value` |
| `GET /api/jp/stocks/{code}/history` | `GET /v1/jp-equities/{code}/history` |
| `GET /api/jp/stocks/{code}/events` | `GET /v1/jp-equities/{code}/events` |
| `POST /api/jp/stocks/screener` | `POST /v1/jp-equities/screener` |
| `POST /api/jp/stocks/recalc` | `POST /v1/jp-equities/recalc` |

- 旧路径加 302 redirect handler（兼容过渡期）
- 更新所有相关测试

**同时请确认 `JpFairValueResponse` 是否含以下字段（给 Claude Code 建 JP 详情页用）：**
- `methods[]`（含 `methodName/methodValue/weight/keyAssumption`）
- `scenarios[]`（含 `scenario/probability/fairValueLow/Mid/High`）
- `riskFlags[]`（含 `factor/level/description`）
- `catalysts[]`（含 `type/description/timeframe`）

若无，请补充并回复字段清单。

**验证：** `GET /v1/jp-equities/7203/overview` → 200

---

### S9-E（P1）— HistoryPoint 补 run_id 字段

**涉及文件：**
- `src/main/java/com/fairvalue/engine/valuation/HistoryPoint.java`
- `src/main/java/com/fairvalue/engine/service/ValuationService.java`（`historyFromPersistedUsData()`）
- `src/main/java/com/fairvalue/engine/repository/ValuationRunsRepository.java`

**要求：**
- `HistoryPoint` 新增 `String runId`（可为 null），序列化为 `run_id`
- `ValuationRunsRepository` 的 history 查询包含 `run_id` 列
- Service 层提取并填充 `runId`

**验证：** `GET /v1/valuation/history/US/AAPL?days=90` → `points[0].run_id` 非 null

---

### S9-F（P1）— CN DTO 字段确认与修复

**背景：** 前端 `cn-stock-detail.js` 期望以下字段，请 Codex 确认或补充：

**`CnValuationSummaryResponse` 期望字段（snake_case）：**

| 字段 | Java 字段 | 状态 |
|------|-----------|------|
| `current_price` | `double currentPrice` | 请确认 |
| `fair_value_mid` | `double fairValueMid` | 请确认 |
| `fair_value_low` | `double fairValueLow` | 请确认 |
| `fair_value_high` | `double fairValueHigh` | 请确认 |
| `upside_downside` | `double upsideDownside` | 请确认 |
| `valuation_verdict` | `String valuationVerdict` | 请确认 |
| `confidence_score` | `int confidenceScore` 0-100 | 已知 |
| `one_liner` | `String oneLiner`（可 null） | 请确认 |

**`sections` Map key 格式（关键）：**
Jackson SNAKE_CASE 不转换 Map key（只转 bean 字段名）。
请确认实际 key 是 camelCase 还是 snake_case，或统一改为 snake_case。

**`CnValuationReportResponse.riskMatrix[]` 期望字段：** `factor`, `level`, `adjustment`(double), `note`

**`MarketPeerItem` 期望字段：** `ticker`, `companyName`, `currentPrice`, `fairValueMid`, `upsideDownside`, `pe`, `evEbitda`, `marketCap`

**输出：** 请 Codex 回复字段确认表格，Claude Code 据此决定是否改前端。

**当前确认结果：**

| 对象 | 字段/规则 | 确认结果 |
| --- | --- | --- |
| `CnValuationSummaryResponse` | `current_price` | 存在 |
| `CnValuationSummaryResponse` | `fair_value_mid` | 存在 |
| `CnValuationSummaryResponse` | `fair_value_low` | 存在 |
| `CnValuationSummaryResponse` | `fair_value_high` | 存在 |
| `CnValuationSummaryResponse` | `upside_downside` | 不存在；当前字段名为 `upside` |
| `CnValuationSummaryResponse` | `valuation_verdict` | 不存在；当前字段名为 `verdict` |
| `CnValuationSummaryResponse` | `confidence_score` | 存在 |
| `CnValuationSummaryResponse` | `one_liner` | 不存在 |
| `CnValuationSummaryResponse` | 总字段数 | 当前为 9 个，不是 8 个 |
| `CnValuationReportResponse.sections` | Map key 风格 | 当前实际写入为 `snake_case` 风格自定义 key，例如 `company_and_industry_positioning` |
| `CnValuationReportResponse.riskMatrix[]` | `factor/level/adjustment/note` | 当前未完全对齐，仍需前后端进一步收口 |
| `MarketPeerItem` | `currentPrice/fairValueMid/upsideDownside` | 当前实际字段为 `currentPrice/fairValue/upside` |

---

### S9-G（P2）— CN/JP History 合成数据标注

**要求：**
- `JpHistoryPoint` 和 CN history point 新增 `boolean simulated`（默认 false）
- 生成 sine-wave 合成数据时设 `simulated = true`

**验证：** `GET /v1/valuation/history/CN/000001.SZ?days=90` → `points[0].simulated = true`（无真实数据时）

---

## 7. 当前最顺的下一步（总览）

**Codex 端（后端）：**
1. `S9-A`（P0）：bootstrap 健壮性
2. `S9-B`（P0）：Top 50 估值快照（数据任务）
3. `S9-C`（P1）：Damodaran fallback
4. `S9-D`（P1）：JP URL 规范化 + DTO 确认
5. `S9-E`（P1）：HistoryPoint run_id
6. `S9-F`（P1）：CN DTO 字段确认
7. `US-14`：Relative Valuation peer set 行业专属规则
8. `US-24`：valuation_jobs worker pool 升级

**Claude Code 端（前端）：**
1. `CC-A`（P1）：`jp-stock-detail.html` + `.js`（等 S9-D 完成）
2. `CC-C`（P1）：JP ticker 链接收口（等 CC-A 完成）
3. `CC-US-ADMIN`（P1）：复核 `US admin` 新增的 `Top 50 Backfill / Top 100 Queue / 严格榜单覆盖率` 交互与文案

---

## 8. Session 10 补充（US strict ranking 覆盖率推进）

### S10-A（P1）— Universe 批量快照回填入口

**已完成：**
- `POST /v1/us-equities-admin/snapshot-backfill/universe`
- 支持参数：
  - `page`
  - `size`
  - `mode=run|queue`
  - `priority`

**用途：**
- 给 `valuation_latest_snapshot` 批量补覆盖率
- 加快 `strict_ready` 从 `false` 向 `true` 推进

**当前建议调用：**
- `POST /v1/us-equities-admin/snapshot-backfill/universe?page=1&size=100&mode=queue&priority=220`

### S10-B（P1）— US Admin 覆盖率可视化

**已完成：**
- `us-equities-admin.html` 新增：
  - `Top 50 Backfill`
  - `Top 100 Queue`
  - `严格榜单覆盖率`

**Claude Code Review 重点：**
- 覆盖率卡片文案是否需要更强提示
- queue 模式是否需要二次确认
- `strict_ready=false` 时是否需要更明确的产品提示
