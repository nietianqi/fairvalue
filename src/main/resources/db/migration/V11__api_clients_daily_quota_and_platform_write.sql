SET search_path TO fairvalue, public;

ALTER TABLE fairvalue.api_clients
    ADD COLUMN IF NOT EXISTS daily_quota BIGINT NOT NULL DEFAULT 25000;

CREATE INDEX IF NOT EXISTS idx_api_clients_plan_enabled
    ON fairvalue.api_clients (plan_code, enabled);

UPDATE fairvalue.api_clients
SET daily_quota = 25000,
    entitlements_json = CASE
        WHEN client_key = 'fv-browser-dev-key'
            THEN '[
              "market.read",
              "market.screen",
              "us.read",
              "us.valuation.run",
              "us.admin.read",
              "us.admin.write",
              "platform.read"
            ]'::jsonb
        WHEN client_key = 'fv-service-admin-key'
            THEN '[
              "market.read",
              "market.screen",
              "us.read",
              "us.valuation.run",
              "us.admin.read",
              "us.admin.write",
              "platform.read",
              "platform.write"
            ]'::jsonb
        ELSE entitlements_json
    END,
    updated_at = NOW()
WHERE client_key IN ('fv-browser-dev-key', 'fv-service-admin-key');

UPDATE fairvalue.api_clients
SET daily_quota = 200000,
    updated_at = NOW()
WHERE client_key = 'fv-service-admin-key';
