SET search_path TO fairvalue, public;

ALTER TABLE valuation_latest_snapshot
    ADD COLUMN IF NOT EXISTS price_as_of DATE,
    ADD COLUMN IF NOT EXISTS price_freshness_days INTEGER,
    ADD COLUMN IF NOT EXISTS price_source_type TEXT,
    ADD COLUMN IF NOT EXISTS rankable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS valuation_status TEXT,
    ADD COLUMN IF NOT EXISTS exclusion_reason TEXT,
    ADD COLUMN IF NOT EXISTS industry_match_source TEXT,
    ADD COLUMN IF NOT EXISTS industry_match_confidence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS industry_fallback_used BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_valuation_latest_snapshot_rankable
    ON valuation_latest_snapshot (market, rankable, confidence_level DESC, upside_pct DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_latest_snapshot_exclusion_reason
    ON valuation_latest_snapshot (market, exclusion_reason);

INSERT INTO sector_template_config (
    sector_template,
    primary_methods_json,
    primary_metrics_json,
    forbidden_methods_json,
    default_weights_json,
    risk_notes_json,
    safety_margin_rule_json,
    updated_at
)
VALUES (
    'us_managed_care',
    '["DCF","RELATIVE_VALUATION","HISTORICAL_MULTIPLE","REVERSE_DCF"]'::jsonb,
    '["medical_care_ratio","revenue_growth","operating_cash_flow","regulatory_exposure"]'::jsonb,
    '[]'::jsonb,
    '{"DCF":0.20,"RELATIVE_VALUATION":0.35,"HISTORICAL_MULTIPLE":0.25,"REVERSE_DCF":0.20}'::jsonb,
    '["Managed care names require payer-specific peer sets, conservative margin assumptions, and extra caution on stale or fallback pricing."]'::jsonb,
    '{"default_margin_of_safety":0.24,"quality_bands":{"high":0.18,"medium":0.24,"low":0.32}}'::jsonb,
    NOW()
)
ON CONFLICT (sector_template) DO UPDATE SET
    primary_methods_json = EXCLUDED.primary_methods_json,
    primary_metrics_json = EXCLUDED.primary_metrics_json,
    forbidden_methods_json = EXCLUDED.forbidden_methods_json,
    default_weights_json = EXCLUDED.default_weights_json,
    risk_notes_json = EXCLUDED.risk_notes_json,
    safety_margin_rule_json = EXCLUDED.safety_margin_rule_json,
    updated_at = NOW();

INSERT INTO valuation_parameter_set (
    security_id,
    sector_template,
    parameter_type,
    parameter_key,
    parameter_value,
    effective_from,
    effective_to,
    source_note,
    created_at
)
VALUES
    (NULL, 'us_managed_care', 'wacc', 'base', '{"value":0.090}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'wacc', 'floor', '{"value":0.082}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'wacc', 'ceiling', '{"value":0.110}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'terminal_growth', 'base', '{"value":0.022}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'terminal_growth', 'floor', '{"value":0.018}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'terminal_growth', 'ceiling', '{"value":0.028}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'dcf', 'forecast_years', '{"value":5}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'dcf', 'initial_growth_cap', '{"value":0.10}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'dcf', 'growth_floor', '{"value":0.02}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'dcf', 'target_fcf_margin', '{"value":0.06}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD DCF model.', NOW()),
    (NULL, 'us_managed_care', 'relative', 'target_pe', '{"value":15.0}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD relative valuation.', NOW()),
    (NULL, 'us_managed_care', 'relative', 'target_ev_ebitda', '{"value":11.5}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD relative valuation.', NOW()),
    (NULL, 'us_managed_care', 'reverse_dcf', 'implied_growth_floor', '{"value":-0.03}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD reverse DCF.', NOW()),
    (NULL, 'us_managed_care', 'reverse_dcf', 'implied_growth_ceiling', '{"value":0.18}'::jsonb, CURRENT_DATE, NULL, 'Seeded managed care template default for PRD reverse DCF.', NOW())
ON CONFLICT DO NOTHING;
