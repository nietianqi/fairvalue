# US Equity Handoff 2026-04-01

任务批次：`US-14 follow-up + source attribution v2 + live validation`  
主题：`收入增长区间接入 peer set`、`AAPL / MSFT / NVDA live 验证`、`source attribution 终版字段补强`

## 1. 本轮完成

1. 把 `收入增长区间` 正式接进 `Relative Valuation peer set`。
2. `peer set` 当前严格过滤维度已变成：
   - `market_cap`
   - `revenue_growth`
   - `fcf_margin`
   - `roic`
   - `usable_multiple`
3. `source attribution` 已扩展到 `v2`，新增并验证：
   - `peer_candidate_count`
   - `peer_selection_rule_version`
   - `peer_filter_summary`
   - `peer_filter_metrics`
   - `effective_target_multiple_sources`
   - `source_attribution_version`
4. 已用 `Longbridge` 新进程重新跑通：
   - `AAPL`
   - `MSFT`
   - `NVDA`

## 2. 关键代码

1. [UsRelativePeerComparable.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsRelativePeerComparable.java)
2. [SecurityMasterRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/SecurityMasterRepository.java)
3. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
4. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
5. [UsRelativePeerSelectionTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/us/UsRelativePeerSelectionTest.java)

## 3. 测试

1. 命令：`./mvnw.cmd test`
2. 结果：`42` 个测试全部通过
3. 新覆盖点：
   - `revenue_growth` 纳入 peer 严格过滤
   - `source attribution v2` 字段不崩溃且返回稳定

## 4. 2026-04-01 live 验证结果

### 4.1 AAPL

1. `price_source = longbridge:2026-03-31`
2. `current_price = 253.79`
3. `fair_value_mid = 275.90`
4. `peer_selection_basis = sector_template`
5. `relative_source_mode = peer_set_plus_template`
6. `peer_candidate_count = 2`
7. `peer_filter_summary = basis=sector_template, selected=2, mode=relaxed`
8. `peer_set_tickers = [NVDA, MSFT]`

### 4.2 MSFT

1. `price_source = longbridge:2026-03-31`
2. `current_price = 370.17`
3. `fair_value_mid = 353.44`
4. `peer_selection_basis = sector_template`
5. `relative_source_mode = peer_set_plus_template`
6. `peer_candidate_count = 2`
7. `peer_filter_summary = basis=sector_template, selected=2, mode=relaxed`
8. `peer_set_tickers = [NVDA, AAPL]`

### 4.3 NVDA

1. `price_source = longbridge:2026-03-31`
2. `current_price = 174.40`
3. `fair_value_mid = 118.08`
4. `peer_selection_basis = sector_template`
5. `relative_source_mode = peer_set_plus_template`
6. `peer_candidate_count = 2`
7. `peer_filter_summary = basis=sector_template, selected=2, mode=relaxed`
8. `peer_set_tickers = [AAPL, MSFT]`

### 4.4 当前 source attribution v2 共同特征

1. `peer_selection_rule_version = v2_strict`
2. `peer_filter_metrics = [market_cap, revenue_growth, fcf_margin, roic, usable_multiple]`
3. `effective_target_multiple_sources.target_ev_ebitda = peer_set`
4. `effective_target_multiple_sources.target_pe = peer_set`
5. `beta_source = damodaran_beta:Computers/Peripherals`
6. `risk_free_rate_source = null`
7. `erp_source = null`
8. `source_attribution_version = v2`

说明：

- 这轮 live 进程显式打开了 `Longbridge`
- 这轮 live 进程没有提供 `FRED_API_KEY`，所以 `FRED` 保持关闭
- 因此 `risk_free_rate_source / erp_source` 当前为空是预期行为，不是代码丢字段

## 5. 当前实现含义

1. peer set 已不再只按“行业相似”补齐，而是开始要求“量化相似”。
2. `收入增长` 已进入 peer 过滤，因此成长差异过大的公司更难混进同一个 basket。
3. 当前三只股票都只选出了 `2` 个 peer，说明严格规则已经开始起作用。
4. 当前仍然会在 peer 不足时进入 `mode=relaxed`，所以这还不是终版最严格 universe。

## 6. 请 Claude Code 优先审这几件事

1. `revenue_growth` 当前容差 `0.18` 是否合理
2. `peer_candidate_count = 2` 是否足够让 `peer_set_plus_template` 成立
3. `AAPL / MSFT / NVDA` 互为 peer 是否符合终版产品口径
4. `peer_selection_basis = sector_template` 是否应该继续细分到更窄的行业模板
5. `source attribution v2` 里哪些字段应升为“必返”

## 7. 建议下一步

1. 继续做行业专属 peer 规则
2. 继续补 `source attribution` 的宏观参数来源字段
3. 继续补 `AAPL` 的标准化财务覆盖，减少对托底逻辑的依赖
