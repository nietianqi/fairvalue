# 美股估值系统_完整终版方案

## 1. 项目定义

### 1.1 项目名称
US Equity Valuation System  
美股合理股价估值系统

### 1.2 一句话定义
构建一个面向美股市场的估值基础设施，基于 **SEC 原始财报 + 正式价格层 + 宏观参数层 + 多模型估值引擎 + 风险修正 + 解释输出**，持续输出 **fair value 区间、概率加权内在价值、风险标签、置信度评分、Reverse DCF 隐含预期和结构化解释结果**，并可同时服务于网站前端、筛选器、自选监控和对外 API。

### 1.3 系统定位
本系统不定义为“股票合理价格计算器”，而定义为 **可解释、可追溯、可商业化的美股估值中台**。它面向两类场景：

- 对内：自有网站、内容团队、选股器、榜单、自选提醒
- 对外：开发者 API、财经网站、量化工具、研究系统接入

系统最终交付的不是单一价格，而是：
- 合理价值区间
- 模型拆解
- 风险与情景
- 置信度评分
- 数据版本与来源追溯

---

## 2. 目标与非目标

### 2.1 项目目标
1. 建立美股统一估值引擎，支持单股、批量、历史、情景、解释和筛选能力。
2. 输出统一结构化 JSON，供前端、数据库、报告和第三方系统复用。
3. 每只股票至少运行 3 种估值方法，目标 4–5 种，且 Reverse DCF 必跑。
4. 所有标准化财务结果可追溯到 `source_document_id`，所有模型保留 `assumptions_json`。
5. 关键数据缺失时自动降低 `confidence_level`，不允许伪造高置信度。

### 2.2 非目标
1. 不做自动下单与交易执行。
2. 不做券商级全功能行情终端。
3. 不做宏观预测引擎。
4. 不输出“唯一正确价格”，只输出区间、情景和解释。
5. 当前阶段不追求全球市场一次性全覆盖，先把美股做深做稳。

---

## 3. 目标用户

### 3.1 To C 用户
- 个人投资者
- 网站会员用户
- 需要快速判断低估/高估状态的用户
- 需要行业对比、历史估值和情景分析的研究用户

### 3.2 To B 用户
- 小型财经网站
- 量化选股工具开发者
- 投研内容团队
- 自媒体研究团队
- 企业内部研究系统

### 3.3 用户核心诉求
- 不想自己接 SEC、价格源、宏观源和分析师预期源
- 不想自己做财报口径标准化
- 不想自己为不同类型公司写不同模型
- 需要可解释、可展示、可调用、可变现的估值结果

---

## 4. 核心设计原则

1. **SEC 是财报真相层**，任何聚合源都不能替代。
2. **数据层与估值层解耦**，未来可替换供应商。
3. **不输出单点迷信值**，必须输出区间、风险、情景、置信度。
4. **结果必须可解释、可追溯、可版本化**。
5. **不同公司类型不能硬套统一模板**。
6. **先做能卖的能力，不先堆花哨页面**。

---

## 5. 总体架构

系统采用六层架构：

### L1 原始事实层
负责接入并落库：
- SEC EDGAR 10-K / 10-Q / 8-K / DEF 14A / Form 4 / XBRL facts
- 公司 IR 文档
- Investor Day、Guidance、Management Commentary 元数据

作用：形成可追溯证据底座。

### L2 标准化财务层
负责：
- 财报字段标准化
- TTM / FY / Q 生成
- GAAP / Non-GAAP 调节
- SBC 调整
- 稀释股本处理
- 净债务/净现金计算
- 派生指标生成

作用：形成全系统复用的统一财务 schema。

### L3 市场与宏观层
负责：
- 长桥价格、K线、快照、市值、PE/PB、shares
- FRED 无风险利率与宏观序列
- Damodaran ERP、行业 beta、行业倍数
- FINRA short interest / float 风险增强

作用：为 WACC、相对估值和风险矩阵提供输入。

### L4 估值计算层
负责：
- DCF / FCFF
- Historical Multiple
- Relative Valuation
- Reverse DCF
- 特定行业/公司类型专用模型

作用：并行运行多模型，输出基础估值结果。

### L5 风险修正层
负责：
- risk matrix
- 情景权重调整
- WACC 调整
- value_trap_flag
- confidence_level 生成

作用：把“算出来的价值”变成“可决策的价值”。

### L6 解释输出层
负责：
- Summary / Decision / Explanation 三层结构
- 报告型 blocks
- summary / report 两种视图
- `/valuation/run` 的结构化返回

作用：服务前端、报告导出和商业 API。

---

## 6. 数据源终版方案

### 6.1 生产版数据源
最终推荐组合：
- **SEC EDGAR**：财报真相层
- **公司 IR**：指引与经营假设层
- **长桥 API**：正式价格层
- **FRED**：无风险利率与宏观层
- **Damodaran**：ERP、行业 beta、行业倍数参数层

可选增强：
- FINRA
- SimFin

### 6.2 研究版数据源
用于内部研究、联调和备份：
- SEC EDGAR
- 公司 IR
- FRED
- FINRA
- SimFin
- yfinance / Stooq

但必须明确：
**SimFin、yfinance、Stooq 不能作为正式对外产品的财报真相层或主价格层。**

### 6.3 字段级主来源
- Revenue / EBITDA / CFO / CapEx / FCF / Debt / Equity / SBC / Diluted Shares → SEC EDGAR
- Guidance / Segment KPI / Investor Day 假设 → 公司 IR
- Current Price / OHLCV / Market Cap / Vendor PE/PB / Shares → 长桥 API
- Risk-Free Rate → FRED
- ERP / 行业 Beta / 行业倍数 → Damodaran
- Short Interest / Float 风险 → FINRA

原则是：**官方源优先，聚合源补充，原始层和清洗层分离存储。**

---

## 7. 统一数据 Schema

### 7.1 基础字段
- market
- symbol
- exchange
- cik
- company_name
- currency
- fiscal_year
- fiscal_period
- filing_date
- source_document_id
- data_version

### 7.2 财务字段
- revenue_ttm
- gross_profit_ttm
- operating_income_ttm
- ebit_ttm
- ebitda_ttm
- net_income_ttm
- free_cash_flow_ttm
- capex_ttm
- cash
- total_debt
- net_cash
- book_value
- shares_outstanding_basic
- shares_outstanding_diluted
- roe
- roic
- dividend_yield
- buyback_yield
- sbc_expense

### 7.3 补充字段
- segment_data
- analyst_estimates
- management_guidance
- event_flags
- last_financial_update_time
- last_price_update_time

Schema 必须分三层保存：
- raw facts
- standardized facts
- derived metrics

---

## 8. 估值方法体系

### 8.1 Always-On 四大核心方法
所有公司至少跑以下方法中的 3 个，正常目标 4 个：

#### 1) DCF / FCFF
- 主模型
- 默认权重 30%–40%
- 适用于成熟现金流型公司
- 10 年显性预测 + 终值
- 对增长、利润率、WACC 最敏感

#### 2) Historical Multiple
- 默认权重 15%–25%
- 用于判断当前估值相对自身历史区间是贵还是便宜

#### 3) Relative Valuation
- 默认权重 20%–30%
- 用行业中位数和精选 peer set 交叉验证

#### 4) Reverse DCF
- 所有公司必须运行
- 不直接进入 blended value
- 用于反推当前股价隐含的增长、利润率、FCF 预期

### 8.2 按公司类型自动切主方法
- Profitable Growth / Compounder：DCF + Historical Multiple + Relative Valuation + Reverse DCF
- Hyper-growth SaaS：EV/NTM Revenue + EV/Gross Profit + Rule of 40 + Reverse DCF
- Cyclical：Mid-cycle EV/EBITDA + Historical Range + Reverse DCF
- Bank / Insurance：P/TBV + ROE-COE + DDM
- REIT：AFFO Multiple + NAV + DDM
- Biotech pre-revenue：rNPV + Cash Runway + Comparable M&A
- Conglomerate / HoldCo：SOTP / NAV + Look-through Valuation

### 8.3 通用硬约束
- 始终使用 diluted shares
- SBC 必须计入真实成本
- 必须做 net cash / net debt 调整
- 金融、REIT、Biotech 不能套工业股模板
- 关键数据缺失时，降低复杂模型权重，提高 Historical / Relative 权重

---

## 9. WACC、情景与风险系统

### 9.1 WACC 参数来源
- Risk-Free Rate：FRED，按日刷新
- Equity Risk Premium：Damodaran，年更
- Beta：行业 beta + 自算 beta 并存
- Size Premium：按市值区间规则化配置
- Specific Risk Premium：风险引擎按 run 决定
- Cost of Debt / Tax Rate：来自 SEC 标准化财务

### 9.2 三情景框架
默认情景权重：
- Bear 25%
- Base 55%
- Bull 20%

但必须支持动态调整，依据：
- 当前估值位置
- 资产负债表压力
- 盈利可见度
- 催化剂时点

### 9.3 风险矩阵
至少覆盖：
- earnings miss
- multiple compression
- balance sheet stress
- competitive disruption
- regulatory / legal
- macro / recession
- governance
- dilution
- commodity / input cost
- FX / geopolitics
- value trap

规则：
- 1 个 High/High 风险，上调 WACC 0.5%–1.0%
- 多个 High/High 风险，上调 1.0%–2.0%，压低 Bull 权重
- 二元事件优先进入情景概率，而不是简单堆到 WACC
- 同时出现低估值、弱治理、弱现金流、差资本配置时，输出 `value_trap_flag=true`

### 9.4 置信度评分
评分维度：
- 财报完整性
- 数据新鲜度
- 盈利稳定性
- 现金流稳定性
- 行业可比性
- 模型一致性
- 流动性
- 主观假设依赖度

置信度既是展示字段，也是模型权重和风险提示的输入。

---

## 10. 输出规范

### 10.1 三层输出结构

#### Summary
用于首页卡片和筛选器：
- fair_value_low
- fair_value_mid
- fair_value_high
- blended_intrinsic_value
- margin_of_safety
- verdict
- confidence_level
- buy_zone / hold_zone / avoid_zone

#### Decision
用于真正的决策层：
- scenario_matrix
- risk_matrix
- implied_expectation
- data_quality_audit
- value_trap_flag

#### Explanation
用于详情页、报告和 explain API：
- one_line_verdict
- executive_summary
- valuation_breakdown
- financial_quality_scorecard
- uncertainty_sources

### 10.2 Explanation 固定 blocks
- One-line Verdict
- Executive Summary
- Data Quality Audit
- Business & Moat
- Financial Quality Scorecard
- Growth & Catalysts
- Risk Matrix
- Valuation Breakdown
- Scenario Matrix
- Final Fair Value
- Actionable Framework
- Uncertainty & Error Sources

### 10.3 输出形式
所有结果必须以 **结构化 JSON** 输出，不使用自由文本拼接。产品版同时支持：
- summary 视图
- report 视图
- `/valuation/run` 结构化返回

---

## 11. API 设计

首期建议实现以下接口：

1. `GET /v1/valuation/US/{symbol}`  
   返回单股估值主结果。

2. `POST /v1/valuation/batch`  
   返回批量估值结果，用于榜单、自选监控和机构批处理。

3. `GET /v1/valuation/US/{symbol}/explain`  
   返回模型适用理由、主要驱动因子、风险说明和置信度下降原因。

4. `POST /v1/valuation/scenario`  
   输入 Revenue Growth、Margin、WACC、Terminal Growth 等参数，输出悲观/中性/乐观估值区间和敏感度分析。

5. `GET /v1/valuation/history/US/{symbol}`  
   返回价格 vs fair value 历史序列。

6. `GET /v1/peers/US/{symbol}`  
   返回同行公司与相对估值数据。

7. `POST /v1/screener/valuation`  
   用于低估榜、高估榜、多条件筛选。

---

## 12. 工程落地方案

### 12.1 技术栈
- 后端：Python + FastAPI + Pydantic
- 主库：PostgreSQL
- 缓存：Redis
- 对象存储：SEC 原文、IR PDF/HTML 和中间解析文件

### 12.2 建议目录
- ingestion/
- normalization/
- classification/
- quality_engine/
- valuation_engine/
- risk_engine/
- report_engine/
- api/

### 12.3 核心表
- security_master
- security_identifier_map
- source_documents
- source_document_facts_raw
- financial_standardized
- financial_derived_metrics
- market_snapshot
- market_price_daily
- data_quality_audit
- valuation_runs
- valuation_method_results
- scenario_results
- reverse_dcf_results
- risk_scores
- report_blocks

核心链路必须做到：
`source_documents -> raw facts -> standardized financials -> derived metrics -> valuation_runs -> report_blocks`

---

## 13. 产品功能结构

一级模块建议为：
1. 数据接入中心
2. 财务清洗与标准化中心
3. 估值模型引擎
4. 市场修正引擎
5. API 服务中心
6. 前端展示中心
7. 筛选与监控中心
8. 商业化与权限中心

其中首批必须上线的，不是所有模块，而是：
- 单股估值
- 批量估值
- explain
- history
- screener 基础版
- 自选提醒基础版

---

## 14. 商业化方案

### 14.1 To C
**免费版**
- 基础估值结论
- 每日少量查询

**Pro 版**
- 单股详细估值
- 历史估值
- explain
- 风险矩阵
- 自选提醒

**Premium 版**
- 批量估值
- 高级筛选
- 情景分析
- 数据导出

### 14.2 To B
**Developer**
- 基础估值接口
- API Key
- 调用限额
- 不允许大规模再分发

**Startup**
- batch / history / screener / explain
- 可商用展示

**Business / Enterprise**
- 高调用量
- SLA
- Webhook
- 白标
- 再分发许可

### 14.3 核心售卖逻辑
卖点不是基础股票数据，而是：
- 清洗后的统一财务
- 可解释估值能力
- 可直接嵌入前端的结构化结果
- 可直接用于筛选和监控的 API

---

## 15. 分阶段实施路线

### Phase 1 / MVP
目标：跑通单股估值。  
范围：
- SEC + 长桥 + FRED + Damodaran
- DCF / Historical / Relative / Reverse DCF
- summary API
- 单股详情页最小版

### Phase 2 / 完整版
目标：跑通标准化、风险和报告。  
加入：
- 公司 IR
- quality score
- risk matrix
- scenario engine
- report blocks
- 批量筛选

### Phase 3 / 产品版
目标：对外服务化。  
加入：
- 前端展示
- 定时任务
- 告警
- 用户 API
- Universe 管理
- 商业权限与稳定性增强

---

## 16. 最终推荐与执行优先级

最优路径不是继续堆更多免费价格源，而是：

1. **用 SEC 锚定财报真相层**
2. **用长桥一次性确定正式价格层**
3. **用 FRED + Damodaran 统一 WACC 参数层**
4. **先建标准化财务层和追溯链路**
5. **再做多方法估值、风险修正和解释层**
6. **最后再扩展页面、筛选、监控和商业化能力**

一句话总结：

**这个系统的正确终局不是“算一个目标价”，而是建立一套基于 SEC 真相层、长桥价格层、FRED/Damodaran 参数层、多模型估值引擎、风险矩阵和结构化解释输出的美股估值基础设施。**
