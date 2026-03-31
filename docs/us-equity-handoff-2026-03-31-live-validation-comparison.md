# US Equity Handoff - 2026-03-31 - Live Validation Comparison

任务批次：`US-09 + US-14 + US-23` Longbridge live 验证、Relative Valuation peer set、source attribution 终版字段补强  
当前分支：`codex/java-backend-foundation`  
上一稳定提交：`6de2770`  
当前状态：本轮已完成 `AAPL / MSFT / NVDA` 三只的 Longbridge live 验证，并把 `peer_set` 与 `source attribution` 字段接入 `valuation run` 输出。  

## 1. 本轮新增能力

1. `Relative Valuation` 不再只依赖模板目标倍数
2. 现在会优先读取本地持久化 peer set：
   - `security_master`
   - `market_snapshot`
   - `financial_standardized`
   - `financial_derived_metrics`
3. `decision.source_attribution` 新增：
   - `peer_set_source`
   - `peer_selection_basis`
   - `relative_source_mode`
   - `peer_set_tickers`
   - `risk_free_rate_source`
   - `erp_source`
   - `beta_source`
   - `industry_multiple_source`
   - `target_multiple_sources`
   - `market_multiple_source`

## 2. 三只股票 live 对比

### 2.1 AAPL

1. `price_source = longbridge:2026-03-30`
2. `peer_selection_basis = sector_template`
3. `relative_source_mode = peer_set_plus_template`
4. `peer_set_tickers = [COST, CVX, ABBV, AMD, AXP, AMGN, ABT, GILD]`
5. `industry_multiple_source = damodaran_ev_ebitda:Computers/Peripherals`
6. `fair_value_mid = 172.19`

### 2.2 MSFT

1. `price_source = longbridge:2026-03-30`
2. `market_price_daily_count = 253`
3. `peer_selection_basis = industry`
4. `relative_source_mode = peer_set_plus_template`
5. `peer_set_tickers = [CRM, ADBE, CRWD, DDOG, NVDA, GOOGL, AVGO, GE]`
6. `industry_multiple_source = damodaran_ev_ebitda:Software (System & Application)`
7. `fair_value_mid = 348.46`

### 2.3 NVDA

1. `price_source = longbridge:2026-03-30`
2. `market_price_daily_count = 253`
3. `peer_selection_basis = industry`
4. `relative_source_mode = peer_set_plus_template`
5. `peer_set_tickers = [AVGO, AMD, AMAT, GOOGL, MSFT, GE, CRM, CRWD]`
6. `industry_multiple_source = damodaran_ev_ebitda:Software (System & Application)`
7. `fair_value_mid = 115.50`

## 3. 当前判断

1. `Longbridge` 价格主源已经在三只股票上完成真实验证
2. `Relative Valuation` 已经从“模板目标倍数”升级到“peer set + 模板 fallback”
3. `source attribution` 已经能把价格源、beta 源、行业倍数源、peer set 源显式透出
4. `risk_free_rate_source / erp_source` 目前仍可能为空
   - 原因不是字段没接
   - 而是当前 run 里 FRED 没有稳定产出可用参数，WACC 仍更多沿用库内参数和 Damodaran beta

## 4. 还留着的边界

1. peer set 目前仍是“本库可用样本优先”，不是全市场正式筛选器
2. FRED 当前网络下仍超时回退
3. `AAPL` 当前 peer set 还是按 `sector_template` 命中的，不是理想的消费电子 / 硬件专属组
4. `Relative Valuation` 还没有把增长、利润率、资本回报做成更严格的 peer 过滤条件

## 5. 建议 Claude Code 下一步关注

1. 审 peer set 规则是否合理
   - 行业优先
   - sector template 次之
   - company type / sector 继续回退
2. 给 peer set 设计更明确的准入规则：
   - 市值区间
   - 收入增长区间
   - FCF margin 区间
   - ROIC 区间
3. 继续完善 `source attribution` 的终版字段定义
   - 哪些是必返字段
   - 允许哪些字段为空
   - 前端解释层怎么展示为空来源
