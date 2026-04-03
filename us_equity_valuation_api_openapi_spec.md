# 美股估值系统 API 设计文档 / OpenAPI 规范

## 1. 文档信息

- 文档名称：US Equity Valuation System API Design & OpenAPI Spec
- 文档版本：V1.0
- 适用范围：美股估值系统后端 API
- 目标读者：后端工程师、前端工程师、AI 编码助手、测试工程师、第三方接入方
- 输出目标：形成可直接开发、联调和对外开放的 API 规范

---

## 2. 设计目标

本 API 的目标不是单纯返回一个 fair value 数字，而是统一输出：
- 单股估值结果
- 批量估值结果
- 估值解释结果
- 历史估值序列
- 同行对比结果
- 情景分析结果
- 筛选结果
- 数据版本和置信度信息

设计原则：
1. REST 风格优先。
2. 所有接口返回 JSON。
3. 单股与批量接口字段结构尽量统一。
4. 结果分为 summary / decision / explanation 三层。
5. 每次估值运行必须有 `run_id` 和 `data_version`。
6. 必须支持 API Key 鉴权、限流和套餐控制。

---

## 3. 基础规范

### 3.1 Base URL

```text
Production: https://api.yourdomain.com
Staging:    https://staging-api.yourdomain.com
```

### 3.2 API Version

```text
/v1
```

### 3.3 Content Type

```http
Content-Type: application/json
Accept: application/json
```

### 3.4 鉴权

使用 API Key：

```http
Authorization: Bearer <API_KEY>
```

也可支持：

```http
X-API-Key: <API_KEY>
```

### 3.5 幂等性

对于批量接口和情景接口，建议支持：

```http
Idempotency-Key: <uuid>
```

---

## 4. 通用响应结构

### 4.1 成功响应

```json
{
  "success": true,
  "request_id": "req_01HXYZ...",
  "timestamp": "2026-04-02T12:00:00Z",
  "data": {}
}
```

### 4.2 失败响应

```json
{
  "success": false,
  "request_id": "req_01HXYZ...",
  "timestamp": "2026-04-02T12:00:00Z",
  "error": {
    "code": "INVALID_SYMBOL",
    "message": "The symbol is invalid or unsupported.",
    "details": {
      "symbol": "AAPLL"
    }
  }
}
```

### 4.3 分页响应

```json
{
  "success": true,
  "request_id": "req_01HXYZ...",
  "timestamp": "2026-04-02T12:00:00Z",
  "data": {
    "items": [],
    "pagination": {
      "page": 1,
      "page_size": 50,
      "total_items": 240,
      "total_pages": 5,
      "has_next": true
    }
  }
}
```

---

## 5. 核心数据对象定义

## 5.1 ValuationSummary

```json
{
  "market": "US",
  "symbol": "AAPL",
  "currency": "USD",
  "valuation_date": "2026-04-02",
  "price": 210.35,
  "fair_value_low": 176.20,
  "fair_value_mid": 198.60,
  "fair_value_high": 225.40,
  "blended_intrinsic_value": 198.60,
  "tradable_fair_value": 205.20,
  "margin_of_safety": -0.056,
  "upside_downside_pct": -0.0245,
  "valuation_status": "slightly_overvalued",
  "confidence_level": 0.82,
  "verdict": "hold",
  "run_id": "run_us_aapl_20260402_001",
  "data_version": "2026Q1"
}
```

## 5.2 ModelResult

```json
{
  "method": "dcf_fcff",
  "value": 205.10,
  "weight": 0.35,
  "selected": true,
  "applicable": true,
  "rationale": "适用于现金流稳定的大型科技股",
  "assumptions": {
    "revenue_cagr_5y": 0.08,
    "ebit_margin_target": 0.31,
    "wacc": 0.09,
    "terminal_growth": 0.025
  }
}
```

## 5.3 ScenarioResult

```json
{
  "scenario": "base",
  "probability": 0.55,
  "fair_value": 198.60,
  "drivers": {
    "revenue_growth": 0.08,
    "operating_margin": 0.31,
    "wacc": 0.09,
    "terminal_growth": 0.025
  }
}
```

## 5.4 RiskItem

```json
{
  "risk_key": "multiple_compression",
  "severity": "high",
  "probability": "medium",
  "impact_direction": "negative",
  "description": "当前估值对增长延续要求较高，若成长放缓可能导致估值压缩"
}
```

## 5.5 ExplanationBlock

```json
{
  "block_type": "executive_summary",
  "title": "Executive Summary",
  "content": "公司具备稳定现金流和强回购能力，但当前估值已经部分透支未来增长。"
}
```

---

## 6. 核心接口设计

## 6.1 单股估值接口

### Endpoint

```http
GET /v1/valuation/US/{symbol}
```

### 用途
返回单只美股的当前估值主结果。

### Path 参数
- `symbol`：股票代码，例如 `AAPL`

### Query 参数
- `view`：`summary` | `full`，默认 `summary`
- `include_models`：`true` | `false`
- `include_decision`：`true` | `false`
- `include_explanation`：`true` | `false`
- `force_refresh`：`true` | `false`

### 示例请求

```http
GET /v1/valuation/US/AAPL?view=full&include_models=true&include_decision=true&include_explanation=true
Authorization: Bearer <API_KEY>
```

### 示例响应

```json
{
  "success": true,
  "request_id": "req_001",
  "timestamp": "2026-04-02T12:00:00Z",
  "data": {
    "summary": {
      "market": "US",
      "symbol": "AAPL",
      "currency": "USD",
      "valuation_date": "2026-04-02",
      "price": 210.35,
      "fair_value_low": 176.20,
      "fair_value_mid": 198.60,
      "fair_value_high": 225.40,
      "blended_intrinsic_value": 198.60,
      "tradable_fair_value": 205.20,
      "margin_of_safety": -0.056,
      "upside_downside_pct": -0.0245,
      "valuation_status": "slightly_overvalued",
      "confidence_level": 0.82,
      "verdict": "hold",
      "run_id": "run_us_aapl_20260402_001",
      "data_version": "2026Q1"
    },
    "models": [],
    "decision": {
      "value_trap_flag": false,
      "implied_expectation": {
        "reverse_dcf_revenue_cagr": 0.095,
        "reverse_dcf_operating_margin": 0.32
      },
      "risk_matrix": []
    },
    "explanation": {
      "blocks": []
    }
  }
}
```

---

## 6.2 批量估值接口

### Endpoint

```http
POST /v1/valuation/batch
```

### 用途
一次性估值多只股票，适用于：
- 自选股监控
- 榜单生成
- 机构批处理
- 选股器后端

### 请求体

```json
{
  "market": "US",
  "symbols": ["AAPL", "MSFT", "GOOGL"],
  "view": "summary",
  "include_models": false,
  "force_refresh": false
}
```

### 响应体

```json
{
  "success": true,
  "request_id": "req_batch_001",
  "timestamp": "2026-04-02T12:01:00Z",
  "data": {
    "items": [
      {
        "symbol": "AAPL",
        "summary": {}
      },
      {
        "symbol": "MSFT",
        "summary": {}
      }
    ],
    "failed_symbols": [
      {
        "symbol": "INVALID",
        "error_code": "INVALID_SYMBOL",
        "message": "Unsupported symbol"
      }
    ]
  }
}
```

---

## 6.3 估值解释接口

### Endpoint

```http
GET /v1/valuation/US/{symbol}/explain
```

### 用途
返回估值解释结果，包括：
- 为什么选这些模型
- 为什么这些模型有当前权重
- 哪些因子驱动 fair value
- 哪些风险压低了置信度

### Query 参数
- `format`：`structured` | `report`
- `lang`：`zh-CN` | `en-US` | `ja-JP`

### 响应体

```json
{
  "success": true,
  "request_id": "req_explain_001",
  "timestamp": "2026-04-02T12:02:00Z",
  "data": {
    "symbol": "AAPL",
    "run_id": "run_us_aapl_20260402_001",
    "blocks": [
      {
        "block_type": "one_line_verdict",
        "title": "One-line Verdict",
        "content": "高质量龙头，但当前价格已接近中性偏贵区间。"
      },
      {
        "block_type": "valuation_breakdown",
        "title": "Valuation Breakdown",
        "content": "DCF 给出 205.1 美元，相对估值给出 192.8 美元。"
      }
    ]
  }
}
```

---

## 6.4 情景分析接口

### Endpoint

```http
POST /v1/valuation/scenario
```

### 用途
允许用户自定义关键估值参数，生成悲观/中性/乐观估值区间和敏感度分析。

### 请求体

```json
{
  "market": "US",
  "symbol": "AAPL",
  "assumptions": {
    "revenue_growth": 0.08,
    "operating_margin": 0.31,
    "wacc": 0.09,
    "terminal_growth": 0.025
  },
  "scenarios": [
    {
      "name": "bear",
      "revenue_growth": 0.05,
      "operating_margin": 0.28,
      "wacc": 0.10,
      "terminal_growth": 0.02
    },
    {
      "name": "base",
      "revenue_growth": 0.08,
      "operating_margin": 0.31,
      "wacc": 0.09,
      "terminal_growth": 0.025
    },
    {
      "name": "bull",
      "revenue_growth": 0.10,
      "operating_margin": 0.33,
      "wacc": 0.085,
      "terminal_growth": 0.03
    }
  ]
}
```

### 响应体

```json
{
  "success": true,
  "request_id": "req_scenario_001",
  "timestamp": "2026-04-02T12:03:00Z",
  "data": {
    "symbol": "AAPL",
    "results": [
      {
        "scenario": "bear",
        "fair_value": 176.20
      },
      {
        "scenario": "base",
        "fair_value": 198.60
      },
      {
        "scenario": "bull",
        "fair_value": 225.40
      }
    ],
    "sensitivity": {
      "wacc": [
        {"input": 0.085, "fair_value": 209.10},
        {"input": 0.09, "fair_value": 198.60},
        {"input": 0.095, "fair_value": 189.20}
      ]
    }
  }
}
```

---

## 6.5 历史估值接口

### Endpoint

```http
GET /v1/valuation/history/US/{symbol}
```

### 用途
返回历史价格与合理价值序列。

### Query 参数
- `range`：`1m` | `3m` | `6m` | `1y` | `3y` | `5y`
- `interval`：`day` | `week` | `month`

### 响应体

```json
{
  "success": true,
  "request_id": "req_history_001",
  "timestamp": "2026-04-02T12:04:00Z",
  "data": {
    "symbol": "AAPL",
    "series": [
      {
        "date": "2026-03-01",
        "price": 201.00,
        "fair_value_mid": 193.50,
        "fair_value_low": 171.80,
        "fair_value_high": 219.90
      }
    ]
  }
}
```

---

## 6.6 同行对比接口

### Endpoint

```http
GET /v1/peers/US/{symbol}
```

### 用途
返回可比公司列表及相对估值数据。

### Query 参数
- `mode`：`industry` | `custom`
- `limit`：默认 10

### 响应体

```json
{
  "success": true,
  "request_id": "req_peers_001",
  "timestamp": "2026-04-02T12:05:00Z",
  "data": {
    "symbol": "AAPL",
    "peer_group": [
      {
        "symbol": "MSFT",
        "company_name": "Microsoft Corp.",
        "pe_ttm": 31.2,
        "ev_ebitda_ttm": 22.5,
        "revenue_growth_fy1": 0.11
      }
    ],
    "industry_median": {
      "pe_ttm": 28.4,
      "ev_ebitda_ttm": 18.7
    }
  }
}
```

---

## 6.7 筛选器接口

### Endpoint

```http
POST /v1/screener/valuation
```

### 用途
按估值偏离、财务质量、收益率和风险标签进行筛选。

### 请求体

```json
{
  "market": "US",
  "filters": {
    "undervalued_pct_min": 0.20,
    "roe_min": 0.12,
    "fcf_positive": true,
    "confidence_min": 0.70,
    "market_cap_min": 1000000000
  },
  "sort": {
    "field": "undervalued_pct",
    "direction": "desc"
  },
  "page": 1,
  "page_size": 50
}
```

### 响应体

```json
{
  "success": true,
  "request_id": "req_screener_001",
  "timestamp": "2026-04-02T12:06:00Z",
  "data": {
    "items": [
      {
        "symbol": "ABC",
        "price": 45.0,
        "fair_value_mid": 58.0,
        "undervalued_pct": 0.289,
        "confidence_level": 0.78,
        "valuation_status": "undervalued"
      }
    ],
    "pagination": {
      "page": 1,
      "page_size": 50,
      "total_items": 320,
      "total_pages": 7,
      "has_next": true
    }
  }
}
```

---

## 7. 任务型接口建议

## 7.1 手动重算接口

```http
POST /v1/valuation/US/{symbol}/run
```

请求体：

```json
{
  "force_refresh": true,
  "recompute_models": true,
  "recompute_risk": true
}
```

用途：
- 财报更新后手动触发重算
- 内部管理后台使用
- 高级套餐用户使用

## 7.2 自选监控刷新接口

```http
POST /v1/watchlist/revalue
```

请求体：

```json
{
  "symbols": ["AAPL", "MSFT", "NVDA"]
}
```

---

## 8. 状态码设计

### 8.1 标准状态码
- `200 OK`：成功
- `201 Created`：成功创建任务
- `400 Bad Request`：参数错误
- `401 Unauthorized`：未鉴权
- `403 Forbidden`：权限不足
- `404 Not Found`：资源不存在
- `409 Conflict`：幂等冲突
- `422 Unprocessable Entity`：参数合法但无法执行估值
- `429 Too Many Requests`：触发限流
- `500 Internal Server Error`：系统错误
- `503 Service Unavailable`：依赖源暂不可用

### 8.2 业务错误码建议
- `INVALID_SYMBOL`
- `UNSUPPORTED_MARKET`
- `DATA_NOT_READY`
- `FINANCIALS_MISSING`
- `PRICE_NOT_AVAILABLE`
- `MODEL_NOT_APPLICABLE`
- `PLAN_LIMIT_EXCEEDED`
- `RATE_LIMIT_EXCEEDED`
- `SCENARIO_INPUT_INVALID`
- `RECOMPUTE_IN_PROGRESS`

---

## 9. 限流与套餐控制

### 9.1 维度
- 每分钟请求数
- 每日请求数
- 批量 symbols 上限
- 历史接口可用范围
- explain / scenario 是否开放
- Webhook 是否开放

### 9.2 套餐建议

#### Free
- 仅 summary
- 限少量每日调用
- 不开放 batch / explain / scenario

#### Pro
- 开放单股 full view
- explain
- history
- 较高限流

#### Business
- batch
- screener
- scenario
- 更高频率
- 可商业展示

#### Enterprise
- 白标
- SLA
- Webhook
- 高配额
- 商业再分发许可

---

## 10. OpenAPI 3.1 草案

下面给出可直接作为后端生成器输入起点的 OpenAPI 草案。

```yaml
openapi: 3.1.0
info:
  title: US Equity Valuation System API
  version: 1.0.0
  description: >-
    美股估值系统 API，提供单股估值、批量估值、解释、历史、同行、筛选和情景分析能力。
servers:
  - url: https://api.yourdomain.com
    description: Production
  - url: https://staging-api.yourdomain.com
    description: Staging
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: APIKey
  schemas:
    ValuationSummary:
      type: object
      properties:
        market:
          type: string
          example: US
        symbol:
          type: string
          example: AAPL
        currency:
          type: string
          example: USD
        valuation_date:
          type: string
          format: date
        price:
          type: number
        fair_value_low:
          type: number
        fair_value_mid:
          type: number
        fair_value_high:
          type: number
        blended_intrinsic_value:
          type: number
        tradable_fair_value:
          type: number
        margin_of_safety:
          type: number
        upside_downside_pct:
          type: number
        valuation_status:
          type: string
        confidence_level:
          type: number
        verdict:
          type: string
        run_id:
          type: string
        data_version:
          type: string
    ErrorResponse:
      type: object
      properties:
        success:
          type: boolean
          example: false
        request_id:
          type: string
        timestamp:
          type: string
          format: date-time
        error:
          type: object
          properties:
            code:
              type: string
            message:
              type: string
            details:
              type: object
paths:
  /v1/valuation/US/{symbol}:
    get:
      summary: Get valuation by symbol
      security:
        - bearerAuth: []
      parameters:
        - name: symbol
          in: path
          required: true
          schema:
            type: string
        - name: view
          in: query
          required: false
          schema:
            type: string
            enum: [summary, full]
      responses:
        '200':
          description: Success
        '400':
          description: Bad Request
        '401':
          description: Unauthorized
        '404':
          description: Not Found
  /v1/valuation/batch:
    post:
      summary: Batch valuation
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
  /v1/valuation/US/{symbol}/explain:
    get:
      summary: Explain valuation
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
  /v1/valuation/scenario:
    post:
      summary: Scenario valuation
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
  /v1/valuation/history/US/{symbol}:
    get:
      summary: Historical valuation series
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
  /v1/peers/US/{symbol}:
    get:
      summary: Peer comparison
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
  /v1/screener/valuation:
    post:
      summary: Valuation screener
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Success
security:
  - bearerAuth: []
```

---

## 11. 后端实现建议

### 11.1 FastAPI 路由建议

```text
api/
  valuation.py
  scenario.py
  history.py
  peers.py
  screener.py
  auth.py
```

### 11.2 Service 层建议

```text
services/
  valuation_service.py
  valuation_run_service.py
  explanation_service.py
  scenario_service.py
  peer_service.py
  screener_service.py
```

### 11.3 Pydantic 模型建议

```text
schemas/
  common.py
  valuation.py
  scenario.py
  peers.py
  screener.py
  errors.py
```

---

## 12. 测试建议

优先覆盖：
1. 正常 symbol 返回
2. 无效 symbol 返回
3. 财务缺失但能部分输出
4. 批量接口部分成功部分失败
5. 高并发限流
6. 情景分析边界值
7. history 范围和分页
8. 不同套餐权限差异

---

## 13. 下一步建议

这份 API 文档写完后，最适合继续补的两份文档是：

1. **数据库表结构 SQL 设计文档**
2. **FastAPI 后端模块设计文档**

如果继续往工程落地走，建议顺序是：
- 先定 Pydantic schema
- 再定 OpenAPI
- 再定 SQL 表结构
- 最后让 Codex / Claude 生成 FastAPI 路由和 service skeleton

---

## 14. 一句话总结

这份 API 规范的目标，不是给前端返回一个“目标价”，而是提供一套 **统一、可解释、可扩展、可商业化的美股估值 API 协议**，使网站、筛选器、自选监控和第三方开发者都能基于同一套结构化结果进行复用。
