SET search_path TO fairvalue, public;

INSERT INTO source_registry (source_name, source_tier, source_type, priority_rank, is_enabled, description)
VALUES
    ('stooq', 'TIER_2', 'market_data', 4, TRUE, 'Current fallback US market data source for daily quote and OHLCV persistence.')
ON CONFLICT (source_name) DO UPDATE SET
    source_tier = EXCLUDED.source_tier,
    source_type = EXCLUDED.source_type,
    priority_rank = EXCLUDED.priority_rank,
    is_enabled = EXCLUDED.is_enabled,
    description = EXCLUDED.description,
    updated_at = NOW();

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
VALUES
    (
        'us_general_quality',
        '["DCF","HISTORICAL_MULTIPLE","RELATIVE_VALUATION","REVERSE_DCF"]'::jsonb,
        '["revenue_growth","fcf_margin","roe","net_debt_to_ebitda"]'::jsonb,
        '[]'::jsonb,
        '{"DCF":0.30,"HISTORICAL_MULTIPLE":0.25,"RELATIVE_VALUATION":0.25,"REVERSE_DCF":0.20}'::jsonb,
        '["Use general quality template when no sector-specialized framework is clearly dominant."]'::jsonb,
        '{"default_margin_of_safety":0.25,"quality_bands":{"high":0.18,"medium":0.25,"low":0.32}}'::jsonb,
        NOW()
    ),
    (
        'us_financial_quality',
        '["PTBV","DDM","RELATIVE_VALUATION","REVERSE_DCF"]'::jsonb,
        '["roe","capital_ratio","book_value_growth","underwriting_quality"]'::jsonb,
        '["DCF"]'::jsonb,
        '{"PTBV":0.35,"DDM":0.20,"RELATIVE_VALUATION":0.25,"REVERSE_DCF":0.20}'::jsonb,
        '["Prefer balance-sheet and capital-return anchors for non-bank financials."]'::jsonb,
        '{"default_margin_of_safety":0.24,"quality_bands":{"high":0.18,"medium":0.26,"low":0.34}}'::jsonb,
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_data_quality_audit_security_date
    ON data_quality_audit (security_id, audit_date);
