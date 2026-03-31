SET search_path TO fairvalue, public;

INSERT INTO source_registry (source_name, source_tier, source_type, priority_rank, is_enabled, description)
VALUES
    ('fred', 'TIER_1', 'macro', 5, TRUE, 'Primary macro source for risk-free rate and policy-rate inputs.'),
    ('damodaran', 'TIER_1', 'macro', 6, TRUE, 'Primary valuation parameter source for ERP, industry beta, PE and EV/EBITDA anchors.')
ON CONFLICT (source_name) DO UPDATE SET
    source_tier = EXCLUDED.source_tier,
    source_type = EXCLUDED.source_type,
    priority_rank = EXCLUDED.priority_rank,
    is_enabled = EXCLUDED.is_enabled,
    description = EXCLUDED.description,
    updated_at = NOW();
