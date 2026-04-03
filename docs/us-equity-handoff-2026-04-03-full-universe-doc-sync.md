# US Equity Handoff 2026-04-03

任务编号：US-14 / US-23 / US-24 / DOC-SYNC  
任务名称：US full-universe 状态同步、任务系统与 peer universe 协作交接  
交接日期：2026-04-03  
交接人：Codex  
接手人：Claude Code  
当前分支：`codex/java-backend-foundation`

## 1. 本批目标

1. 把当前代码与文档对齐到最新 `US full-universe` 状态
2. 明确哪些能力已经完成，哪些还处于过渡态
3. 给 Claude Code 一个可直接 review 和继续拆任务的基线

本批不包含：

1. 不推进 `CN / JP / HK` 新能力
2. 不做新的大规模业务代码重构
3. 不处理无关 dirty worktree 文件

## 2. 当前已完成的关键事实

1. `US discovery` 已切到 `security_master` 全量分页读取
2. `US rankings` 已切到 `security_master` 全量分页口径
3. 最近一次真实 `SEC universe sync` 结果：
   - `total_fetched = 10433`
   - `inserted = 8068`
   - `updated = 2365`
   - `failed = 0`
4. 当前 live `US active securities = 8072`
5. `US rankings` 仍是过渡态：
   - universe 是全量
   - 排序仍是 `page-scoped`
   - 原因是 `valuation_latest_snapshot` 还未覆盖全量 universe
6. `valuation_jobs` 已具备：
   - `queued / running / completed / failed`
   - `priority / available_at / worker_id`
   - `claim / retry / recover / alerts`
7. `US peers` 已不再默认空集合
8. `US peers` 当前 live 经常落在：
   - `strict peer universe first`
   - `snapshot_fallback` second
9. 平台层当前已启用：
   - envelope
   - API key
   - rate limit
   - entitlement
   - usage

## 3. 当前未完成的关键点

1. `US rankings` 还不是 strict persisted ranking engine
2. `valuation_jobs` 还不是完整 worker-pool / priority-policy / alerting 系统
3. `US peers` 还不是终版行业专属 peer universe
4. 平台层仍未完成：
   - client lifecycle
   - billing
   - quota / plan control-plane
5. `history` 还未做到按交易日重放估值

## 4. 建议 Claude Code 优先 review 的问题

1. `US rankings` 当前的产品口径是否合理：
   - `full-universe paged`
   - `page-scoped ordering until snapshot coverage is complete`
2. `valuation_latest_snapshot` 覆盖率达到什么阈值时，才允许移除当前 disclaimer
3. `US peers` 哪些行业必须强规则，哪些行业允许 `snapshot_fallback`
4. 平台层当前 control-plane 还缺哪些最小必需字段：
   - `/platform/bootstrap`
   - `/v1/platform/me`
   - `/v1/platform/usage`
5. `valuation_jobs` 是否应该优先引入：
   - multi-worker claim
   - priority bands
   - backlog alert escalation

## 5. 建议 Claude Code 发给 Codex 的下一批任务卡

1. `US-23`
   - 目标：把 `US rankings` 改成 strict persisted ranking engine
   - 需要明确：
     - 排序字段
     - 覆盖率门槛
     - disclaimer 下线条件
2. `US-24`
   - 目标：把 `valuation_jobs` 从 queue foundation 升级成正式 worker / priority / alerting 系统
   - 需要明确：
     - priority policy
     - retry ceiling
     - stale/recover SLA
3. `US-14`
   - 目标：把 `US peers` 收紧到行业专属 peer universe
   - 需要明确：
     - 行业 rule profile
     - fallback 允许范围
     - 必需字段与缺失字段文案

## 6. 当前建议的互相 review 方式

1. Claude Code review 规则和产品口径
2. Codex review实现是否会：
   - 误写库
   - 污染历史
   - 拉低性能
   - 误导前端字段
3. review 结论统一写成：
   - `P0 / P1 / P2`
   - 文件路径
   - 风险说明
   - 修复建议

## 7. 当前推荐阅读顺序

1. [全球股票估值系统_统一产品文档.md](F:/fairvalue/全球股票估值系统_统一产品文档.md)
2. [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
3. [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
4. [docs/claude-code-collaboration-guide.md](F:/fairvalue/docs/claude-code-collaboration-guide.md)
