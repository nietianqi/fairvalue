# US Equity Handoff 2026-04-01

任务批次：`US-14 + source attribution review follow-up`  
主题：`Relative Valuation peer set` 收紧、`source attribution` 修复、回归测试补齐

## 1. 本轮完成

1. 修复了 `target_multiple_sources` 在缺失来源时可能触发的空值问题。
2. 修复了 `template_only` 场景下 `peer_set_source / peer_selection_basis / relative_source_mode` 的误报。
3. 把 `Relative Valuation peer set` 从“宽松补齐”升级为“分层选组 + 严格过滤 + 模板回退”：
   - 候选池扩大到 `max(limit * 3, 24)`
   - 选择优先级：`industry -> sector_template -> company_type -> sector`
   - 严格过滤：
     - 市值区间
     - `FCF margin` 贴近度
     - `ROIC` 贴近度
     - 至少有一个可用倍数
   - 如果没有满足阈值的干净 peer set，回退 `template_only`
4. 新增 `peer_selection_breakdown`
5. 新增 `peer_selection_rule_version = v2_strict`
6. 新增 `peer_filter_summary`

## 2. 关键代码

1. [SecurityMasterRepository.java](F:/fairvalue/src/main/java/com/fairvalue/engine/repository/SecurityMasterRepository.java)
2. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
3. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
4. [UsRelativePeerSelectionTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/us/UsRelativePeerSelectionTest.java)

## 3. 测试

1. 针对性测试：
   - `.\mvnw.cmd "-Dtest=UsRelativePeerSelectionTest,UsEquityControllerTest" test`
2. 全量测试：
   - `.\mvnw.cmd test`
3. 结果：
   - `41` 个测试全部通过

## 4. 这轮规则的真实含义

当前 peer set 已不再是“只要能补满就塞进来”的模式，而是：

1. 先按业务同类性分层
2. 再按量化相似度收紧
3. 不够干净就宁可回退模板

这让 `source attribution` 更诚实，也减少了把明显不相干大票混进 peer basket 的情况。

## 5. 仍需 Claude Code 继续审的点

1. `收入增长区间` 还没有正式接到 peer 筛选里  
原因：当前 peer 持久化层没有直接可用的稳定 `revenue_growth` 信号。

2. `行业专属 peer rule` 还没完成  
例如：
   - 硬件 / 半导体
   - SaaS
   - 银行 / 保险
   - REIT
   - Biotech

3. `AAPL` 分类与 peer set 仍值得复核  
需要判断是否应从当前分类调回更偏 `compounder / us_tech_compounder` 的路径。

4. `source attribution` 终版字段仍可继续细化  
当前已经比较可用，但还可以继续明确：
   - 必返字段
   - 允许为空字段
   - 前端为空展示策略

## 6. 建议发给 Claude Code 的任务

1. 审当前 `peer set` 严格规则是否合理，特别是：
   - 市值区间阈值
   - `FCF margin` 容差
   - `ROIC` 容差
   - 至少需要多少个 peer 才算有效
2. 输出 `收入增长区间` 的最终规则稿，并说明需要哪些持久化字段支持
3. 审 `AAPL / MSFT / NVDA` 的 peer set 是否符合终版产品口径
4. 审 `peer_selection_breakdown / peer_filter_summary` 是否满足 explanation 层需要
