# US Equity Progress

更新日期：2026-04-03  
当前分支：`codex/java-backend-foundation`  
当前服务地址：[http://localhost:18080](http://localhost:18080)  
当前健康检查：[http://localhost:18080/actuator/health](http://localhost:18080/actuator/health)

## 1. 当前阶段一句话总结

**US 估值主链路已经从“读接口现算”为主，推进到“手动/定时计算写库 + 读接口优先读库 + full-universe paged discovery/rankings”的阶段。**

当前已经具备：

1. 外部源接入
2. 标准化与质量评分
3. 多方法估值
4. 风险矩阵
5. 历史落库
6. 最新快照落库
7. summary/report/rankings 读库
8. 30 分钟 `US security_master` universe 定时刷新骨架
9. `valuation_jobs` 已进入队列化基础阶段并有告警摘要
10. 平台层已正式启用：envelope / API key / rate limit / entitlement / usage
11. 前端已补齐基础工作台链路：榜单页、筛选页、详情页、后台页
12. 本轮新增：
   - `US strict ranking engine` 已落地 strict query + coverage gating
   - `US peers` 已升级成行业规则优先并新增 `peer_selection_mode / peer_quality_score / peer_data_completeness`
   - `valuation_jobs` 已补 priority / backoff / dead-letter / alert summary
   - 平台 control-plane 已补 `clients / rotate-key / disable / plans / quota`

## 1.1 当前最重要的新事实

1. `US discovery` 已经从内存 seed universe 切到 `security_master` 全量分页读取
2. `US rankings` 也已切到 `security_master` 全量分页口径
3. 最近一次真实 `SEC universe sync` 后，当前 live `US active securities = 8072`
4. `US rankings` 仍处于过渡态：
   - universe 已经是全量
   - 但在 `valuation_latest_snapshot` 尚未覆盖全量 universe 之前，排序仍是 `page-scoped`
5. 当前应把产品口径表述为：
   - `discovery = full-universe paged`
   - `rankings = full-universe paged, page-scoped until snapshot coverage is complete`
6. 当前 strict ranking 自动切换依赖：
   - snapshot freshness
   - confidence threshold
   - market cap guardrail
   - rankable coverage >= configured threshold

## 2. 当前运行状态

1. 服务地址：[http://localhost:18080](http://localhost:18080)
2. 全球榜单页：[http://localhost:18080/cn-undervalued-stocks.html](http://localhost:18080/cn-undervalued-stocks.html)
3. 全球筛选页：[http://localhost:18080/valuation-screener.html](http://localhost:18080/valuation-screener.html)
4. 美股详情页：[http://localhost:18080/us-stock-detail.html?ticker=AAPL](http://localhost:18080/us-stock-detail.html?ticker=AAPL)
5. 后台页面：[http://localhost:18080/us-equities-admin.html](http://localhost:18080/us-equities-admin.html)
6. 源状态接口：`GET /v1/us-equities-admin/{ticker}/source-status`
7. 告警接口：`GET /v1/us-equities-admin/valuation-alerts`
8. 平台 bootstrap：`GET /platform/bootstrap`
9. 平台 usage：`GET /v1/platform/usage`
10. 健康检查：`GET /actuator/health = UP`
11. 启动方式：Spring Boot + embedded PostgreSQL + Flyway

## 3. 已完成的真实能力

### 3.1 数据接入与标准化

1. SEC universe 同步
2. `submissions -> source_documents`
3. `companyfacts -> source_document_facts_raw`
4. 公司 IR feed 元数据 -> `source_documents`
5. `financial_standardized`
6. `financial_derived_metrics`
7. `financial_quality_scores`
8. `data_quality_audit`
9. `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`
10. `Longbridge / FRED / Damodaran / SimFin` 客户端和 `source-status`
11. 平台 API 客户端与 usage metering：
   - `api_clients`
   - `api_usage_daily`
   - `/platform/bootstrap`

### 3.2 估值落库

当前真实 run 会写入：

1. `valuation_runs`
2. `valuation_method_results`
3. `scenario_results`
4. `reverse_dcf_results`
5. `risk_scores`
6. `report_blocks`
7. `valuation_latest_snapshot`
8. `valuation_jobs`
9. `api_usage_daily`

### 3.3 当前估值方法

当前真实 `valuation run` 默认会落出 4 个核心方法：

1. `dcf`
2. `reverse_dcf`
3. `relative_valuation`
4. `historical_multiple`

### 3.4 当前读库状态

1. `GET /v1/us-equities/{ticker}/valuation/summary`
   - 优先读取 `valuation_latest_snapshot.summary_json`
2. `GET /v1/us-equities/{ticker}/valuation/report`
   - 优先读取 `valuation_latest_snapshot.report_json`
3. `GET /v1/rankings/US/*`
   - 当 latest snapshot 覆盖当前 `US security_master` universe 时优先读库
   - 覆盖不完整时，返回全量 universe 的 `page-scoped ranking`
4. `GET /v1/valuation/history/US/{ticker}`
   - 优先读取 `market_price_daily + valuation_runs`
5. `POST /v1/screener/valuation`
   - `US` 优先读取 latest snapshot，缺失时走轻量快照估值
6. `GET /v1/peers/US/{ticker}`
   - 只读，不再触发新的 `valuation_runs`
   - 优先走行业规则 peer universe
   - 兜底走只读 `snapshot_fallback`

### 3.5 当前前端工作台

当前已经能走通这条基础操作路径：

1. 在全球榜单页切市场和排序
2. 进入美股详情页查看 summary / report / peers / history / risk
3. 在筛选页直接调用 `POST /v1/screener/valuation`
4. 在后台页查看：
   - source status
   - valuation alerts
   - platform plan / usage
   - valuation jobs summary
   - ticker recent jobs
   - global recent jobs
   - overview 数据快照

## 4. 定时刷新现状

1. 已开启 `@EnableScheduling`
2. 已新增 `UsScheduledValuationRefreshService`
3. 默认每 30 分钟执行一次
4. 当前范围：`US security_master` universe
5. 当前写库模式：`run_mode = scheduled`
6. 手动重算模式：`run_mode = manual`
7. 定时调度会写入 `valuation_jobs`
8. 当前状态流转：`queued -> running -> completed / failed`
9. 当前队列字段：`priority / available_at / worker_id`
10. 当前失败重试：下一轮优先处理 `retryable failed jobs`
11. 当前 stale recovery：把超时 `running` 任务重新排回 `queued`
12. 当前告警摘要：`GET /v1/us-equities-admin/valuation-alerts`
13. 当前 worker 模式：`enqueue` 与 `claim/process` 已拆分为两条调度循环
14. `app.valuation.us.schedule.max-symbols=0` 时表示按全量 `security_master` universe 调度
15. 当前 `valuation_jobs` 已具备：
   - `priority / available_at / worker_id`
   - `queued -> running -> completed / failed / dead_letter`
   - retry backoff
   - stale recovery
   - alert summary
16. 平台 control-plane 已具备：
   - `GET /v1/platform/clients`
   - `POST /v1/platform/clients`
   - `POST /v1/platform/clients/{id}/rotate-key`
   - `POST /v1/platform/clients/{id}/disable`
   - `GET /v1/platform/plans`
   - `GET /v1/platform/usage`

## 5. 当前 live 验证结论

### 5.1 外部源

1. `Longbridge`
   - 已完成 `AAPL / MSFT / NVDA` live 验证
2. `FRED`
   - 已完成真实 key 验证
   - `risk_free_rate_source = fred_api`
3. `Damodaran`
   - 已进入参数归因
4. `SimFin`
   - `companies` 与 `source-status` 已 live 通
   - `derived` 入口已接，但当前 key 受订阅级别限制

### 5.2 读库验证

已验证：

1. 手动执行 `POST /v1/us-equities/AAPL/valuation/run` 后，`valuation_latest_snapshot` 会写入
2. 连续调用 `summary / report` 不会继续新增新的 `valuation_runs`
3. `history` 已返回 `valuation_run_date`
4. `screener` 当前能在超时前返回
5. `valuation-jobs/summary` 已返回统计
6. `AAPL overview` 已返回 `valuation_run_count / latest_valuation_snapshot_present / valuation_job_count / latest_valuation_jobs`
7. `GET /v1/peers/US/AAPL?limit=5` 已不再返回空集合
   - 当前模式：严格 peer universe 优先，live 样本目前多为 `snapshot_fallback`
   - 当前 basis：`snapshot_fundamentals`
   - `verdict` 已统一收口为 `UNDERVALUED / OVERVALUED / FAIR`
   - 当前已能返回部分真实 `market_cap`
8. `GET /v1/discovery/US?page=1&size=1` 在 header `X-Use-Envelope: true` 下已返回：
   - `success`
   - `request_id`
   - `timestamp`
   - `data`
9. `valuation-jobs/summary` 当前已可读到队列状态统计
10. `GET /v1/platform/usage` 当前已能看到真实 usage 记账
11. `GET /v1/us-equities-admin/valuation-alerts` 当前已返回空告警或 backlog/failed 摘要
12. `POST /v1/us-equities-admin/valuation-jobs/retry-failed` 已可用
13. `POST /v1/us-equities-admin/valuation-jobs/recover-stale` 已可用
14. `us-equities-admin.html` 已补：
   - 重试本票失败任务
   - 重试全局失败任务
   - 恢复卡住任务
15. `POST /v1/us-equities-admin/universe/sync` 最近一次真实返回：
   - `total_fetched = 10433`
   - `inserted = 8069`
   - `updated = 2364`
   - `failed = 0`
16. `GET /v1/discovery/US?page=1&size=50` 当前 live 返回：
   - `total = 8072`
   - `source = us_security_master_paged`
17. `GET /v1/rankings/US/undervalued?page=1&size=50` 当前 live 返回：
   - `total = 8072`
   - `source = us_security_master_page_scoped_ranking|full-universe-paged`
18. `GET /v1/us-equities-admin/ranking-coverage` 当前 live 返回：
   - `universe_size = 8072`
   - `snapshot_count = 0`
   - `rankable_count = 0`
   - `strict_ready = false`
   - `ranking_mode = page_scoped_ranking`
19. `GET /v1/peers/US/AAPL?limit=5` 当前 live 返回：
   - `peer_selection_mode = snapshot_fallback`
   - `peer_candidate_count = 5`
   - `peer_filter_metrics = industry / sector_template / company_type / market_cap / pe / revenue_growth / fcf_margin / roic / liquidity`
20. `GET /v1/platform/usage` 在 envelope 模式下当前 live 返回：
   - `daily_quota = 25000`
   - `remaining_quota = 24996`
   - 已出现真实 route usage 记账
21. `GET /platform/bootstrap` 当前已具备安全兜底：
   - 无 public client 时不再抛异常
   - 返回空 `api_key` 与 `force_envelope = false`
22. `POST /v1/us-equities/{ticker}/valuation/run` 当前请求体必须包含：
   - `style`
   - `horizon`
   推荐默认值：
   - `style = balanced`
   - `horizon = 6-18m`
23. Top 50 US snapshot backfill 当前已完成最小验收：
   - `snapshot_count = 923`
   - `rankable_count = 497`
   - `GET /v1/peers/US/AAPL?limit=5` 当前已返回 `5` 条真实同行数据
24. `GET /v1/us-equities-admin/AAPL/source-status` 当前 live 返回：
   - `damodaran.status = warning`
   - `damodaran.erp_source = damodaran_static_fallback`
25. `GET /v1/valuation/history/US/AAPL?days=10` 当前 live 已返回：
   - `valuation_run_date`
   - `run_id`
26. `JP` canonical API 当前已切到：
   - `GET /v1/jp-equities/{code}/overview`
   - `GET /v1/jp-equities/{code}/fair-value`
   - `GET /v1/jp-equities/{code}/history`
   - `GET /v1/jp-equities/{code}/events`
27. `POST /v1/us-equities/{ticker}/valuation/run` 当前已支持空 body 默认值：
   - `style = balanced`
   - `horizon = 6-18m`
   - `useConsensus = true`
28. `POST /v1/us-equities-admin/snapshot-backfill/top50` 当前已可直接回填跨行业 Top 50：
   - 最近一次 live：`completed = 38`
   - `missing_in_security_master = 12`
   - `failed = 0`
29. `POST /v1/us-equities-admin/snapshot-backfill/universe?page=1&size=100&mode=queue` 当前已可批量把 US universe 页面加入估值队列：
   - 适合用来提升 `valuation_latest_snapshot` 覆盖率
   - 默认建议：`page=1,size=100,mode=queue,priority=220`
30. `us-equities-admin.html` 当前已补：
   - `Top 50 Backfill` 按钮
   - `Top 100 Queue` 按钮
   - `严格榜单覆盖率` 卡片（Universe / Snapshots / Rankable / Coverage）
31. `UsSnapshotBackfillService` 当前支持两种模式：
   - `run`：立即执行估值并落快照
   - `queue`：仅入队，由 worker 异步处理
32. embedded PostgreSQL 当前已支持复用现有 `5432` 实例：
   - 本地测试和开发服务不再因为重复 `initdb` 直接失败
   且旧 `GET /api/jp/stocks/*` 已返回 `302` redirect

## 5.3 Session 8 前端新增（2026-04-03，Claude Code）

1. **估值筛选器全面重构**（Investing.com 风格）
   - 策略 Chip 横向滚动栏（9 个预设策略）
   - 分类 Tab 展开筛选面板（12 类 150+ 指标入口）
   - 工具栏：市场 Tab × 视图 Tab（9 种视图，列动态切换）
   - Avatar + 彩色 Ticker 链接 + CSV 导出
   - **P0 修复**：去掉 `platform-client.js` 依赖，消除 "Missing or invalid API key" 报错

2. **CN 股票详情页新建**（`cn-stock-detail.html` + `cn-stock-detail.js`）
   - `confidenceScore` 正确处理为 int 0-100
   - 估值 Tab：方法表 + 情景矩阵 + 操作区间 + 分析章节 Accordion
   - 历史/同行/风险 Tab 完整（含 SVG 折线图 + 懒加载）

3. **全球榜单 CN Ticker 链接**
   - CN 市场 ticker 从锁图标 → `<a href="/cn-stock-detail.html?ticker=...">` 链接

## 6. 当前未完成的关键点

1. `valuation_jobs` 已有 worker/alerting 基础版，但还没有完整 multi-worker 池、熔断和更细的优先级策略
2. `US rankings` 虽然已经有 strict persisted ranking 查询与 coverage gating，但当前 live `strict_ready = false`
   - 要做到严格排序，仍需把 `valuation_latest_snapshot` 覆盖率推满
   - 然后改成直接 `ORDER BY persisted upside / confidence / valuation status`
3. `US peers` 已有行业规则优先和只读 `snapshot_fallback`
   - 但还不是终版行业专属 peer universe
   - 当前 live 仍常落到 `snapshot_fallback`
   - `roic` 和部分 `market_cap` 仍可能缺失
4. `history` 还未做到按交易日重放估值
5. `SimFin derived / income / cashflow` 还未真正进入估值主链路
6. 行业专属模型仍未补齐：Bank / Insurance / REIT / Biotech / SOTP
7. 平台层已启用 envelope / auth / rate limit / entitlement / usage + 最小 control-plane，但仍未完成：
   - 正式 client lifecycle 管理
   - billing / entitlement 控制台
   - 使用量套餐与超额策略
   - OpenAPI 契约全量收口

## 7. 下一步建议

### P0

1. 把 `US peers` 真正做成行业专属 peer universe
2. 把 `valuation_jobs` 从基础 worker/alerting 体系升级成正式任务系统
3. 把 `US rankings` 从 full-universe paged 过渡到 strict persisted ranking engine

### P1

1. 历史估值按交易日重放
2. 行业专属模型补齐
3. 全市场持久化 rankings

### P2

1. watchlist / alert / webhook
2. CN 接入 `latest snapshot + schedule + read-db`
3. 多租户 / 白标
