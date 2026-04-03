# 全球股票估值系统_统一产品文档

更新日期：2026-04-03  
适用对象：产品、后端、数据工程、前端、Codex、Claude Code  
文档定位：全局单一产品基线（Global Product Baseline）+ 面向 Codex 的执行清单  
文档状态：增强版 / 用于继续完善当前仓库，而不是推翻重写

---

## 1. 文档目标

这份文档不是研究材料，而是全局产品与工程执行基线。

目标有四个：

1. 统一美国、中国、日本、香港四个市场的估值产品口径。
2. 明确哪些能力已经做了，哪些只是基础版，哪些还没做。
3. 把新增功能、补强功能、商业化功能统一纳入一个文档，避免需求散落。
4. 给 Codex / Claude Code 一个可直接执行的产品与工程清单。

一句话定义：

**这是一个覆盖 US / CN / JP / HK 的、可追溯、可解释、可 API 化、可商业化的全球股票估值能力平台，而不是一个只返回单点目标价的小工具。**

---

## 2. 文档来源与主次关系

当前以以下文档为准：

1. `全球股票估值系统_统一产品文档.md`
2. `美国股票估值.md`
3. `美股估值系统_完整终版方案_v2.docx`
4. `中国股票估值引擎_PRD_正式版.docx`
5. `us_equity_valuation_complete_final_plan.md`
6. `us_equity_valuation_api_openapi_spec.md`

主次关系：

1. **全球范围、功能矩阵、跨市场接口、平台能力、商业化路径**，以本文为准。
2. **美国市场深度规则、六层架构、输出块、风险矩阵**，以 `美国股票估值.md` 和 `美股估值系统_完整终版方案_v2.docx` 为准。
3. **中国市场的分型、路由、修正、红旗检查、三情景**，以 `中国股票估值引擎_PRD_正式版.docx` 为准。
4. 日本和香港当前仍处于基础版，应以本文的统一产品目标补齐。
5. 旧的全球 PRD、重复功能清单、临时草稿不再作为主文档。

---

## 3. 全局产品定义

### 3.1 核心输出对象

系统输出的核心对象不是单一 target price，而是：

1. `fair_value_low / fair_value_mid / fair_value_high`
2. `blended_intrinsic_value`
3. `margin_of_safety`
4. `verdict`
5. `scenario_matrix`
6. `risk_matrix`
7. `implied_expectation`
8. `data_quality_audit`
9. `financial_quality_scorecard`
10. `source_attribution`
11. `history`
12. `rankings / peers / screener`
13. `report_blocks`
14. `watchlist / alert_state`
15. `portfolio_valuation_summary`

### 3.2 产品必须支持的三层输出

所有市场最终都必须收口为三层：

1. **Summary**
   - 第一屏结论
   - 用于列表页、榜单页、筛选器、卡片视图
2. **Decision**
   - 真正的决策依据层
   - 包含 scenario、risk、implied expectation、quality、data audit
3. **Explanation**
   - 给详情页、报告页、API、导出 PDF / markdown 统一复用

### 3.3 全局非目标

当前不做：

1. 自动下单与交易执行
2. 高频撮合系统
3. 纯资讯门户
4. 没有来源追溯的拍脑袋 AI 估值
5. 没有数据质量标记的自动报告

---

## 4. 全局设计原则

### 4.1 方法原则

1. 必须至少 3 种估值方法交叉验证。
2. Reverse DCF 必须运行，不能省略。
3. 输出必须是区间、概率和解释，不是只给单点数值。
4. 不同行业和公司类型必须走不同路由，不能一套模板打天下。

### 4.2 数据原则

1. 关键财务字段必须可追溯到来源文档或来源快照。
2. 缺失关键数据时必须下调 `confidence_level`。
3. 禁止把 synthetic 数据伪装成真实生产输出。
4. 数据质量要显式进入 API、报告和 UI。

### 4.3 工程原则

1. 当前仓库继续沿用 `Java + Spring Boot + PostgreSQL + Flyway`。
2. Codex 要做的是**收敛当前代码到统一产品基线**，不是重写技术栈。
3. 规则、权重、模板、行业分型必须配置化，不得散落硬编码。
4. 解释块必须结构化，不得拼大段不可解析文本。

---

## 5. 当前代码架构梳理

### 5.1 后端主结构

1. `com.fairvalue.engine.api`
2. `com.fairvalue.engine.service`
3. `com.fairvalue.engine.us`
4. `com.fairvalue.engine.cn`
5. `com.fairvalue.engine.jp`
6. `com.fairvalue.engine.repository`
7. `src/main/resources/db/migration`
8. `src/main/resources/static`

### 5.2 当前 US 主链路

#### 计算链

- `POST /v1/us-equities/{ticker}/valuation/run`
- 估值方法计算
- 写入 `valuation_runs` 及子表
- `upsert valuation_latest_snapshot`
- 调度链路写入 `valuation_jobs`

#### 读取链

- `GET summary / report / rankings` 优先读库
- `history` 优先读 `market_price_daily + valuation_runs`
- `peers` 优先复用最近 run 和 latest snapshot
- `screener` 优先读 latest snapshot

### 5.3 当前定时刷新模型

1. 已接入 `@EnableScheduling`
2. 已新增 `UsScheduledValuationRefreshService`
3. 默认每 30 分钟刷新一次 `US security_master` 全量 universe
4. `scheduled / manual` 模式已区分
5. `valuation_jobs` 已有基础队列状态、失败重试、卡死恢复、告警摘要

---

## 6. 数据源策略

### 6.1 当前正式 / 半正式数据源矩阵

| 市场 | 当前主链路 | 状态 |
| --- | --- | --- |
| 美国 | SEC EDGAR + 公司 IR + Longbridge + FRED + Damodaran + SimFin(部分) | 最完整 |
| 中国 | Eastmoney + CN 规则引擎 | 可运行但未完全数据库化 |
| 日本 | 本地规则 + 通用快照 | 基础版 |
| 香港 | 通用快照 + 通用估值 | 基础版 |

### 6.2 各类源的职责

1. `SEC EDGAR`：财报真相层
2. `公司 IR`：guidance、presentation、管理层补充说明
3. `Longbridge`：正式价格层和市场快照层
4. `FRED`：risk-free rate、宏观参数
5. `Damodaran`：ERP、行业 beta、行业倍数
6. `SimFin`：标准化加速与交叉回查
7. `FINRA`：short interest、crowding、float 风险增强
8. `Eastmoney`：中国市场 live quote / discovery 主源
9. 日本、香港后续需要补正式数据源，不应长期停留在通用快照层

### 6.3 全局数据源目标状态

| 市场 | 目标正式数据层 |
| --- | --- |
| 美国 | SEC + IR + Longbridge + FRED + Damodaran + FINRA + SimFin |
| 中国 | Eastmoney / Tushare / AkShare(研究) + 公告/财报主源 + 中国规则引擎 |
| 日本 | EDINET / TDnet / 正式行情主源 + 日本专属规则引擎 |
| 香港 | HKEX 披露 + 正式行情主源 + 港股专属规则引擎 |

---

## 7. 全局能力地图（统一功能矩阵）

> 状态说明  
> `已实现`：已进入主链路或已有接口/页面/数据库支撑  
> `已实现待增强`：当前有基础版本，但功能深度不够  
> `未实现`：还没有正式产品链路  
> `规划新增`：本轮补进统一产品文档的新能力

### 7.1 估值核心引擎能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| 多方法估值 | DCF / Historical Multiple / Relative Valuation / Reverse DCF | US 已实现，其他市场未统一 | 当前主要完整落在 US | 保持 US 为基线，抽象全局方法接口 |
| 三情景 | Bear / Base / Bull 场景与权重 | US 已实现待增强，CN 已有规则，JP/HK 未统一 | 各市场输出结构还不一致 | 统一 DTO 和持久化结构 |
| 风险矩阵 | earnings / leverage / governance / regulation / FX / value trap | US 已实现待增强，CN 规则中有同类思路 | 还没有全市场统一风险枚举 | 提取全局 risk taxonomy |
| 数据质量审计 | completeness / freshness / source health / confidence | US 已实现，CN/JP/HK 未完成 | 需要成为全市场固定输出 | 为所有市场补 `data_quality_audit` |
| 解释块 | 12 个 explanation blocks | US 已实现，其他市场未统一 | JP/HK 仍偏基础文本输出 | 统一 report block schema |
| 方法追溯 | `assumptions_json` / `source_document_id` | US 部分已实现，非美不足 | 全局应变成硬要求 | 非美估值结果补追溯链路 |
| 行业专属模型 | Bank / Insurance / REIT / Biotech / SOTP / NAV / DDM / rNPV | US 未完整，CN 部分规则已有，JP/HK 未系统化 | 是全局准确率关键 | 做规则路由，不要硬编码塞 controller |

### 7.2 市场发现与榜单能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| Discovery | 按市场分页发现股票 | 已实现待增强 | 目前口径不完全一致 | 统一返回字段和分页结构 |
| Rankings | 最低估 / 最高估 | 已实现待增强 | 非美和全量 strict ranking 仍不足 | 统一 latest snapshot 排序引擎 |
| 扩展榜单 | 52 周高点 / 52 周低点 / 最活跃 / 涨幅排行 / 跌幅排行 | 规划新增 | 与合理股价站非常匹配 | 增加榜单类型枚举与查询层 |
| 质量榜单 | 财务稳健 / 现金流 / 成长 / 盈利能力 / 分红质量 | 规划新增 | 非常适合用户付费与筛选 | 基于评分表生成榜单接口 |
| 价值陷阱榜单 | 低估但质量差 / 风险高 | 规划新增 | 避免只按 upside 排序误导用户 | 新增 `value_trap_flag` 和榜单筛选 |
| 市场热度榜单 | 用户关注、自选股热度、最近触发提醒 | 规划新增 | 有利于留存和前端产品化 | 后台先做统计，不急着开放前端编辑 |

### 7.3 个股详情能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| Summary 卡片 | 当前价、公允价值、上涨空间、verdict、安全边际 | 已实现待增强 | 各市场字段和样式待统一 | 收口全局 summary schema |
| Decision 层 | 场景矩阵、风险矩阵、隐含预期、数据质量 | US 已实现，其他市场未统一 | 是详情页核心 | 所有市场都返回该层 |
| Explanation 层 | 12 blocks 报告输出 | US 已实现，其他市场未完成 | 必须结构化 | 前后端共享 block schema |
| 估值拆解 | 各方法输出、权重、使用理由 | US 基础版，其他市场不足 | 用户最需要解释性 | 新增 valuation breakdown 细化字段 |
| 历史估值 | fair value 历史、估值区间变化 | US 已实现待增强 | 仍缺按交易日重放 | 历史表和回放任务补齐 |
| 同行对比 | peers 列表、同行估值对比、质量对比 | US 已实现待增强，CN/JP/HK 未正式 | 真正的同行集还不稳定 | 做 per-market peer engine |
| 敏感性分析 | WACC / 增长率 / 终值倍数敏感性 | 规划新增 | 是高质量估值产品必备 | 放入 scenario / method result 子对象 |
| Reverse DCF 详情 | 当前价格隐含增长与利润率 | US 已实现待增强 | 应单独展示给用户 | 新增详情卡片与 report block |

### 7.4 筛选器与机会发现能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| Screener | 估值筛选 | 已实现待增强 | 目前更偏基础筛选 | 扩展多维条件 |
| 高级筛选 | 低估幅度、质量评分、盈利能力、股息、流动性、成长 | 规划新增 | 用户最愿意付费的功能之一 | 先做后端筛选 DSL / DTO |
| 风险过滤 | 排除高杠杆、弱现金流、治理红旗 | 规划新增 | 避免低估陷阱 | 与 risk matrix 联动 |
| 行业专属筛选 | 银行看 PB-ROE、REIT 看 AFFO、多成长股看 PS / Rule of 40 | 规划新增 | 提高专业度 | 配置化 market + sector filter templates |
| 事件叠加筛选 | 财报前后、分红、回购、并购、强赎、指数调整 | 规划新增 | 有利于商业化与差异化 | 先建 event schema，再接数据源 |

### 7.5 用户功能与持续留存能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| Watchlist | 自选股 | 未实现 | 关键留存功能 | 建表 + API + 前端入口 |
| Alerts | 价格 / fair value / verdict 变化提醒 | 未实现 | 关键付费能力 | 先做后端规则 + scheduler |
| Webhook | 估值变化推送到外部系统 | 未实现 | 面向高级用户和 B 端 | 与 alerts 共用事件模型 |
| Portfolio | 自选组合估值概览、低估比例、风险暴露 | 规划新增 | 有助于长期订阅 | P1 起步，先组合只读 |
| Saved Screens | 保存筛选器 | 规划新增 | 高级用户强需求 | 与用户系统打通后做 |
| Notes / Tags | 股票备注、自定义标签 | 规划新增 | 有助于工作流化 | 放到 P2 |

### 7.6 平台与商业化能力

| 模块 | 功能 | 状态 | 备注 | Codex 动作 |
| --- | --- | --- | --- | --- |
| API key | 鉴权 | 已实现待增强 | 缺正式 key 管理和后台 | 做 key 生命周期管理 |
| Rate limit | 限流 | 已实现待增强 | 缺套餐与配额体系 | 与 entitlement 统一 |
| Entitlement | 权限检查 | 已实现待增强 | 缺正式商品与套餐模型 | 做 plan / feature matrix |
| Usage metering | 用量统计 | 已实现待增强 | 缺对账、计费、后台 | 增加 billing-ready usage ledger |
| Billing | 支付 / 账单 / 套餐 | 未实现 | 商业化必备 | 平台层单独模块 |
| Tenant / White label | 多租户 / 白标 | 未实现 | 企业版能力 | 不要放 P0 |
| Admin Console | 用户、key、套餐、作业、用量、源状态后台 | 基础 US 后台已实现待增强 | 还不是正式平台后台 | 拆分运营后台和风控后台 |

---

## 8. 当前已实现功能（增强版梳理）

### 8.1 全局已实现

1. 通用估值接口：`GET /v1/valuation/{market}/{symbol}`
2. discovery：`GET /v1/discovery/{market}`
3. rankings：`GET /v1/rankings/{market}/undervalued`、`GET /v1/rankings/{market}/overvalued`
4. peers：`GET /v1/peers/{market}/{symbol}`
5. batch：`POST /v1/valuation/batch`
6. explain：`GET /v1/valuation/{market}/{symbol}/explain`
7. scenario：`POST /v1/valuation/scenario`
8. history：`GET /v1/valuation/history/{market}/{symbol}`
9. screener：`POST /v1/screener/valuation`
10. 多市场统一 `ValuationService`

### 8.2 美国市场已实现

1. `security_master / security_identifier_map`
2. `source_documents / source_document_facts_raw`
3. SEC universe、submissions、companyfacts 同步
4. 公司 IR 元数据同步
5. `financial_standardized`
6. `financial_derived_metrics`
7. `financial_quality_scores`
8. `data_quality_audit`
9. `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`
10. `Longbridge / FRED / Damodaran / SimFin` 客户端与 `source-status`
11. 美国专属接口：
   - `/v1/us-equities/{ticker}/profile`
   - `/v1/us-equities/{ticker}/data-quality`
   - `/v1/us-equities/{ticker}/financial-quality`
   - `/v1/us-equities/{ticker}/valuation/run`
   - `/v1/us-equities/{ticker}/valuation/summary`
   - `/v1/us-equities/{ticker}/valuation/report`
12. 美国后台接口：
   - `/v1/us-equities-admin/{ticker}/overview`
   - `/v1/us-equities-admin/{ticker}/source-status`
   - `universe / sec / ir / market / standardize sync`
13. 四个核心估值方法已落库：
   - `dcf`
   - `historical_multiple`
   - `relative_valuation`
   - `reverse_dcf`
14. `summary / decision / explanation` 三层输出已存在
15. explanation 固定 `12` 个 blocks
16. risk matrix 已进入估值修正链路
17. source attribution v2 已进入主链路
18. `US peers GET` 已改为只读，不再写新的 `valuation_runs`
19. `valuation_latest_snapshot` 已落地
20. `summary / report / rankings` 已支持优先读库
21. `history` 已优先读真实持久化历史
22. `screener` 已优先读 latest snapshot，缺失时走轻量估值
23. 已接 30 分钟 `US security_master` universe 定时刷新骨架
24. `valuation_jobs` 已有后台统计与 ticker 维度查询
25. `valuation_jobs` 已支持后台失败重试与卡死恢复
26. `valuation_jobs` 已进入队列化基础阶段
27. `valuation-alerts` 已可用
28. `US peers` 当前已具备行业规则只读 peer universe 与 `snapshot_fallback`
29. 平台层已进入主运行态：统一 envelope、API key、rate limit、entitlement、usage metering、`/platform/bootstrap`、`/v1/platform/me`、`/v1/platform/usage`
30. `US peers verdict` 已统一收口为稳定枚举：`UNDERVALUED / OVERVALUED / FAIR`

### 8.3 中国市场已实现

1. 中国专属 controller
2. `valuation/run`、`summary`、`report`
3. `discovery`
4. `rules/version`
5. Eastmoney live quote
6. Eastmoney universe discovery
7. 中国规则文件和行业路由逻辑

### 8.4 日本市场已实现

1. 日本专属 controller
2. `overview`
3. `fair-value`
4. `history`
5. `screener`
6. `recalc`
7. `events`

### 8.5 前端与后台页面已实现

1. 全球榜单页基础版
2. 美国详情页基础版
3. 美国后台管理页
4. 全球筛选页基础版
5. 市场切换、榜单渲染、详情 tab、风险/方法/历史展示
6. 美国后台页已支持：
   - source status
   - valuation alerts
   - platform usage snapshot
   - valuation jobs summary
   - ticker recent jobs
   - global recent jobs
   - retry failed jobs
   - recover stale jobs

---

## 9. 已实现功能的补强要求（这部分是给 Codex 的）

### 9.1 通用接口层补强

1. 把所有市场逐步收口到统一 envelope：`success / request_id / timestamp / data / error / meta`。
2. 所有 summary / report / screener / rankings 返回固定分页与排序字段。
3. 所有 market controller 都必须返回统一的 `market`、`symbol`、`currency`、`as_of`、`data_version`。
4. 非美市场不能继续只返回松散 JSON，必须逐步对齐 US 的 summary / decision / explanation 三层。

### 9.2 已有估值能力补强

1. `history` 目前虽然已优先读真实持久化历史，但还缺按交易日重放 fair value。
2. `scenario` 目前还偏接口能力，不是完整的情景引擎产品模块。
3. `peers` 当前已有基础能力，但同行集稳定性和行业专属规则不足。
4. `screener` 当前有基础接口，但还缺高级筛选、排序、保存和组合视图。
5. `report` 当前 US 已较完整，但 CN/JP/HK 还没真正产品化。

### 9.3 已有后台能力补强

1. 当前 US 后台是工程后台，不是正式运营后台。
2. 必须补权限分层、危险操作二次确认、操作审计日志。
3. `valuation_jobs` 当前具备基础运行和恢复能力，但还缺完整 worker pool、priority policy、熔断和 backlog 平衡。
4. source status 页面要能区分 `healthy / degraded / stale / disabled`。

---

## 10. 当前未实现或未完成的功能（统一补完版）

### 10.1 全局未完成

1. 全平台单一 API 契约收口
2. 正式 API key 管理、用户绑定、后台控制台
3. 套餐、配额、超额、client lifecycle 管理
4. 多租户 / 白标能力
5. 用户系统、付费系统、账单系统
6. watchlist / alert / webhook 正式产品链路
7. 真正的全市场持久化 ranking engine
8. 统一的全市场 latest snapshot 体系
9. 统一的 portfolio / watchlist / saved screen 数据模型
10. 全市场统一的 report blocks schema 和渲染层
11. 前端正式产品页而不是基础 demo 页
12. 导出能力：markdown / PDF / CSV / webhook payload

### 10.2 美国市场未完成

1. `valuation_jobs` 正式 worker pool / priority / alerting / circuit breaker / backlog rebalancing
2. `FINRA` 正式接入风险主链路
3. `SimFin derived / income / cashflow` 真正进入估值主链路
4. 更完整的行业专属模型：Bank / Insurance / REIT / Biotech / SOTP
5. 更完整的 peer universe 与行业专属 peer rules
6. `history` 按交易日重放估值
7. `US rankings` 做到全量 latest snapshot 覆盖 + 全市场严格排序
8. 平台层 client lifecycle / quota / billing / 控制台完成
9. 更完整的 OpenAPI 契约对齐和商业字段
10. `US peers` 真实同行集稳定化，减少 `template_only`
11. `US portfolio valuation` 与 `watchlist alerts`
12. `US advanced screener`，支持质量、成长、风险、事件多条件组合

### 10.3 中国市场未完成

1. 标准化财报落库主链路
2. 类似美股的 `source_documents -> raw facts -> standardized` 数据库链路
3. 中国专属 `profile / data-quality / financial-quality`
4. 中国市场完整 `latest_snapshot + schedule` 框架
5. 中国专属 peers engine
6. 全市场持久化 rankings
7. 中国市场红旗检查结果结构化输出
8. 中国市场 WACC 与风险溢价分解输出
9. 中国市场政策 / 流动性 / 治理 / AH / VIE 修正落库
10. 中国市场行业模板：消费、制造、资源、银行、券商、保险、医药、互联网平台、地产链
11. 港股 / 中概股与 A 股的规则分层
12. 批量估值和选股池排序

### 10.4 日本市场未完成

1. 正式日本市场数据源接入
2. 正式日本财报标准化与数据库链路
3. 日本专属同行比较与榜单引擎
4. 日本市场产品级 explanation / report
5. 日本市场 latest snapshot + schedule 框架
6. 日本市场股息、回购、资本效率、PBR 改善路线等特色模块
7. 日本市场 `events + valuation` 联动
8. 日本市场金融股 / 商社 / 高股息股专属模板

### 10.5 香港市场未完成

1. 独立 HK controller
2. 正式港股实时/历史行情主源
3. 正式港股财务标准化链路
4. 独立港股估值模型与流动性折价引擎
5. 港股专属 rankings / peers / screener
6. 港股 latest snapshot + schedule 框架
7. 港股 `Southbound / Northbound / ADR / 同股不同地` 价差与结构修正
8. 港股红筹、平台股、地产、金融、资源股分型模板

### 10.6 本轮新增补进主文档、但当前还没做的产品功能

1. 榜单扩展：52 周高点、52 周低点、最活跃、涨幅榜、跌幅榜
2. 质量榜单：财务稳健、现金流质量、成长质量、盈利质量、股息质量
3. 价值陷阱榜单：低估但质量差 / 风险高
4. 个股敏感性分析矩阵
5. 组合估值页：watchlist / portfolio summary
6. 估值提醒：价格到买点、fair value 上调、verdict 改变、财报后重算
7. Saved screens / Saved models
8. 导出报告：markdown / PDF / share link
9. 用户备注、标签、自定义买入区间
10. 市场事件层与估值联动
11. 商业化功能页：套餐差异、额度展示、升级入口

---

## 11. 新增页面与前端模块规划

### 11.1 全局榜单页

应支持：

1. 市场切换：US / CN / JP / HK
2. 榜单切换：
   - 最被低估
   - 最被高估
   - 52 周高点
   - 52 周低点
   - 最活跃
   - 涨幅排行
   - 跌幅排行
3. 列字段：
   - 名称 / 代码
   - 当前价
   - 公允价值
   - upside / downside
   - verdict
   - 财务稳健
   - 现金流质量
   - 成长质量
   - 盈利能力
   - 分红质量
   - 风险等级
4. 排序与筛选
5. 登录后支持加入 watchlist / 设置提醒

### 11.2 个股详情页

详情页必须统一为以下 tab：

1. 总览
2. 估值拆解
3. 情景矩阵
4. 风险矩阵
5. 数据质量
6. 财务质量
7. 同行对比
8. 历史估值
9. 事件与催化剂
10. 报告导出

### 11.3 全球筛选页

1. 低估幅度
2. 安全边际
3. 市值
4. 流动性
5. 质量评分
6. 成长评分
7. 盈利评分
8. 股息评分
9. 风险等级
10. 行业 / 国家 / 交易所
11. 是否金融股 / 周期股 / 高股息股 / 亏损成长股
12. 是否排除 value trap

### 11.4 用户页

1. watchlist
2. alerts
3. portfolio
4. saved screens
5. api usage
6. billing
7. export center

---

## 12. 全局 API 与输出结构要求

### 12.1 所有市场都应逐步提供的标准接口

1. `GET /v1/{market}-equities/{symbol}/profile`
2. `GET /v1/{market}-equities/{symbol}/data-quality`
3. `GET /v1/{market}-equities/{symbol}/financial-quality`
4. `POST /v1/{market}-equities/{symbol}/valuation/run`
5. `GET /v1/{market}-equities/{symbol}/valuation/summary`
6. `GET /v1/{market}-equities/{symbol}/valuation/report`
7. `GET /v1/{market}-equities/{symbol}/valuation/history`
8. `GET /v1/{market}-equities/{symbol}/peers`
9. `POST /v1/screener/valuation`
10. `GET /v1/rankings/{market}/{ranking_type}`
11. `POST /v1/watchlist`
12. `POST /v1/alerts`
13. `GET /v1/portfolio/summary`

### 12.2 Summary 必须返回

1. `verdict`
2. `fair_value_range`
3. `blended_intrinsic_value`
4. `upside_downside`
5. `confidence_level`
6. `margin_of_safety`
7. `buy_zone / hold_zone / avoid_zone`
8. `quality_summary`
9. `risk_summary`
10. `as_of / data_version`

### 12.3 Decision 必须返回

1. `scenario_matrix`
2. `risk_matrix`
3. `implied_expectation`
4. `data_quality_audit`
5. `value_trap_flag`
6. `source_attribution`
7. `method_coverage`
8. `assumption_version`

### 12.4 Explanation 必须返回固定 blocks

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

---

## 13. 数据库与任务系统补充要求

### 13.1 全局建议核心表

1. `security_master`
2. `security_identifier_map`
3. `source_documents`
4. `source_document_facts_raw`
5. `financial_standardized`
6. `financial_derived_metrics`
7. `financial_quality_scores`
8. `data_quality_audit`
9. `market_snapshot`
10. `market_price_daily`
11. `valuation_runs`
12. `valuation_method_results`
13. `scenario_results`
14. `reverse_dcf_results`
15. `risk_scores`
16. `report_blocks`
17. `valuation_latest_snapshot`
18. `valuation_jobs`
19. `watchlists`
20. `watchlist_items`
21. `alert_rules`
22. `alert_events`
23. `saved_screens`
24. `portfolio_positions`
25. `usage_ledger`
26. `api_clients`
27. `billing_accounts`

### 13.2 任务系统目标状态

1. 定时任务与手动任务统一进入 `valuation_jobs`
2. 支持 `queued / claimed / running / succeeded / failed / stale / cancelled`
3. 支持 `priority / available_at / worker_id / attempt_count / last_error`
4. 支持失败重试、熔断、限流、重平衡
5. 支持按市场 / ticker / 功能类型派发
6. 为 alerts、history replay、snapshot refresh 共用任务基础设施

---

## 14. 给 Codex 的明确执行备注

### 14.1 允许做的事

1. 新增数据库表、DTO、service、controller、配置类、定时任务。
2. 将行业模板、WACC、方法权重、风险枚举、评分规则配置化。
3. 为非美市场补 `summary / decision / explanation` 三层输出。
4. 为估值方法补 `assumptions_json / sensitivity_json / source_document_id`。
5. 为 watchlist / alerts / webhook / portfolio 建基础数据模型和 API。
6. 为 rankings / screener / peers 补 latest snapshot 驱动模式。
7. 为后台补权限、审计、危险操作确认。

### 14.2 禁止做的事

1. 禁止推翻 `Java + Spring Boot + PostgreSQL` 当前基线。
2. 禁止为了“快速可见效果”先大量拼页面而不补数据和估值主链路。
3. 禁止把 synthetic 数据伪装成生产数据。
4. 禁止因为缺数就返回高置信度估值。
5. 禁止把 explanation 文案散落硬编码在多个 service。
6. 禁止把行业模板、风险权重、WACC 参数写死成多个常量副本。
7. 禁止删掉来源追溯字段、数据质量字段、风险矩阵字段。

### 14.3 Codex 实施顺序建议

#### Phase A：先补主链路

1. US strict ranking engine
2. US peer universe 稳定化
3. valuation_jobs 正式 worker 化
4. 全局 API 契约收口
5. watchlist / alerts 基础表和 API

#### Phase B：再补产品层

1. 全球榜单扩展
2. 高级筛选器
3. 历史估值重放
4. 详情页 Explanation 统一 block schema
5. 后台权限与审计

#### Phase C：再补非美主链路

1. 中国市场标准化落库
2. 中国市场 data-quality / financial-quality / peers / rankings
3. 日本市场正式数据源 + 标准化链路
4. 香港市场独立 controller + 主数据源 + peers / rankings

#### Phase D：最后补商业化

1. plan / feature / quota / billing
2. API client lifecycle
3. usage ledger 对账
4. tenant / white label

### 14.4 每个任务都要附带的验收口径

1. 是否有稳定 API 输出
2. 是否有数据库追溯
3. 是否有数据质量标记
4. 是否有测试覆盖
5. 是否有后台可观测性
6. 是否不会破坏现有 US 主链路

---

## 15. 研发优先级（更新版）

### P0

1. 美国正式 peer universe 收口
2. `valuation_jobs` worker pool / priority / alerting 收口
3. `US rankings` 全量 strict ranking engine
4. 全局 API 契约统一收口
5. watchlist / alerts / webhook 基础能力
6. 平台层 client lifecycle / quota / billing 基础收口
7. CN 标准化财报落库主链路起步

### P1

1. 历史估值按交易日重放
2. 高级筛选器
3. 行业专属模型扩展
4. CN / JP / HK 正式 peers 引擎
5. 组合估值页与 portfolio summary
6. 前端正式详情页与榜单页增强
7. Admin Console 正式化

### P2

1. 日本正式数据源与标准化链路
2. 香港独立 controller 与正式主数据源
3. Saved screens / saved models
4. PDF / markdown / share link 导出
5. 事件层与估值联动
6. 多租户 / 白标
7. 客户后台与用量统计

### P3

1. 企业版 API 管理
2. 团队协作工作流
3. 机构版 portfolio / research workspace
4. 深度商业化运营后台

---

## 16. 验收标准（全局版）

### 16.1 功能验收

1. 任一市场输入一个股票代码，都能返回统一结构的 summary。
2. 至少 US / CN 要能返回结构化 report blocks。
3. 排行榜不能只返回 page-scoped 伪全市场结果。
4. watchlist / alerts 至少能跑通基础闭环。
5. screener 至少支持估值 + 质量 + 风险三类条件。
6. peers 至少返回稳定同行集和基本估值对比。

### 16.2 质量验收

1. 缺关键数据时，不允许返回高置信度 fair value。
2. 非金融股与金融股不得混用错误模板。
3. 周期股不得直接用高点利润给出乐观估值。
4. 亏损成长股不得强行套传统 P/E。
5. 所有市场的 API 都必须带 `as_of / confidence / source / quality`。
6. 任一估值 run 必须能追溯到方法、参数、来源、风险修正。

---

## 17. 当前结论

一句话总结当前项目状态：

**美国股票估值主链路已经进入“可定时刷新、可落库、可读库、可继续产品化”的阶段；中国市场已有专属规则和 API 雏形，但数据库主链路仍不足；日本市场已有接口和基础估值能力，但还未进入正式数据产品阶段；香港市场仍主要处于通用层覆盖；整个全球产品下一步不是再写散乱文档，而是按本文件把“统一契约、统一输出、统一排名、统一用户能力、统一商业化能力”逐步补齐。**

## 18. 给 Codex 的一句话任务定义

**在不推翻当前 Java 技术栈的前提下，以 US 成熟链路为基线，先补统一 API / ranking / peers / jobs / watchlist / alerts，再向 CN / JP / HK 复制“数据源 -> 标准化 -> 多模型估值 -> 风险矩阵 -> Summary/Decision/Explanation”的完整能力。**
