SET search_path TO fairvalue, public;

INSERT INTO source_registry (source_name, source_tier, source_type, priority_rank, is_enabled, description)
VALUES
    ('sec_edgar', 'TIER_1', 'filing', 1, TRUE, 'Primary source for filings, financial statements, governance and share-count data.'),
    ('company_ir', 'TIER_1', 'ir', 2, TRUE, 'Primary supplemental source for guidance, investor presentations and management commentary.'),
    ('longbridge_api', 'TIER_1', 'market_data', 3, TRUE, 'Primary market data source for price, OHLCV and vendor multiples in MVP.')
ON CONFLICT (source_name) DO UPDATE SET
    source_tier = EXCLUDED.source_tier,
    source_type = EXCLUDED.source_type,
    priority_rank = EXCLUDED.priority_rank,
    is_enabled = EXCLUDED.is_enabled,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO valuation_method_catalog (method_name, method_group, is_enabled, description)
VALUES
    ('DCF', 'intrinsic', TRUE, 'Discounted cash flow / FCFF valuation.'),
    ('HISTORICAL_MULTIPLE', 'market_anchor', TRUE, 'Historical valuation range and percentile regression.'),
    ('RELATIVE_VALUATION', 'market_anchor', TRUE, 'Peer multiple based valuation.'),
    ('REVERSE_DCF', 'expectation', TRUE, 'Implied growth and margin expectation from current price.'),
    ('DDM', 'income', TRUE, 'Dividend discount model for yield and payout oriented companies.'),
    ('PTBV', 'financials', TRUE, 'Price to tangible book and ROE-COE style framework.'),
    ('SOTP', 'special_situations', TRUE, 'Sum of the parts valuation for conglomerates and holdcos.'),
    ('NAV', 'special_situations', TRUE, 'Net asset value anchor.'),
    ('LBO_FLOOR', 'downside', TRUE, 'Private market style downside floor.'),
    ('RNPV', 'biotech', TRUE, 'Risk-adjusted NPV for clinical or binary outcome assets.')
ON CONFLICT (method_name) DO UPDATE SET
    method_group = EXCLUDED.method_group,
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
        'us_tech_compounder',
        '["DCF","HISTORICAL_MULTIPLE","RELATIVE_VALUATION","REVERSE_DCF"]'::jsonb,
        '["revenue_growth","fcf_margin","roe","net_cash_to_market_cap"]'::jsonb,
        '[]'::jsonb,
        '{"DCF":0.35,"HISTORICAL_MULTIPLE":0.20,"RELATIVE_VALUATION":0.25,"REVERSE_DCF":0.20}'::jsonb,
        '["Monitor multiple compression risk and SBC dilution."]'::jsonb,
        '{"default_margin_of_safety":0.20,"quality_bands":{"high":0.18,"medium":0.25,"low":0.32}}'::jsonb,
        NOW()
    ),
    (
        'us_hypergrowth_saas',
        '["RELATIVE_VALUATION","DCF","REVERSE_DCF","HISTORICAL_MULTIPLE"]'::jsonb,
        '["ev_revenue","ev_gross_profit","rule_of_40","nrr"]'::jsonb,
        '["DDM","PTBV"]'::jsonb,
        '{"RELATIVE_VALUATION":0.35,"DCF":0.25,"REVERSE_DCF":0.20,"HISTORICAL_MULTIPLE":0.20}'::jsonb,
        '["Demand higher margin of safety when growth visibility and SBC quality are weak."]'::jsonb,
        '{"default_margin_of_safety":0.30,"quality_bands":{"high":0.25,"medium":0.32,"low":0.40}}'::jsonb,
        NOW()
    ),
    (
        'us_cyclical',
        '["HISTORICAL_MULTIPLE","RELATIVE_VALUATION","DCF","REVERSE_DCF"]'::jsonb,
        '["mid_cycle_ebitda","inventory_days","capacity_utilization"]'::jsonb,
        '[]'::jsonb,
        '{"HISTORICAL_MULTIPLE":0.30,"RELATIVE_VALUATION":0.30,"DCF":0.20,"REVERSE_DCF":0.20}'::jsonb,
        '["Do not use peak earnings as a stable anchor."]'::jsonb,
        '{"default_margin_of_safety":0.35,"quality_bands":{"high":0.28,"medium":0.35,"low":0.45}}'::jsonb,
        NOW()
    ),
    (
        'us_bank',
        '["PTBV","DDM","RELATIVE_VALUATION","REVERSE_DCF"]'::jsonb,
        '["roe","cet1","nim","efficiency_ratio"]'::jsonb,
        '["DCF"]'::jsonb,
        '{"PTBV":0.35,"DDM":0.25,"RELATIVE_VALUATION":0.20,"REVERSE_DCF":0.20}'::jsonb,
        '["Credit costs and funding pressure should flow into risk adjustments, not just commentary."]'::jsonb,
        '{"default_margin_of_safety":0.25,"quality_bands":{"high":0.20,"medium":0.28,"low":0.35}}'::jsonb,
        NOW()
    ),
    (
        'us_reit',
        '["DDM","RELATIVE_VALUATION","NAV","REVERSE_DCF"]'::jsonb,
        '["affo","nav","dividend_yield","occupancy"]'::jsonb,
        '["PTBV"]'::jsonb,
        '{"DDM":0.30,"RELATIVE_VALUATION":0.30,"NAV":0.20,"REVERSE_DCF":0.20}'::jsonb,
        '["Use AFFO and NAV anchors instead of GAAP net income."]'::jsonb,
        '{"default_margin_of_safety":0.22,"quality_bands":{"high":0.18,"medium":0.24,"low":0.30}}'::jsonb,
        NOW()
    ),
    (
        'us_biotech',
        '["RNPV","RELATIVE_VALUATION","REVERSE_DCF"]'::jsonb,
        '["cash_runway","pipeline_value","binary_event_risk"]'::jsonb,
        '["DDM","PTBV"]'::jsonb,
        '{"RNPV":0.50,"RELATIVE_VALUATION":0.25,"REVERSE_DCF":0.25}'::jsonb,
        '["Binary event risk should primarily change scenario weights rather than simply widen the DCF band."]'::jsonb,
        '{"default_margin_of_safety":0.40,"quality_bands":{"high":0.35,"medium":0.45,"low":0.55}}'::jsonb,
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

INSERT INTO security_master (
    ticker,
    symbol_full,
    company_name,
    exchange,
    currency,
    sector,
    industry,
    company_type,
    sector_template,
    country,
    is_active
)
VALUES
    ('AAPL', 'AAPL.US', 'Apple Inc.', 'NASDAQ', 'USD', 'Technology', 'Consumer Electronics', 'compounder', 'us_tech_compounder', 'US', TRUE),
    ('MSFT', 'MSFT.US', 'Microsoft Corporation', 'NASDAQ', 'USD', 'Technology', 'Software Infrastructure', 'compounder', 'us_tech_compounder', 'US', TRUE),
    ('NVDA', 'NVDA.US', 'NVIDIA Corporation', 'NASDAQ', 'USD', 'Technology', 'Semiconductors', 'profitable_growth', 'us_tech_compounder', 'US', TRUE)
ON CONFLICT (ticker) DO UPDATE SET
    symbol_full = EXCLUDED.symbol_full,
    company_name = EXCLUDED.company_name,
    exchange = EXCLUDED.exchange,
    currency = EXCLUDED.currency,
    sector = EXCLUDED.sector,
    industry = EXCLUDED.industry,
    company_type = EXCLUDED.company_type,
    sector_template = EXCLUDED.sector_template,
    country = EXCLUDED.country,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

INSERT INTO security_identifier_map (
    security_id,
    source_name,
    source_symbol,
    exchange_code,
    is_primary,
    created_at,
    updated_at
)
SELECT id, 'longbridge_api', symbol_full, exchange, TRUE, NOW(), NOW()
FROM security_master
WHERE ticker IN ('AAPL', 'MSFT', 'NVDA')
ON CONFLICT (source_name, source_symbol) WHERE source_symbol IS NOT NULL DO NOTHING;
