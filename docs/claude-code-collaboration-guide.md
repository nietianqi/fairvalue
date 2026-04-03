# Claude Code Collaboration Guide

这份文档是给 Claude Code 的协作说明。

目标：
让 Claude Code 和 Codex 在同一个仓库里并行推进，但尽量不冲突、不返工、不误改。

## 1. 当前项目事实

1. 项目目录：`F:\fairvalue`
2. 当前重点：美股估值主链路，其次是全局产品 API 收口
3. 当前分支：`codex/java-backend-foundation`
4. 当前服务地址：[http://localhost:18080](http://localhost:18080)
5. 当前健康检查：[http://localhost:18080/actuator/health](http://localhost:18080/actuator/health)
6. 当前工作区仍然是 dirty 的，除了美股链路外，还有 CN / JP / 前端等未整理改动
7. 终版规则基线请同时参考：
   - [全球股票估值系统_统一产品文档.md](F:/fairvalue/全球股票估值系统_统一产品文档.md)
   - [美国股票估值.md](F:/fairvalue/美国股票估值.md)
   - [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
   - [us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
8. 密钥安全硬约束：
   - 所有真实密钥只允许出现在环境变量或 `F:/fairvalue/application-secrets.properties`
   - 不允许写入可提交源码文件
   - 测试环境默认 `enabled=false`
9. 当前 `US` 定时调度已经写入 `valuation_jobs`，并已具备 claim / retry / recover / alerts 基础能力，请把任务状态模型也纳入产品审查范围
10. 当前前端已经有 4 个基础工作台页面：
   - [cn-undervalued-stocks.html](F:/fairvalue/src/main/resources/static/cn-undervalued-stocks.html)
   - [valuation-screener.html](F:/fairvalue/src/main/resources/static/valuation-screener.html)
   - [us-stock-detail.html](F:/fairvalue/src/main/resources/static/us-stock-detail.html)
   - [us-equities-admin.html](F:/fairvalue/src/main/resources/static/us-equities-admin.html)
11. 当前 `US` universe 权威来源已经切换到 `security_master`
12. 最近一次真实 `SEC universe sync` 后，当前 live `US active securities = 8072`
13. 当前 `US rankings` 的产品口径是：
   - `discovery = full-universe paged`
   - `rankings = full-universe paged, page-scoped until valuation_latest_snapshot coverage is complete`

## 2. 当前代码到什么程度

### 2.1 已完成的 US 主链路

1. 数据接入：SEC / IR / Longbridge / FRED / Damodaran / SimFin(部分)
2. 标准化：`financial_standardized`
3. 派生指标：`financial_derived_metrics`
4. 质量评分：`financial_quality_scores`
5. 数据质量：`data_quality_audit`
6. 市场表：`market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`
7. 估值表：
   - `valuation_runs`
   - `valuation_method_results`
   - `scenario_results`
   - `reverse_dcf_results`
   - `risk_scores`
   - `report_blocks`
   - `valuation_latest_snapshot`
   - `valuation_jobs`
8. 方法：
   - `dcf`
   - `historical_multiple`
   - `relative_valuation`
   - `reverse_dcf`
9. 输出：
   - `summary`
   - `decision`
   - `explanation`
   - explanation 固定 `12` blocks

### 2.2 当前读写模式

当前 US 已经切成：

1. 写路径
   - `POST /v1/us-equities/{ticker}/valuation/run`
   - 计算方法 -> 写历史 run -> upsert latest snapshot
2. 读路径
   - `summary / report` 优先读 latest snapshot
   - `rankings` 覆盖完整时优先读 latest snapshot
   - `history` 优先读 `market_price_daily + valuation_runs`
   - `peers` 只读，不再写新的 `valuation_runs`
   - `screener` 优先读 latest snapshot，缺失时走轻量快照估值
3. 定时刷新
   - `UsScheduledValuationRefreshService`
   - 每 30 分钟刷新 `US security_master` universe
   - 当前会写 `valuation_jobs`
   - 当前具备基础失败重试窗口、队列 claim 语义、告警摘要与后台查询接口
4. 平台层
   - envelope / auth / rate limit / entitlement / usage 已进入代码主链路
   - main runtime 已正式启用
   - 前端通过 `/platform/bootstrap` 自举 API key 和 envelope 头

### 2.3 当前还没做完的关键缺口

1. `US peers` 已不再默认空集合，但仍未达到终版行业专属 peer universe
2. `valuation_jobs` 已有 queue foundation，但仍未达到 multi-worker / priority policy / alerting 终版
3. `US rankings` 已有 strict persisted ranking 查询与 coverage gating，但当前 live `strict_ready = false`
4. 平台层已启用，并已补最小 control-plane：
   - `/v1/platform/clients`
   - `/v1/platform/plans`
   - rotate-key / disable
   - quota / usage
   但仍未完成 billing / quota control-plane / entitlement dashboard

## 3. Codex 和 Claude Code 的推荐分工

### 3.1 Codex 负责

1. Java 后端实现
2. Repository / Service / Controller
3. Flyway migration
4. 接口联调
5. 测试运行
6. 服务启动与真实数据验证
7. 精确挑文件提交与推送

### 3.2 Claude Code 负责

1. PRD 校对和补充
2. 规则定义与规则审查
3. explanation blocks 文案与结构设计
4. company_type / sector_template 规则复审
5. peer universe / valuation taxonomy / risk taxonomy 设计
6. API 输出字段验收
7. 文档回写与验收标准整理

## 4. 最推荐的协作模式

1. Claude Code 先定义规则或验收标准
2. Codex 按任务编号落代码
3. Claude Code 审实现是否符合 PRD
4. Codex 修问题并做真实验证
5. 双方回写 handoff / progress / task list

不要两边同时改同一个 Java 文件。

## 4.1 互相布置任务的固定流程

1. `Claude Code -> Codex`
   - 用“任务卡”方式下发：
     - 任务编号
     - 背景与目标
     - 规则口径
     - 需要改的文件范围
     - 完成定义
     - 验收方式
2. `Codex -> Claude Code`
   - 用“实现 handoff”方式回传：
     - 已完成内容
     - 未完成内容
     - live 验证结果
     - 剩余边界
     - 需要 Claude 重点 review 的问题
3. 双方都不要用“开放式大任务”互相丢球
   - 每次最多推进 1 到 3 个明确子任务

## 4.2 互相 review 的固定规则

1. Claude Code 重点 review：
   - 规则是否符合 PRD
   - API 字段是否符合产品口径
   - 文案与解释层是否一致
   - fallback 是否被正确披露
2. Codex 重点 review：
   - 实现是否会触发重型写操作
   - 调度与持久化是否污染历史
   - 性能 / 并发 / 数据完整性风险
   - 前端是否误读后端字段
3. review 输出要尽量收敛成：
   - `P0 / P1 / P2`
   - 文件路径
   - 行为风险
   - 具体修法

## 5. 文件 ownership 建议

### 5.1 建议 Claude Code 主责文件

1. [全球股票估值系统_统一产品文档.md](F:/fairvalue/全球股票估值系统_统一产品文档.md)
2. [美国股票估值.md](F:/fairvalue/美国股票估值.md)
3. [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
4. [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
5. [docs/us-equity-handoff-template.md](F:/fairvalue/docs/us-equity-handoff-template.md)
6. `src/main/resources/rules/*.json`
7. 当前任务如果明确是前端/产品体验，也可以主责：
   - `src/main/resources/static/*.html`
   - `src/main/resources/static/assets/*.css`
   - `src/main/resources/static/assets/*.js`

### 5.2 建议 Codex 主责文件

1. `src/main/java/com/fairvalue/engine/us/**`
2. `src/main/java/com/fairvalue/engine/repository/**`
3. `src/main/java/com/fairvalue/engine/api/**`
4. `src/main/resources/db/migration/**`
5. `src/test/java/com/fairvalue/engine/api/us/**`
6. `src/test/java/com/fairvalue/engine/us/**`

## 6. 不要顺手改的地方

在没有明确任务指向之前，Claude Code 不要顺手修改：

1. `CN / JP` 相关 Java 文件
2. 运行日志文件
3. `.run_pid`
4. 与当前任务无关的 dirty 文件

如果当前任务明确是前端页面，则可以修改静态页，但请优先避免：

1. 顺手改 Java 主链路
2. 顺手改 Flyway migration
3. 为了前端效果而绕过后端真实字段

## 7. 当前最适合 Claude Code 接手的任务

### 第一优先级

1. 审 `valuation_latest_snapshot` 是否满足终版产品口径
2. 审 `summary / decision / explanation` 哪些字段应升级为 API 必返
3. 审 `US strict ranking engine` 的 coverage 阈值、免责声明和切换口径
4. 审 `US peers` 当前行业规则 peer universe 与 `snapshot_fallback` 的产品解释口径
   - 当前已不再默认空集合
   - 但仍需判断哪些场景必须继续回退为 `template_only`
5. 审 30 分钟 `US security_master` universe 调度策略是否需要进一步分层
6. 审 `valuation_jobs` 是否要从现有 claim/retry/recover/alerts/dead-letter 基础版升级成 worker-pool / priority policy / alerting 模式
7. 审平台层当前字段是否满足正式产品口径：
   - `/platform/bootstrap`
   - `/v1/platform/me`
   - `/v1/platform/usage`
8. 审后台页新增的 jobs 操作是否需要更严格的权限/确认：
   - retry ticker failed jobs
   - retry global failed jobs
   - recover stale jobs
9. 审 `US rankings` 何时可以从 `page-scoped full-universe` 升级成 `strict persisted ranking`
10. 审 `valuation_latest_snapshot` 覆盖率达到多少才允许移除当前 disclaimer

### 第二优先级

1. 审 `valuation_jobs` 任务状态、claim 语义、告警阈值与失败重试模型
2. 审 `history` 交易日重放的终版输出结构
3. 审前端工作台的页面分层和缺口：
   - 全球榜单页
   - 全球筛选页
   - 美股详情页
   - 美股后台页
4. 审平台层 `/platform/bootstrap / v1/platform/me / v1/platform/usage` 的商业化字段是否够用

## 8. Claude Code 输出建议格式

请 Claude Code 每次输出尽量包含：

1. 本次任务编号
2. 是否只改文档 / 规则，还是需要 Codex 落代码
3. 建议改哪些文件
4. 不建议改哪些文件
5. 完成定义
6. 验收方式

## 9. 当前建议的双向任务分配

### 9.1 建议 Claude Code 先接手

1. review `US rankings` 的过渡期产品口径：
   - full-universe paged
   - page-scoped ordering
   - disclaimer 何时下线
2. review `US peers` 的行业 rule profile：
   - 哪些行业必须强规则
   - 哪些行业允许 snapshot fallback
3. review 平台层 control-plane：
   - API key lifecycle
   - quota / plan / billing 字段
   - usage 可视化字段

### 9.2 建议 Codex 继续执行

1. 把 `US rankings` 改成 strict persisted ranking engine
2. 把 `valuation_jobs` 升级到更正式的 worker / alerting 系统
3. 继续收紧 `US peers` 的行业专属 peer universe
