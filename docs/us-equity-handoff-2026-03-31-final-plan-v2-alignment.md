# US Equity Handoff 2026-03-31 Final Plan V2 Alignment

任务编号：文档重梳理  
任务名称：基于终版方案 v2 重新梳理项目与代码  
交接日期：2026-03-31  
交接人：Codex  
接手人：Claude Code / 后续开发者  
当前分支：`codex/java-backend-foundation`  
当前最新提交：`6fc492a`

## 1. 本批目标

这批任务的目标：

1. 参考 [美股估值系统_完整终版方案_v2.docx](F:/fairvalue/美股估值系统_完整终版方案_v2.docx)，重新梳理美股项目与当前代码。
2. 把“终版产品规则”和“当前 Java 代码实现”分开写清楚。
3. 让 Claude Code 后续接手时，先对齐规则和差距，再决定要不要让 Codex 落代码。

这批不包含什么：

1. 不重写 Java 代码主链路。
2. 不切换技术栈到 Python。
3. 不碰 CN / JP / 前端等无关 dirty 文件。

## 2. 本批修改文件

1. [美国股票估值.md](F:/fairvalue/美国股票估值.md)
   - 修改
   - 对齐终版数据源、方法体系、输出结构、Java 落地边界
2. [docs/us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
   - 新增
   - 终版 v2 与当前 Java 代码映射总表
3. [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
   - 修改
   - 把终版对齐文档纳入任务基线
4. [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)
   - 修改
   - 把终版差距重新写清楚
5. [docs/claude-code-collaboration-guide.md](F:/fairvalue/docs/claude-code-collaboration-guide.md)
   - 修改
   - 明确 Claude Code 需要先读对齐文档，且不要把终版方案里的 Python 建议理解成重写要求

## 3. 已完成内容

1. 已把美股终版方案 v2 的核心要求重新压缩成当前项目可执行基线：
   - 生产版数据源：`SEC + 公司 IR + 长桥 + FRED + Damodaran`
   - Always-On 4 方法：`DCF / Historical / Relative / Reverse DCF`
   - 输出 3 层：`Summary / Decision / Explanation`
   - Explanation 12 个固定 blocks
2. 已把当前 Java 代码按六层架构重新映射清楚：
   - L1 原始事实层
   - L2 标准化财务层
   - L3 市场与宏观层
   - L4 估值计算层
   - L5 风险修正层
   - L6 解释输出层
3. 已明确写出当前和终版的主要差距：
   - 价格主源还不是 Longbridge
   - FRED / Damodaran 未正式接入
   - Relative 还不是 peer set
   - Historical 还不是正式历史区间引擎
   - AAPL 标准化财务覆盖仍偏薄
   - Explanation 12 blocks 还未完全标准化
4. 已明确写出工程边界：
   - 终版原文虽提 Python + FastAPI
   - 但当前项目继续沿用 Java + Spring Boot
   - 正确方向是“按层补齐”，不是“技术栈重写”

## 4. 未完成内容

1. 这批是文档重梳理，不包含新的 Java 代码实现。
2. `Longbridge / FRED / Damodaran` 仍未实际接入代码主链路。
3. `Relative Valuation peer set` 规则仍需 Claude Code 先出规则稿。
4. `Summary / Decision / Explanation` 的完整字段契约仍需要继续细化。

## 5. 当前建议 Claude Code 先做什么

### 第一优先级

1. 阅读：
   - [美国股票估值.md](F:/fairvalue/美国股票估值.md)
   - [docs/us-equity-final-plan-v2-alignment.md](F:/fairvalue/docs/us-equity-final-plan-v2-alignment.md)
   - [docs/us-equity-agent-task-list.md](F:/fairvalue/docs/us-equity-agent-task-list.md)
   - [docs/us-equity-progress.md](F:/fairvalue/docs/us-equity-progress.md)

### 第二优先级

2. 输出 `Longbridge / FRED / Damodaran` 接入规则稿：
   - 字段映射
   - 缓存策略
   - 回退规则
   - API 契约
   - 验收标准

### 第三优先级

3. 输出 `Relative Valuation peer set` 规则稿：
   - peer set 选取规则
   - 行业层级
   - 排除条件
   - 默认倍数
   - 验收方式

### 第四优先级

4. 复审 `Summary / Decision / Explanation` 3 层和 12 个 fixed blocks 的字段定义。

## 6. 风险与注意事项

1. 工作区当前仍是 dirty 的，包含大量本轮之外的未提交文件。
2. 后续提交时必须精确挑文件，不能整体提交。
3. Claude Code 不要顺手修改：
   - CN / JP 代码
   - 前端榜单文件
   - 无关日志和运行文件
4. 终版方案里提到 Python，并不意味着当前项目要重写。

## 7. 一句话交接

**请把终版方案 v2 当成产品与规则基线，把当前 Java 项目当成已跑通的工程底座；接下来要做的是补齐主数据源、补深方法和输出，而不是推翻重写。**
