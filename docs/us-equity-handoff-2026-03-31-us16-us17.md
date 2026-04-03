# US Equity Handoff 2026-03-31 (US-16 / US-17)

## 1. 本批任务

1. `US-16`：把 `DCF / FCFF` 升级成 PRD 版可配置模型
2. `US-17`：把 `Reverse DCF` 做成独立可解释引擎

## 2. 本批核心实现

### 2.1 新增

1. [UsValuationConfigService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationConfigService.java)
   - 读取 `sector_template_config`
   - 读取 `valuation_parameter_set`
   - 解析 `primary_methods / default_weights / safety_margin / numeric parameters`
2. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
   - 构建 `UsValuationModelContext`
   - 输出配置化 `dcf / historical_multiple / relative_valuation / reverse_dcf`
   - 生成 `UsReverseDcfAnalysis`
3. [V5__us_valuation_parameter_seed.sql](F:/fairvalue/src/main/resources/db/migration/V5__us_valuation_parameter_seed.sql)
   - 为 `us_tech_compounder / us_general_quality / us_cyclical / us_hypergrowth_saas` 写入参数种子

### 2.2 改造

1. [UsMarketValuationStrategy.java](F:/fairvalue/src/main/java/com/fairvalue/engine/valuation/strategy/UsMarketValuationStrategy.java)
   - 不再使用旧启发式公式
   - 改为委托 `UsConfiguredValuationModelsService`
2. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
   - `runValuation()` 改为直接使用配置化方法结果
   - `blended_intrinsic_value` 与 `fair_value_range` 改为按方法输出加权汇总
   - explanation blocks 增加 `reverse_dcf`
3. [UsValuationPersistenceService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationPersistenceService.java)
   - `valuation_method_results` 直接落配置化方法结果
   - `reverse_dcf_results` 直接落 `UsReverseDcfAnalysis`

## 3. 真实运行验证

服务地址：

1. [http://localhost:18080](http://localhost:18080)
2. `GET /actuator/health = UP`

### 3.1 AAPL

1. `valuation_methods = dcf / historical_multiple / relative_valuation / reverse_dcf`
2. `blended_intrinsic_value = 233.37`
3. `fair_value_range = 204.60 - 262.15`
4. `implied_expectation = aggressive`
5. `reverse_dcf.structured_inputs = false`

### 3.2 MSFT

1. `blended_intrinsic_value = 316.51`
2. `fair_value_range = 262.93 - 390.87`
3. `implied_expectation = aggressive`
4. `reverse_dcf.structured_inputs = true`

### 3.3 NVDA

1. `blended_intrinsic_value = 115.36`
2. `fair_value_range = 98.79 - 137.95`
3. `implied_expectation = aggressive`
4. `reverse_dcf.structured_inputs = true`

## 4. 测试

1. 命令：`./mvnw.cmd test`
2. 结果：`37` 个测试全部通过

## 5. 当前边界

1. `Relative Valuation` 仍是模板目标倍数版，不是 peer set 引擎
2. `AAPL` 当前 `DCF / Reverse DCF` 仍会走 fallback，说明结构化 FCFF 输入覆盖不完整
3. `US-19` 风险矩阵仍是轻量版，尚未把 `wacc / scenario_weight / margin_of_safety` 的修正链路完整打通
4. 工作区仍是 dirty 的，提交时只能精确挑文件

## 6. 下一步建议

1. `US-19`：补风险矩阵规则并接入 WACC / scenario 权重修正
2. 补 `AAPL` fallback case 的结构化 FCFF 覆盖
3. 把 `Relative Valuation` 升级成 peer set 引擎
