# US Equity Handoff - 2026-03-31 - Longbridge FRED Damodaran

任务批次：`US-09 + US-23` 外部价格/宏观源接入与终版输出结构补强  
当前分支：`codex/java-backend-foundation`  
当前状态：本批代码已本地落地、测试通过、服务运行在 [http://localhost:18080](http://localhost:18080)；已完成一轮真实 Longbridge live 验证，FRED 在当前网络下超时但已完成快速失败与回退收口。  

## 1. 本批完成

1. 接入 `Longbridge` Java SDK：`io.github.longbridge:openapi-sdk:4.0.0`
2. 新增 [UsLongbridgeClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsLongbridgeClient.java)
3. `MarketDataService` 在 `US` 路径下优先读取 `Longbridge`，fallback 到 `Stooq + SEC`
4. `UsMarketDataPersistenceService` 新增 `persistLongbridgeSnapshot(...)`
5. `Longbridge` 行情已接入：
   - `market_data_raw`
   - `market_price_daily`
   - `market_snapshot`
   - `market_intraday_snapshot`
6. 新增 [UsFredClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsFredClient.java)
   - 支持官方 API
   - 支持 `fredgraph.csv` fallback
   - 已配置连接超时和读取超时
7. 新增 [UsDamodaranClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsDamodaranClient.java)
   - ERP
   - industry beta
   - trailing PE
   - EV/EBITDA
8. 新增 [UsExternalValuationParameterService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsExternalValuationParameterService.java)
   - 覆盖 `wacc.rf`
   - `wacc.erp`
   - `wacc.beta`
   - `wacc.size_premium`
   - `wacc.base/floor/ceiling`
   - `terminal_growth.base/floor/ceiling`
   - `relative.target_pe`
   - `relative.target_ev_ebitda`
9. `UsConfiguredValuationModelsService` 现在会把外部参数和 `parameter_sources` 叠加到配置上下文
10. `UsEquityValuationService` 已输出终版结构雏形：
   - `summary`
   - `decision`
   - `explanation`
11. `explanation` 已固定为 12 个 blocks，并输出：
   - `key`
   - `title`
   - `display_order`
12. 修正了 `uncertainty_and_error_sources` 的一个误判：
   - 当 `data_version` 同时包含 `longbridge + stooq` 时，不再把本轮错误标成“纯 fallback”

## 2. 关键文件

1. [UsLongbridgeClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsLongbridgeClient.java)
2. [UsFredClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsFredClient.java)
3. [UsDamodaranClient.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsDamodaranClient.java)
4. [UsExternalValuationParameterService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsExternalValuationParameterService.java)
5. [MarketDataService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/service/MarketDataService.java)
6. [UsMarketDataPersistenceService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsMarketDataPersistenceService.java)
7. [UsConfiguredValuationModelsService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsConfiguredValuationModelsService.java)
8. [UsValuationConfigService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationConfigService.java)
9. [UsEquityValuationService.java](F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsEquityValuationService.java)
10. [UsValuationDecisionResponse.java](F:/fairvalue/src/main/java/com/fairvalue/engine/api/dto/us/UsValuationDecisionResponse.java)
11. [UsValuationExplanationResponse.java](F:/fairvalue/src/main/java/com/fairvalue/engine/api/dto/us/UsValuationExplanationResponse.java)
12. [UsExplanationBlock.java](F:/fairvalue/src/main/java/com/fairvalue/engine/api/dto/us/UsExplanationBlock.java)
13. [application.properties](F:/fairvalue/src/main/resources/application.properties)
14. [UsExternalValuationParameterServiceTest.java](F:/fairvalue/src/test/java/com/fairvalue/engine/us/UsExternalValuationParameterServiceTest.java)

## 3. 测试与运行

1. `./mvnw.cmd test` 通过
2. 当前测试数：`38`
3. 服务地址：[http://localhost:18080](http://localhost:18080)
4. 健康检查：`GET /actuator/health = UP`

## 4. 本轮真实验证结果

### 4.1 Longbridge

1. `POST /v1/us-equities-admin/AAPL/market-sync`
2. 返回关键结果：
   - `market_data_raw_count = 3`
   - `market_price_daily_count = 255`
   - `market_snapshot_count = 4`
   - `market_intraday_snapshot_count = 2`
3. `GET /v1/us-equities/AAPL/valuation/summary`
   - `current_price = 246.63`
   - `data_version = longbridge:2026-03-30|stooq:2026-03-30|sec:2026-01-30`
4. `POST /v1/us-equities/AAPL/valuation/run`
   - `decision.source_attribution.price_source = longbridge:2026-03-30`

结论：`Longbridge` 已真实参与价格层与市场历史落表，不再只是代码接线状态。

### 4.2 FRED

1. 直接请求：
   - `https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10`
2. 当前网络下结果：
   - `~8197 ms timeout`
3. 当前系统行为：
   - `UsFredClient` 已加 timeout
   - `summary/run/report` 不会被 FRED 长时间阻塞
   - 本轮 `AAPL` 输出中的 `macro_sources` 仍以 `valuation_parameter_set + Damodaran` 为主

结论：FRED 代码链路已接通，但当前网络环境下未完成稳定 live 数值验证；系统已正确回退。

### 4.3 Summary / Decision / Explanation

1. `POST /v1/us-equities/AAPL/valuation/run`
2. 已确认返回：
   - `summary`
   - `decision`
   - `explanation`
3. `explanation.blocks = 12`
4. `uncertainty_and_error_sources` 当前样例：
   - `price data version=longbridge:2026-03-30|stooq:2026-03-30|sec:2026-01-30 ...`
   - 已不再出现“明明走了 Longbridge 还被写成 fallback”的误判

## 5. 当前边界

1. `Longbridge` 凭证没有写入仓库，必须通过环境变量启用
2. `FRED` 在当前网络下仍超时，尚未看到稳定 live 宏观参数进入最终 attribution
3. `Damodaran` 已参与 beta / target multiple，但 `wacc.rf / wacc.erp` 在 AAPL 当前输出中还没有稳定压过库内种子参数
4. `Relative Valuation` 还不是 peer set 引擎
5. `AAPL` 当前仍落到 `income_defensive / us_general_quality`

## 6. 建议 Claude Code 下一步关注

1. 审阅 `parameter_sources` 是否已满足终版 PRD 的解释层要求
2. 继续设计 `Summary / Decision / Explanation` 中如何显式展示：
   - `price_source`
   - `macro_source`
   - `erp_source`
   - `industry_multiple_source`
   - `peer_set_source`
3. 继续定义 `Relative Valuation peer set` 规则，而不是只靠行业模板目标倍数
4. 评估 `AAPL` 当前分类是否需要从 `income_defensive / us_general_quality` 调回 `compounder / us_tech_compounder`
