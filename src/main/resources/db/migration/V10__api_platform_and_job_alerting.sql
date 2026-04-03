CREATE TABLE IF NOT EXISTS fairvalue.api_clients (
    id BIGSERIAL PRIMARY KEY,
    client_key TEXT NOT NULL UNIQUE,
    client_name TEXT NOT NULL,
    plan_code TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    public_client BOOLEAN NOT NULL DEFAULT FALSE,
    requests_per_minute INTEGER NOT NULL DEFAULT 120,
    entitlements_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_clients_public_enabled
    ON fairvalue.api_clients (public_client, enabled);

CREATE TABLE IF NOT EXISTS fairvalue.api_usage_daily (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES fairvalue.api_clients(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    route_key TEXT NOT NULL,
    http_method TEXT NOT NULL,
    status_bucket TEXT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, usage_date, route_key, http_method, status_bucket)
);

CREATE INDEX IF NOT EXISTS idx_api_usage_daily_client_date
    ON fairvalue.api_usage_daily (client_id, usage_date DESC);

INSERT INTO fairvalue.api_clients (
    client_key,
    client_name,
    plan_code,
    enabled,
    public_client,
    requests_per_minute,
    entitlements_json,
    metadata_json
)
VALUES (
    'fv-browser-dev-key',
    'Fairvalue Browser Dev Client',
    'developer_browser',
    TRUE,
    TRUE,
    180,
    '[
      "market.read",
      "market.screen",
      "us.read",
      "us.valuation.run",
      "us.admin.read",
      "us.admin.write",
      "platform.read"
    ]'::jsonb,
    '{"environment":"local","note":"Public browser development client. Use separate scoped keys in production."}'::jsonb
)
ON CONFLICT (client_key) DO UPDATE
SET client_name = EXCLUDED.client_name,
    plan_code = EXCLUDED.plan_code,
    enabled = EXCLUDED.enabled,
    public_client = EXCLUDED.public_client,
    requests_per_minute = EXCLUDED.requests_per_minute,
    entitlements_json = EXCLUDED.entitlements_json,
    metadata_json = EXCLUDED.metadata_json,
    updated_at = NOW();

INSERT INTO fairvalue.api_clients (
    client_key,
    client_name,
    plan_code,
    enabled,
    public_client,
    requests_per_minute,
    entitlements_json,
    metadata_json
)
VALUES (
    'fv-service-admin-key',
    'Fairvalue Service Admin Client',
    'internal_service',
    TRUE,
    FALSE,
    600,
    '[
      "market.read",
      "market.screen",
      "us.read",
      "us.valuation.run",
      "us.admin.read",
      "us.admin.write",
      "platform.read"
    ]'::jsonb,
    '{"environment":"local","note":"Internal service key for backend automation and ops."}'::jsonb
)
ON CONFLICT (client_key) DO UPDATE
SET client_name = EXCLUDED.client_name,
    plan_code = EXCLUDED.plan_code,
    enabled = EXCLUDED.enabled,
    public_client = EXCLUDED.public_client,
    requests_per_minute = EXCLUDED.requests_per_minute,
    entitlements_json = EXCLUDED.entitlements_json,
    metadata_json = EXCLUDED.metadata_json,
    updated_at = NOW();
