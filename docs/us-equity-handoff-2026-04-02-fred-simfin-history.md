# US Equity Handoff - 2026-04-02 FRED SimFin History

更新日期：2026-04-02  
当前分支：`codex/java-backend-foundation`  
当前运行服务：[http://localhost:18080](http://localhost:18080)

## 1. 本轮 Codex 已完成

1. 新增 `FRED` 本地安全配置加载
   - 通过 `spring.config.import=optional:file:./application-secrets.properties`
   - 本地文件 `F:/fairvalue/application-secrets.properties` 已放入：
     - `market-data.us.fred.enabled=true`
     - `market-data.us.fred.api-key=...`
2. 新增 `SimFin` 本地安全配置加载
   - 同一安全文件中已放入：
     - `market-data.us.simfin.enabled=true`
     - `market-data.us.simfin.api-key=...`
3. 新增 `UsSimfinClient`
   - 按 SimFin 官方 bulk-download 口径实现
   - 认证头：`Authorization: api-key <key>`
   - 当前已接：`companies` dataset
4. 新增 `GET /v1/us-equities-admin/{ticker}/source-status`
   - 返回 `fred` 和 `simfin` 两类状态
   - 返回 `risk_free_observation / policy_rate_observation`
   - 返回 `simfin.company_record`
5. 修复 `US history`
   - `GET /v1/valuation/history/US/{symbol}` 现已优先读取真实持久化历史
   - 数据来源：`market_price_daily + valuation_runs`
   - 只有无持久化历史时才回退到旧逻辑

## 2. 当前 live 验证

### 2.1 FRED

`GET /v1/us-equities-admin/AAPL/source-status`

结果：
- `fred.enabled = true`
- `fred.api_key_present = true`
- `fred.risk_free_observation.series_id = DGS10`
- `fred.risk_free_observation.date = 2026-03-31`
- `fred.risk_free_observation.value = 0.043`
- `fred.risk_free_observation.source_label = fred_api`

### 2.2 SimFin

`GET /v1/us-equities-admin/AAPL/source-status`

结果：
- `simfin.enabled = true`
- `simfin.api_key_present = true`
- `simfin.authenticated = true`
- `simfin.status = ok`
- `simfin.dataset_url = https://prod.simfin.com/api/bulk-download/s3?dataset=companies&market=us`
- `simfin.company_record.ticker = AAPL`
- `simfin.company_record.simfin_id = 111052`
- `simfin.company_record.industry_id = 101001`
- `simfin.company_record.fiscal_year_end = 9`
- `simfin.company_record.num_employees = 147000`
- `simfin.company_record.cik = 320193`
- `simfin.company_record.main_currency = USD`

### 2.3 Source Attribution

`POST /v1/us-equities/AAPL/valuation/run`

当前已确认：
- `decision.source_attribution.risk_free_rate_source = fred_api`
- `decision.source_attribution.parameter_sources.wacc_rf = fred_api`
- `decision.source_attribution.parameter_sources.terminal_growth = fred_api`

当前仍为空：
- `erp_source = null`

说明：当前 `erp` 仍主要依赖 `Damodaran / valuation_parameter_set`，尚未独立形成更强归因标签。

### 2.4 US History

`GET /v1/valuation/history/US/AAPL?days=30`

当前已返回真实持久化点，例如：
- `2026-03-27 close=220.0 fair=240.0`
- `2026-03-28 close=230.0 fair=240.0`
- `2026-03-31 close=245.0 fair=250.0`
- `2026-04-01 close=255.63 fair=250.0`

这说明 `US history` 已不再使用旧的正弦波模拟。

## 3. 新增 / 修改文件

- `F:/fairvalue/.gitignore`
- `F:/fairvalue/src/main/resources/application.properties`
- `F:/fairvalue/src/test/resources/application.properties`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSimfinProperties.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsSimfinClient.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsExternalSourceStatusService.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/api/UsEquityAdminController.java`
- `F:/fairvalue/src/main/resources/static/us-equities-admin.html`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/repository/MarketPriceDailyRepository.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/repository/ValuationRunsRepository.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/us/UsValuationRunHistoryRecord.java`
- `F:/fairvalue/src/main/java/com/fairvalue/engine/service/ValuationService.java`
- `F:/fairvalue/src/test/java/com/fairvalue/engine/api/us/UsEquityAdminControllerTest.java`
- `F:/fairvalue/src/test/java/com/fairvalue/engine/api/UsValuationHistoryControllerTest.java`
- `F:/fairvalue/docs/us-equity-progress.md`
- `F:/fairvalue/docs/us-equity-agent-task-list.md`

## 4. 测试

命令：`./mvnw.cmd test`

结果：`44` 个测试全部通过

## 5. 希望 Claude Code 优先审的点

1. `SimFin` 现在只接了 `companies` dataset，是否应该优先扩到：
   - `derived`
   - `derived-shareprices`
   - `income/balance/cashflow`
2. `source-status` 输出字段是否还要标准化成终版 envelope / field naming。
3. `US history` 当前已是真实持久化历史，但 fair value 仍依赖最近 `valuation_runs` 的阶梯对齐；是否需要继续补“按交易日重放估值”的终版逻辑。
4. `source attribution` 中 `erp_source` 为空时，终版文案是否应该更明确说明回退来源。
5. `application-secrets.properties` 这条本地安全配置策略是否还要在协作文档中再强调一次。
