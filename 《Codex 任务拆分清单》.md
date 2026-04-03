# Codex 任务拆分清单

更新日期：2026-04-03  
适用对象：Codex、Claude Code、后端研发、数据工程、前端研发  
适用项目：全球股票估值系统（US / CN / JP / HK）

---

## 1. 文档目的

这份清单不是新的 PRD，而是把《全球股票估值系统_统一产品文档》增强版进一步拆成 **可以直接派给 Codex 执行的工程任务清单**。

目标：

1. 把“产品能力”拆成“可开发任务”。
2. 把“优先级”拆成“阶段和依赖”。
3. 把“要做什么”拆成“改哪里、加什么、验收什么”。
4. 避免 Codex 重写全项目，而是沿着当前 Java 主链路持续补齐。

一句话：

**Codex 的任务不是重做一个新系统，而是在当前仓库基础上，把全球股票估值系统补成真正可运行、可追溯、可筛选、可告警、可商业化的产品。**

---

## 2. 执行总规则

### 2.1 必须遵守

1. 保持当前 `Java + Spring Boot + PostgreSQL + Flyway` 技术栈。
2. 保持 `US` 链路为当前主基线，不要推翻现有估值表和 API。
3. 所有新增估值结果都必须可追溯到：
   - `source_document_id`
   - `assumptions_json`
   - `data_version / as_of`
4. 缺关键数据时必须降低 `confidence_level`，不得返回伪高质量估值。
5. 解释层必须结构化，不允许把大段解释文案散落在多个 service 中硬编码。
6. 行业模板、WACC、权重、风控阈值、评分规则，必须配置化。
7. 每个任务都要尽量做到：
   - 有 migration
   - 有 DTO
   - 有 service
   - 有 API
   - 有最小测试
   - 有后台或日志可观测性

### 2.2 明确禁止

1. 禁止重写技术栈。
2. 禁止只做前端假数据页面。
3. 禁止用 synthetic 数据伪装生产输出。
4. 禁止把 explanation 文案硬塞到 controller。
5. 禁止新增一堆重复常量类导致参数分叉。
6. 禁止做大而全重构，影响当前 `US valuation` 主链路稳定性。

---

## 3. 任务优先级和阶段

### Phase A：先补主链路（P0）

目标：先把当前最影响可用性和可信度的能力补齐。

1. US strict ranking engine
2. US peer universe 稳定化
3. valuation_jobs 正式 worker 化
4. 全局 API 契约收口
5. watchlist / alerts / webhook 基础能力
6. 平台层 client lifecycle / quota 基础收口
7. CN 标准化落库主链路启动

### Phase B：再补产品层（P1）

目标：把“能算”升级成“能用、能筛、能看、能留存”。

1. 全球扩展榜单
2. 高级筛选器
3. 历史估值重放
4. Explanation block 统一
5. Admin Console 正式化
6. 组合估值只读版

### Phase C：再补非美市场主链路（P1/P2）

目标：把 US 的成熟模式复制到 CN / JP / HK。

1. CN 数据标准化、质量评分、榜单、同行、报告
2. JP 正式数据源与标准化链路
3. HK 独立 controller + 正式主数据源 + 排名 / 同行

### Phase D：最后补商业化（P2/P3）

目标：从工具走向平台。

1. API client lifecycle
2. 套餐 / 配额 / entitlement
3. 用量账本
4. billing
5. tenant / white label

---

## 4. 任务拆分总表（适合复制给 Codex）

> 字段说明  
> `ID`：任务编号  
> `优先级`：P0 / P1 / P2 / P3  
> `类型`：backend / data / api / frontend / ops / platform  
> `依赖`：前置任务  
> `产出`：必须落地的代码/表/API/页面  

| ID | 任务 | 优先级 | 类型 | 依赖 | 核心产出 |
| --- | --- | --- | --- | --- | --- |
| GBL-001 | 全局 API envelope 收口 | P0 | backend/api | 无 | 统一 `success/request_id/timestamp/data/error` |
| US-001 | US strict ranking engine | P0 | backend/data | 无 | 全量 `valuation_latest_snapshot` 排序引擎 |
| US-002 | US peer universe 稳定化 | P0 | backend/data | 无 | 稳定同行集和 fallback 规则 |
| US-003 | valuation_jobs worker pool 化 | P0 | backend/ops | 无 | 可 claim / retry / stale recover 的任务执行器 |
| GBL-002 | Summary / Decision / Explanation 统一 DTO | P0 | backend/api | GBL-001 | 所有市场统一输出对象 |
| USER-001 | Watchlist 基础能力 | P0 | backend/api/frontend | GBL-001 | 表结构 + CRUD API + 基础前端入口 |
| USER-002 | Alerts 基础能力 | P0 | backend/api/ops | USER-001 | 规则表 + 事件表 + scheduler |
| USER-003 | Webhook 基础能力 | P0 | backend/api | USER-002 | alert event 推送外部 endpoint |
| PLATFORM-001 | API client lifecycle 基础版 | P0 | platform/backend | GBL-001 | client / key / status / rotate / revoke |
| PLATFORM-002 | Quota / entitlement 基础版 | P0 | platform/backend | PLATFORM-001 | 计划、配额、能力开关 |
| CN-001 | CN 财务标准化落库主链路 | P0 | data/backend | 无 | 标准化表、落库任务、最小字段映射 |
| CN-002 | CN data_quality_audit | P0 | backend/data | CN-001 | 质量审计结果写库 + API |
| GBL-003 | report block schema 统一 | P1 | backend/frontend | GBL-002 | 全市场 block schema |
| GBL-004 | 扩展榜单 | P1 | backend/api/frontend | US-001 | 52 周高低、最活跃、涨跌榜 |
| GBL-005 | 高级筛选器 | P1 | backend/api/frontend | GBL-002 | 筛选 DSL / DTO / 查询实现 |
| GBL-006 | 历史估值重放 | P1 | backend/data | US-003 | 交易日级 fair value replay |
| GBL-007 | 质量榜单 / 价值陷阱榜单 | P1 | backend/api | GBL-005 | 榜单接口 + 价值陷阱过滤 |
| USER-004 | Portfolio 只读版 | P1 | backend/api/frontend | USER-001 | 组合概览、低估比例、风险暴露 |
| ADMIN-001 | Admin Console 正式化 | P1 | backend/frontend | PLATFORM-001 | 运营后台和风控后台分拆 |
| CN-003 | CN peers 引擎 | P1 | backend/data | CN-001 | 同行业对比集合 |
| CN-004 | CN rankings / screener 正式化 | P1 | backend/api | CN-001,CN-002 | 估值榜单和筛选器 |
| CN-005 | 中国市场 report blocks | P1 | backend/api | CN-002,GBL-003 | 结构化报告输出 |
| JP-001 | JP 正式数据源接入 | P2 | data/backend | 无 | 日本主数据、日线、财务、分红 |
| JP-002 | JP 标准化与估值表 | P2 | data/backend | JP-001 | 标准化输入 + fair value 结果 |
| JP-003 | JP peers / rankings / report | P2 | backend/api | JP-002,GBL-003 | 日本正式榜单和详情能力 |
| HK-001 | HK 独立 controller 与主数据表 | P2 | backend/data/api | 无 | 港股独立入口和主数据层 |
| HK-002 | HK 标准化估值链路 | P2 | backend/data | HK-001 | 标准化 + quality + valuation |
| HK-003 | HK peers / rankings / report | P2 | backend/api | HK-002,GBL-003 | 港股正式榜单和详情页能力 |
| USER-005 | Saved Screens | P2 | backend/api/frontend | USER-001,GBL-005 | 保存筛选器 |
| USER-006 | Notes / Tags | P2 | backend/api/frontend | USER-001 | 股票备注和标签 |
| PLATFORM-003 | Usage ledger | P2 | platform/backend | PLATFORM-001,PLATFORM-002 | 可对账用量账本 |
| PLATFORM-004 | Billing 基础版 | P2 | platform/backend | PLATFORM-003 | 套餐、账单、订阅状态 |
| GBL-008 | 事件层与估值联动 | P2 | backend/data/api | GBL-006 | 财报、分红、回购、并购事件触发重算 |
| PLATFORM-005 | Tenant / White label | P3 | platform/backend | PLATFORM-004 | 多租户 / 白标 |
| PLATFORM-006 | 企业版 API 管理台 | P3 | platform/frontend/backend | PLATFORM-005 | key、配额、usage、webhook 管理 |

---

## 5. Phase A 详细拆分（P0）

## 5.1 GBL-001 全局 API envelope 收口

### 目标

把现有不同市场、不同 controller 的 API 输出收口为统一外层契约。

### Codex 要做

1. 新建统一响应对象：
   - `ApiEnvelope<T>`
   - `ApiErrorBody`
2. 全局异常处理：
   - 参数错误
   - 资源不存在
   - 数据缺失
   - 限流 / entitlement
   - 内部错误
3. 统一注入：
   - `request_id`
   - `timestamp`
   - `market`
   - `as_of`
4. 统一错误码枚举。
5. 不要破坏现有前端已依赖的关键字段，采用兼容式收口。

### 涉及模块

- `com.fairvalue.engine.api`
- 全局 exception handler
- 公共 DTO 模块

### 验收标准

1. US / CN / JP / HK 都返回统一 envelope。
2. 成功和失败响应结构一致。
3. 旧接口不出现大规模 break change。
4. 至少补 1 组 controller test。

---

## 5.2 US-001 US strict ranking engine

### 目标

把当前 `page-scoped ordering` 过渡态，升级为全量 `valuation_latest_snapshot` 驱动的真正全市场排序。

### Codex 要做

1. 梳理 `valuation_latest_snapshot` 覆盖率。
2. 新增 strict ranking 查询服务：
   - `undervalued`
   - `overvalued`
   - `quality`
   - `value_trap`
3. 排除无效数据：
   - snapshot 过旧
   - confidence 太低
   - 流动性不足
   - market cap / share 异常
4. 支持分页前先全量排序，不再页内排序。
5. 增加后台覆盖率指标：
   - snapshot coverage
   - stale ratio
   - market completeness

### 涉及表

- `security_master`
- `valuation_latest_snapshot`
- `data_quality_audit`

### 验收标准

1. 排行结果不再是 page-scoped。
2. 能清晰说明哪些股票被排除。
3. 后台可查看覆盖率。
4. 性能可接受，支持分页查询。

---

## 5.3 US-002 US peer universe 稳定化

### 目标

让 `US peers` 不再频繁退回 `template_only` 或空集合，而是形成稳定真实同行集。

### Codex 要做

1. 统一同行选择规则：
   - `industry`
   - `sector_template`
   - `company_type`
   - `market_cap bucket`
   - `revenue_growth bucket`
   - `fcf_margin / roic / liquidity`
2. 明确 fallback 层级：
   - exact industry
   - sector template
   - company type
   - broad fallback
3. 将 fallback 原因写入响应和日志。
4. 给 peers 增加稳定字段：
   - `peer_selection_mode`
   - `peer_quality_score`
   - `peer_data_completeness`
5. 补最小缓存，避免重复计算。

### 验收标准

1. 大部分主流股票返回稳定同行集。
2. 返回中显式说明 `selection_mode`。
3. 真实同行优先于模板兜底。
4. 至少补 3 组 peer selection 测试。

---

## 5.4 US-003 valuation_jobs worker pool 化

### 目标

把当前基础调度链路升级成正式任务系统，供 valuation、alerts、history replay 复用。

### Codex 要做

1. 补齐 `valuation_jobs` 状态机：
   - `queued`
   - `claimed`
   - `running`
   - `succeeded`
   - `failed`
   - `stale`
   - `cancelled`
2. 新增字段：
   - `priority`
   - `available_at`
   - `worker_id`
   - `attempt_count`
   - `last_error`
3. 实现 claim / heartbeat / retry / stale recover。
4. 支持按市场、ticker、job_type 分发。
5. 管理端可查看：
   - backlog
   - stale jobs
   - fail ratio
   - oldest queued job

### 验收标准

1. 手动和定时任务统一进入 jobs。
2. worker 可安全 claim。
3. stale 能自动恢复。
4. 有基本后台可视化或日志指标。

---

## 5.5 GBL-002 Summary / Decision / Explanation 统一 DTO

### 目标

所有市场都收口为统一三层输出。

### Codex 要做

1. 定义统一 DTO：
   - `ValuationSummaryDto`
   - `ValuationDecisionDto`
   - `ValuationExplanationDto`
2. 强制字段：
   - Summary：`verdict / fair_value_range / upside_downside / confidence / as_of`
   - Decision：`scenario_matrix / risk_matrix / implied_expectation / data_quality_audit / value_trap_flag`
   - Explanation：固定 12 blocks
3. US 先适配到新 DTO。
4. CN / JP / HK 允许先返回部分字段，但结构必须一致。

### 验收标准

1. 四市场接口结构一致。
2. 前端可以按统一 schema 渲染。
3. 字段缺失时是 `null/empty block`，不是乱结构。

---

## 5.6 USER-001 Watchlist 基础能力

### 目标

补最小用户留存功能。

### Codex 要做

1. 新表：
   - `watchlists`
   - `watchlist_items`
2. 基础 API：
   - 创建 watchlist
   - 删除 watchlist
   - 添加股票
   - 移除股票
   - 查询列表
3. 列表结果返回：
   - symbol
   - market
   - latest verdict
   - latest fair value
   - latest upside
   - latest alert state
4. 静态页先加最小入口即可。

### 验收标准

1. 后端 CRUD 可用。
2. watchlist item 能带最新 snapshot 信息。
3. 不依赖正式支付系统也能工作。

---

## 5.7 USER-002 Alerts 基础能力

### 目标

实现真正能转化付费的提醒系统基础版。

### Codex 要做

1. 新表：
   - `alert_rules`
   - `alert_events`
2. 支持规则：
   - price crosses threshold
   - verdict changes
   - fair value changes
   - upside enters range
3. scheduler 周期检查。
4. 事件落库去重：同一股票、同一规则、同一窗口不要重复刷。
5. 先支持站内 / 日志级触发，邮件可后置。

### 验收标准

1. 可以创建提醒规则。
2. 有事件落库。
3. 可查看最近触发记录。
4. 不会无限重复触发。

---

## 5.8 USER-003 Webhook 基础能力

### 目标

让 alert event 可以推给外部系统。

### Codex 要做

1. 新增 webhook endpoint 配置表或复用 alert_rule 字段。
2. 对 `alert_events` 做异步分发。
3. 记录 webhook 投递状态：
   - pending
   - delivered
   - failed
4. 最小重试逻辑。
5. 签名头 / 基础安全校验。

### 验收标准

1. 事件可推送到测试 endpoint。
2. 失败能重试。
3. 有简单投递日志。

---

## 5.9 PLATFORM-001 API client lifecycle 基础版

### 目标

把已进入主运行态的 API key / entitlement / usage 能力补成正式平台基础。

### Codex 要做

1. 新表：
   - `api_clients`
   - `api_client_keys`
2. 支持 key 状态：
   - active
   - revoked
   - expired
3. 支持 rotate / revoke。
4. 支持 client 级别 metadata。
5. 后台可查看 key 生命周期。

### 验收标准

1. key 生命周期可管理。
2. 老 key revoke 后不可访问。
3. rotate 不破坏 client 身份。

---

## 5.10 PLATFORM-002 Quota / entitlement 基础版

### 目标

把当前“有限流、有 entitlement”升级成真正有套餐与能力矩阵的模式。

### Codex 要做

1. 新表：
   - `plans`
   - `plan_features`
   - `client_plan_bindings`
2. 功能位最少覆盖：
   - market access
   - rankings access
   - screener access
   - alerts access
   - webhook access
   - export access
3. 配额最少覆盖：
   - requests/day
   - batch size
   - alerts count
   - watchlist items
4. entitlement 中间层统一读取 plan。

### 验收标准

1. 同一接口可按 plan 决定是否开放。
2. 配额可单独配置。
3. 后台可查看 client 当前 plan。

---

## 5.11 CN-001 CN 财务标准化落库主链路

### 目标

把中国市场从“规则可跑”升级到“数据标准化可落库”。

### Codex 要做

1. 建立 CN 标准化财务表，尽量与 US 同构：
   - `cn_security_master`
   - `cn_financial_standardized`
   - `cn_financial_derived_metrics`
2. 先覆盖最小字段：
   - revenue
   - net income
   - ex-non-recurring net income
   - EBITDA
   - cash
   - debt
   - equity
   - shares
   - CFO
   - FCF
3. 建立来源追溯字段。
4. 保留中国市场特殊字段入口：
   - pledge ratio
   - related party flags
   - subsidy adjustments
   - VIE / AH info

### 验收标准

1. 单只 CN 股票可落库标准化财务。
2. 能返回最小 summary。
3. 有来源字段，不是只留结果值。

---

## 5.12 CN-002 CN data_quality_audit

### 目标

让中国市场输出与 US 一样具备数据质量显式标记。

### Codex 要做

1. 定义 CN 质量审计规则：
   - completeness
   - freshness
   - source health
   - confidence
2. 识别中国市场特有缺口：
   - 扣非缺失
   - 现金流异常
   - 股本口径不稳定
   - 预期缺失
3. 写入审计表并进入 API。

### 验收标准

1. CN summary / report 都带 data quality。
2. 缺关键数据时 confidence 降低。

---

## 6. Phase B 详细拆分（P1）

## 6.1 GBL-003 report block schema 统一

### 目标

把 `US explanation blocks` 提炼成全市场通用 block schema。

### 固定 block

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

### 验收标准

1. US/CN 至少完全支持。
2. JP/HK 即使内容简化，也要同结构。

---

## 6.2 GBL-004 扩展榜单

### 目标

让首页和市场页从“只有低估/高估”升级到完整财经网站榜单能力。

### 先做榜单类型

1. `undervalued`
2. `overvalued`
3. `fifty_two_week_high`
4. `fifty_two_week_low`
5. `most_active`
6. `top_gainers`
7. `top_losers`
8. `financial_quality`
9. `cash_flow_quality`
10. `growth_quality`
11. `profitability_quality`
12. `value_trap`

### 验收标准

1. 有榜单枚举。
2. 每个榜单能分页。
3. 至少返回统一 summary 字段。

---

## 6.3 GBL-005 高级筛选器

### 目标

补真正能收费的筛选器。

### Codex 要做

1. 定义筛选 DSL / request DTO。
2. 支持条件：
   - 低估幅度
   - 安全边际
   - 市值
   - 成交额
   - 财务健康
   - 现金流质量
   - 成长
   - 盈利能力
   - 股息
   - 风险过滤
   - 行业专属条件
3. 查询层支持组合条件。
4. 结果返回可导出 schema。

### 验收标准

1. 至少支持估值 + 质量 + 风险三类条件。
2. 不对每只股票触发 live 重算。
3. 优先读 latest snapshot。

---

## 6.4 GBL-006 历史估值重放

### 目标

实现按交易日回看 fair value / verdict 变化。

### Codex 要做

1. 设计 replay job。
2. 按交易日重建历史 snapshot。
3. 区分：
   - `market price history`
   - `valuation output history`
4. 前端展示：
   - price vs fair value
   - verdict timeline
   - confidence timeline

### 验收标准

1. US 至少支持最近 1 年日级历史。
2. 不混淆“事后知道的财报”和当时可见数据。

---

## 6.5 GBL-007 质量榜单 / 价值陷阱榜单

### 目标

避免用户只按 upside 排序而踩坑。

### Codex 要做

1. 新增 `value_trap_flag` 规则。
2. 衍生榜单：
   - undervalued but weak quality
   - high upside but high risk
   - low valuation with weak cash flow
3. 结果中给出 trap reason。

### 验收标准

1. `value_trap` 可单独筛选。
2. 详情页显示 trap reason。

---

## 6.6 USER-004 Portfolio 只读版

### 目标

从单股工具升级为组合工具。

### Codex 要做

1. 新表：
   - `portfolio_positions`
2. 支持导入 / 手工录入持仓。
3. 组合返回：
   - total market value
   - weighted fair value gap
   - undervalued ratio
   - sector exposure
   - risk exposure
4. 不做交易流水，先做静态持仓只读版。

### 验收标准

1. 组合页可展示聚合 summary。
2. 每只持仓可关联 latest snapshot。

---

## 6.7 ADMIN-001 Admin Console 正式化

### 目标

把当前 US 后台升级成正式平台后台。

### Codex 要做

1. 拆分后台视图：
   - source status
   - jobs
   - alerts
   - api clients
   - usage
   - plans
2. 补危险操作确认：
   - retry failed jobs
   - recover stale jobs
   - resync source
   - rotate key
3. 增加后台审计日志。

### 验收标准

1. 后台不再只服务 US ticker。
2. 平台层操作有审计记录。

---

## 7. Phase C 详细拆分（非美主链路）

## 7.1 CN-003 中国市场 peers 引擎

### 目标

让中国市场不只是能算单股，还能返回可比公司。

### Codex 要做

1. 定义 CN 同行选择规则：
   - 申万 / 中信行业优先
   - 市值桶
   - 生命周期分型
   - A/H/中概 标识
2. 同行估值字段：
   - PE / PB / PS / EV/EBITDA
   - growth
   - ROE / ROIC
   - 股息率
3. 返回 `selection_mode`。

---

## 7.2 CN-004 中国市场 rankings / screener 正式化

### 目标

把 CN 的 discovery、rules、summary 进一步产品化。

### Codex 要做

1. CN latest snapshot 表。
2. CN 排名接口：
   - undervalued
   - overvalued
   - quality
   - value_trap
3. CN 筛选器复用全球 DSL。

---

## 7.3 CN-005 中国市场 report blocks

### 目标

输出真正结构化、可前端渲染的中国股票估值报告。

### 必须体现的中国特征

1. 扣非口径
2. 现金流质量
3. 政策修正
4. 治理与质押红旗
5. AH / VIE / 补贴 / 商誉等特殊说明

---

## 7.4 JP-001 / JP-002 / JP-003 日本市场正式化

### 目标

让日本市场从基础版迈向正式数据产品。

### Codex 要做

1. 接正式数据源和主数据表。
2. 标准化：
   - 财务摘要
   - 财务明细
   - 分红
   - 日线价格
3. 日本估值输出至少支持：
   - history percentile
   - PE/PB/EV/EBITDA
   - 简化 DCF
   - DDM / PB-ROE（适配金融/高分红）
4. 输出统一 block schema。

---

## 7.5 HK-001 / HK-002 / HK-003 港股正式化

### 目标

让港股不再长期依赖通用估值层。

### Codex 要做

1. HK 独立 controller。
2. HK 主数据和行情层。
3. HK 标准化财务表。
4. HK peers / rankings / screener / report。
5. 处理港股常见问题：
   - 汇率
   - AH 对照
   - 中资/本地股差异

---

## 8. Phase D 详细拆分（平台与商业化）

## 8.1 PLATFORM-003 Usage ledger

### 目标

做 billing-ready 的用量账本。

### Codex 要做

1. 记录每个 client 每次请求的：
   - endpoint
   - feature
   - market
   - units
   - billed_units
   - request_id
2. 支持按日汇总。
3. 支持后台查询。

---

## 8.2 PLATFORM-004 Billing 基础版

### 目标

形成最小可收费闭环。

### Codex 要做

1. 订阅状态模型。
2. plan 绑定。
3. invoice / billing account 最小表结构。
4. entitlement 与 billing 状态联动。

---

## 8.3 PLATFORM-005 Tenant / White label

### 目标

为 B 端和企业版预留能力。

### 注意

这个阶段不要插手 P0 主链路，放到最后。

---

## 9. 每个任务统一模板（Codex 提交时必须带）

以下模板要求 Codex 在每个任务 PR 或提交说明中遵守：

```md
## 任务编号
US-001

## 任务名称
US strict ranking engine

## 本次改动范围
- migration:
- entity/repository:
- service:
- controller:
- frontend:
- tests:

## 本次实现内容
1.
2.
3.

## 明确未做内容
1.
2.

## 风险点
1.
2.

## 验收方式
1.
2.
3.
```

---

## 10. 推荐给 Codex 的执行顺序

### Sprint 1

1. GBL-001
2. GBL-002
3. US-001
4. US-002

### Sprint 2

1. US-003
2. USER-001
3. USER-002
4. USER-003

### Sprint 3

1. PLATFORM-001
2. PLATFORM-002
3. CN-001
4. CN-002

### Sprint 4

1. GBL-003
2. GBL-004
3. GBL-005
4. GBL-006

### Sprint 5

1. GBL-007
2. USER-004
3. ADMIN-001
4. CN-003 / CN-004 / CN-005

### Sprint 6+

1. JP-001 / JP-002 / JP-003
2. HK-001 / HK-002 / HK-003
3. PLATFORM-003 / 004 / 005 / 006

---

## 11. 最终验收口径

### 11.1 系统层

1. 任一市场输入股票代码，都能返回统一 Summary 结构。
2. US / CN 至少能返回完整结构化 report blocks。
3. 排行榜不再是 page-scoped 假全市场排序。
4. Watchlist / Alerts 至少形成基础闭环。
5. 筛选器支持估值 + 质量 + 风险三类条件。
6. peers 返回稳定同行集，不再频繁空结果。

### 11.2 工程层

1. 所有新任务有 migration。
2. 所有新估值字段可追溯。
3. 所有新 API 有最小测试。
4. 所有关键后台动作有日志或审计。
5. 不破坏现有 US 主链路。

### 11.3 产品层

1. 首页可展示更多榜单。
2. 个股详情真正有 Summary / Decision / Explanation 三层。
3. 用户能保存关注股票并收到基础提醒。
4. 平台开始具备真正收费的基础能力。

---

## 12. 给 Codex 的一句话执行提示

**不要重写项目；以 US 主链路为基线，先补统一契约、严格排名、稳定 peers、正式 jobs、watchlist/alerts 和平台基础，再把同样的“数据源 -> 标准化 -> 估值 -> 风险 -> 报告”能力复制到 CN / JP / HK。**
