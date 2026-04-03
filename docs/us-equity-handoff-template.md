# US Equity Handoff Template

这份模板用于 Codex、Claude Code 和人工开发者之间的固定交接。

目标只有一个：
下一位接手的人不需要重新盘点整个仓库。

## 1. 交接头

```text
任务编号：
任务名称：
交接日期：
交接人：
接手人：
当前分支：
当前最新提交：
```

## 2. 本批目标

```text
这批任务的目标是什么：
本批不包含什么：
```

## 3. 修改文件列表

```text
1.
2.
3.
```

要求：

1. 只列本批真正相关文件
2. 标明新增、修改、迁移、测试

## 4. 已完成内容

```text
1.
2.
3.
```

要求：

1. 用结果描述，不要只写“改了某个类”
2. 如果有真实数据验证，写清楚 ticker 和接口

## 5. 未完成内容

```text
1.
2.
3.
```

要求：

1. 明确哪些只是“没做”
2. 明确哪些是“做了一半”
3. 明确哪些依赖上游规则或数据源

## 6. 测试结果

```text
命令：
结果：
是否通过：
```

建议至少写：

1. 定向测试命令
2. 全量测试命令
3. 是否有未覆盖风险

## 7. 真实验证结果

```text
服务地址：
验证接口：
关键返回：
```

建议写法：

1. `POST /v1/us-equities-admin/AAPL/sec-sync`
2. `POST /v1/us-equities-admin/AAPL/ir-sync`
3. `POST /v1/us-equities-admin/AAPL/standardize`
4. `GET /v1/us-equities-admin/AAPL/overview`
5. `GET /v1/us-equities/AAPL/data-quality`
6. `GET /v1/us-equities/AAPL/financial-quality`

## 8. 风险与注意事项

```text
1.
2.
3.
```

这里必须写清楚：

1. 是否还有 fallback
2. 是否有真实数据边界
3. 是否有 dirty worktree 不能碰的文件
4. 是否有端口、运行进程、数据库状态要注意

## 9. 下一步建议

```text
1.
2.
3.
```

要求：

1. 最多 3 条
2. 直接给任务编号
3. 按优先级排序

## 10. 推荐交接实例

```text
任务编号：US-19
任务名称：风险矩阵 PRD 化接线
交接日期：2026-03-31
交接人：Codex
接手人：Claude Code
当前分支：codex/java-backend-foundation
当前最新提交：6fc492a

本批目标：
- 把风险矩阵正式接到 wacc / scenario_weight / margin_of_safety
- 补 AAPL 结构化 FCFF 覆盖，减少 reverse DCF fallback

修改文件列表：
1. src/main/java/com/fairvalue/engine/us/...
2. src/main/java/com/fairvalue/engine/repository/...
3. src/test/java/com/fairvalue/engine/api/us/...
4. docs/us-equity-*.md

已完成内容：
1. 风险矩阵已落到估值主链路和 risk_scores
2. AAPL 的 reverse_dcf.structured_inputs 已从 false 变成 true
3. 37 个测试全部通过

未完成内容：
1. AAPL 的标准化财务层仍偏薄，主要靠 SEC profile 托底
2. Relative Valuation 仍不是 peer set 引擎
3. 价格主源仍不是 Longbridge

测试结果：
命令：./mvnw.cmd test
结果：37 tests passed
是否通过：是

真实验证结果：
服务地址：http://localhost:18080
验证接口：POST /v1/us-equities/AAPL/valuation/run
关键返回：structured_inputs=true, implied_expectation=aggressive

风险与注意事项：
1. 工作区是 dirty 的，不能整体提交
2. 只允许精确挑文件提交
3. CN / JP 的未提交改动不要顺手带进来

下一步建议：
1. US-22
2. US-23
3. US-24
```
