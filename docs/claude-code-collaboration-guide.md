# Claude Code Collaboration Guide

这份文档是给 Claude Code 的协作说明。

目标：
让 Claude Code 和 Codex 在同一个仓库里并行推进，但尽量不冲突、不返工、不误改。

## 1. 当前项目事实

1. 项目目录：`F:\fairvalue`
2. 当前重点：美股估值主链路
3. 当前分支：`codex/java-backend-foundation`
4. 上一稳定基线提交：`6de2770`
5. 当前服务地址：[http://localhost:18080](http://localhost:18080)
6. 当前健康检查：[http://localhost:18080/actuator/health](http://localhost:18080/actuator/health)
7. 当前工作区仍然是 dirty 的，除了美股链路外，还有 CN / JP / 前端等未整理改动
8. 终版规则基线请同时参考：
   - [美国股票估值.md](F:/fairvalue/美国股票估值.md)
   - [us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
   - [us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
   - 最新 handoff 文档

## 2. 当前已完成到什么程度

### 2.1 数据与落库链路

1. `SEC universe -> security_master / identifier_map`
2. `submissions -> source_documents`
3. `companyfacts -> source_document_facts_raw`
4. `financial_standardized`
5. `financial_derived_metrics`
6. `financial_quality_scores`
7. `data_quality_audit`
8. `market_data_raw / market_price_daily / market_snapshot / market_intraday_snapshot`
9. `valuation_runs / valuation_method_results / scenario_results / reverse_dcf_results / risk_scores / report_blocks`

### 2.2 当前估值方法

1. `dcf`
2. `reverse_dcf`
3. `relative_valuation`
4. `historical_multiple`

### 2.3 当前外部源状态

1. `Longbridge`
   - 已接入并完成 `AAPL / MSFT / NVDA` live 验证
   - 当前 `price_source` 已可显式返回 `longbridge:2026-03-30`
2. `FRED`
   - 代码链路已接入
   - 当前网络下会超时回退
3. `Damodaran`
   - 已参与 `beta / target multiple` 归因

### 2.4 当前输出结构

`valuation run` 已返回三层：

1. `summary`
2. `decision`
3. `explanation`

其中 `explanation` 已固定 `12` 个 blocks。

### 2.5 Relative Valuation 当前状态

1. 已从“纯模板目标倍数”升级到“peer set + 模板 fallback”
2. 当前 `source_attribution` 已能返回：
   - `price_source`
   - `risk_free_rate_source`
   - `erp_source`
   - `beta_source`
   - `industry_multiple_source`
   - `target_multiple_sources`
   - `market_multiple_source`
   - `peer_set_source`
   - `peer_selection_basis`
   - `relative_source_mode`
   - `peer_set_tickers`

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
5. peer set / valuation taxonomy / risk taxonomy 设计
6. API 输出字段验收
7. 文档回写与验收标准整理

一句话分工：

- Codex 负责“把东西做出来、跑起来、验证掉”
- Claude Code 负责“把规则讲清楚、审正确、补边界”

## 4. 最推荐的协作模式

按这个顺序协作：

1. Claude Code 先定义规则
2. Codex 按任务编号实现
3. Claude Code 审实现结果是否符合 PRD
4. Codex 修审查问题并做真实验证
5. 双方回写交接文档

不要两边同时改同一个 Java 文件。

## 5. 文件 ownership 建议

### 5.1 建议 Claude Code 主责文件

1. [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
2. [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
3. [docs/us-equity-handoff-template.md](F:/fairvalue/docs/us-equity-handoff-template.md)
4. [美国股票估值.md](F:/fairvalue/美国股票估值.md)
5. `src/main/resources/rules/*.json`
6. explanation 相关文档和验收说明

### 5.2 建议 Codex 主责文件

1. `src/main/java/com/fairvalue/engine/us/**`
2. `src/main/java/com/fairvalue/engine/repository/**`
3. `src/main/java/com/fairvalue/engine/api/**`
4. `src/main/resources/db/migration/**`
5. `src/test/java/com/fairvalue/engine/api/us/**`
6. `src/test/java/com/fairvalue/engine/us/**`

## 6. 不要碰的地方

在没有明确任务指向之前，Claude Code 不要顺手修改这些：

1. `CN / JP` 相关 Java 文件
2. 前端榜单静态页
3. `README.md`
4. 运行日志文件
5. `.run_pid`
6. 任何与本批任务无关的 dirty 文件

## 7. 每次接手前必须先读的文件

1. [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
2. [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
3. [docs/us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
4. 最新 handoff 文档
5. [docs/us-equity-handoff-template.md](F:/fairvalue/docs/us-equity-handoff-template.md)

## 8. Claude Code 接手时建议输出格式

请 Claude Code 每次输出都尽量包含：

1. 本次任务编号
2. 是只改文档 / 规则，还是需要 Codex 落代码
3. 建议改哪些文件
4. 不建议改哪些文件
5. 完成定义
6. 验收方式

## 9. 当前最适合 Claude Code 接手的任务

### 第一优先级

1. `US-22 / US-23`
说明：
继续补齐 `run / summary / report` 的终版 PRD 字段、explanation blocks 文案和 source attribution 展示规则

### 第二优先级

2. `Relative Valuation peer set 规则审校`
说明：
请先审当前 peer set 规则是否合理，并输出更严格的准入条件：
- 市值区间
- 收入增长区间
- FCF margin 区间
- ROIC 区间
- 行业 / sector template / company type 优先级

### 第三优先级

3. `FRED / Damodaran attribution 规则`
说明：
请明确：
- `risk_free_rate_source` 为空时前端和 API 如何表达
- `erp_source` 为空时如何表达
- 哪些 source attribution 字段是必返字段，哪些允许为空

### 第四优先级

4. `AAPL 分类与 peer set 复核`
说明：
请判断 `AAPL` 当前落到 `income_defensive / us_general_quality` 是否合理，是否应调回 `compounder / us_tech_compounder`

## 10. 可以直接发给 Claude Code 的消息

下面这段可以直接发给 Claude Code：

```text
请按下面协作方式与 Codex 配合：

1. 先阅读：
- docs/us-equity-agent-task-list.md
- docs/us-equity-progress.md
- docs/us-equity-final-plan-v2-alignment.md
- docs/us-equity-handoff-template.md
- 最新 handoff 文档，尤其是：
  - docs/us-equity-handoff-2026-03-31-longbridge-fred-damodaran.md
  - docs/us-equity-handoff-2026-03-31-live-validation-comparison.md

2. 当前代码基线：
- 分支：codex/java-backend-foundation
- 上一稳定基线提交：6de2770
- 更近的本地实现请以最新 handoff 和 `git log -1` 为准
- 服务：http://localhost:18080

3. 你的职责优先级：
- 先做 PRD / 规则 / explanation / 验收标准
- 尽量不要直接改 Java 主链路文件
- 如果需要 Codex 落代码，请明确：
  - 任务编号
  - 输入输出字段
  - 规则口径
  - 完成定义
  - 验收方式

4. 当前最适合你接手：
- US-22 / US-23：补 run / summary / report 的终版字段和 explanation 规则
- Relative Valuation 的 peer set 规则复核与收紧
- FRED / Damodaran attribution 规则定义
- AAPL 分类与 peer set 的合理性审查

5. 注意：
- 不要顺手改 CN / JP / 前端榜单相关文件
- 不要碰无关 dirty 文件
- 任何建议都请尽量落成可执行任务清单
```
