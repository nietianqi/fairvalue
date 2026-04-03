# Frontend Handoff 2026-04-02

这份 handoff 给 Claude Code 和后续继续接前端的人。

## 1. 本轮完成

### 1.1 新增页面

1. [valuation-screener.html](F:/fairvalue/src/main/resources/static/valuation-screener.html)
2. [valuation-screener.css](F:/fairvalue/src/main/resources/static/assets/valuation-screener.css)
3. [valuation-screener.js](F:/fairvalue/src/main/resources/static/assets/valuation-screener.js)

这个页面现在是一个真正可用的全局筛选工作台，直接调用：

- `POST /v1/screener/valuation`

当前支持：

1. 市场选择：`US / CN / JP / HK`
2. 低估幅度
3. ROE
4. PB
5. 股息率
6. 正自由现金流过滤
7. 结果上限

### 1.2 升级页面

1. [us-equities-admin.html](F:/fairvalue/src/main/resources/static/us-equities-admin.html)
2. [us-admin-console.css](F:/fairvalue/src/main/resources/static/assets/us-admin-console.css)
3. [us-admin-console.js](F:/fairvalue/src/main/resources/static/assets/us-admin-console.js)
4. [cn-undervalued-stocks.html](F:/fairvalue/src/main/resources/static/cn-undervalued-stocks.html)
5. [cn-undervalued.css](F:/fairvalue/src/main/resources/static/assets/cn-undervalued.css)
6. [us-stock-detail.html](F:/fairvalue/src/main/resources/static/us-stock-detail.html)
7. [us-stock-detail.css](F:/fairvalue/src/main/resources/static/assets/us-stock-detail.css)

## 2. 页面现在能做什么

### 2.1 全球榜单页

- 地址：[http://localhost:18080/cn-undervalued-stocks.html](http://localhost:18080/cn-undervalued-stocks.html)

已经具备：

1. 全球榜单和 discovery 切换
2. `US` 股票行跳到详情页
3. 新增顶部入口：
   - 筛选器
   - 美股详情示例
   - US Admin

### 2.2 全球筛选页

- 地址：[http://localhost:18080/valuation-screener.html](http://localhost:18080/valuation-screener.html)

已经具备：

1. 发起统一 screener 请求
2. 显示候选数量、平均上行、平均置信度
3. 结果表支持 `US` ticker 跳详情页
4. 如果结果出现 `synthetic` 数据版本，会给出提示

### 2.3 美股详情页

- 地址：[http://localhost:18080/us-stock-detail.html?ticker=AAPL](http://localhost:18080/us-stock-detail.html?ticker=AAPL)

已经具备：

1. Hero band
2. Overview / Valuation / History / Peers / Risk
3. 顶部新增：
   - 全球筛选器入口
   - US Admin 入口

### 2.4 美股后台页

- 地址：[http://localhost:18080/us-equities-admin.html](http://localhost:18080/us-equities-admin.html)

现在已不只是“最后一次 JSON 返回”，而是：

1. Source status 卡片
2. `valuation_jobs` summary 卡片
3. ticker recent jobs 表
4. global recent jobs 表
5. overview 快照卡片
6. documents / raw facts / standardized / derived / quality / audit 表
7. 操作按钮：
   - Market Sync
   - SEC Sync
   - IR Sync
   - Standardize
   - 手动重估值
   - Universe Sync

## 3. 已验证

### 3.1 页面可访问

1. `GET /valuation-screener.html` -> `200`
2. `GET /us-equities-admin.html` -> `200`
3. `GET /assets/us-admin-console.js` -> `200`
4. `GET /assets/us-admin-console.css` -> `200`

### 3.2 关键接口 live

1. `POST /v1/screener/valuation`
   - 已返回真实结果
2. `GET /v1/us-equities-admin/valuation-jobs/summary`
   - 已返回任务统计
3. `GET /v1/us-equities-admin/AAPL/source-status`
   - 已返回 `fred / longbridge / damodaran / simfin`

## 4. 当前还没收口的前端问题

1. `US peers` 仍可能返回空集合，所以详情页同行页可能显示“暂无数据”
2. `US rankings` 虽然前端已经可回退，但后端仍不是全市场正式榜单
3. `CN / JP / HK` 详情页还没有正式页面
4. `valuation_jobs` 页面目前还是嵌在 `US admin`，还不是独立监控页
5. 当前前端没有统一 toast / modal / auth / api-envelope 层

## 5. 建议 Claude Code 下一步优先审

1. 这 4 个页面是否已经形成合理的信息架构
2. `US admin` 是否还应拆成：
   - source status
   - valuation jobs
   - ingestion overview
3. `valuation-screener` 是否需要改成更偏产品页而不是控制台页
4. `CN / JP / HK` 应优先补哪一个详情页骨架
5. 顶部导航是否应该统一成一个 shared navigation pattern
