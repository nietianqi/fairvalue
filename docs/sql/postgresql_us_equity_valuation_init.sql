BEGIN;

CREATE SCHEMA IF NOT EXISTS fairvalue;
SET search_path TO fairvalue, public;

CREATE TABLE IF NOT EXISTS security_master (
    id BIGSERIAL PRIMARY KEY,
    ticker TEXT NOT NULL,
    symbol_full TEXT NOT NULL,
    company_name TEXT NOT NULL,
    exchange TEXT NOT NULL,
    currency TEXT NOT NULL,
    sector TEXT,
    industry TEXT,
    subindustry TEXT,
    company_type TEXT,
    sector_template TEXT,
    country TEXT NOT NULL DEFAULT 'US',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_security_master_ticker UNIQUE (ticker),
    CONSTRAINT uq_security_master_symbol_full UNIQUE (symbol_full)
);

CREATE TABLE IF NOT EXISTS source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_name TEXT NOT NULL,
    source_tier TEXT NOT NULL,
    source_type TEXT NOT NULL,
    priority_rank INTEGER NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_source_registry_source_name UNIQUE (source_name)
);

CREATE TABLE IF NOT EXISTS security_identifier_map (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    source_name TEXT NOT NULL,
    source_symbol TEXT,
    cik TEXT,
    isin TEXT,
    cusip TEXT,
    sedol TEXT,
    exchange_code TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS source_documents (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES source_registry(id),
    document_type TEXT NOT NULL,
    document_title TEXT,
    filing_date DATE,
    accepted_at TIMESTAMPTZ,
    period_end_date DATE,
    accession_no TEXT,
    document_url TEXT,
    local_storage_path TEXT,
    checksum TEXT,
    parsed_status TEXT NOT NULL DEFAULT 'pending',
    parser_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS source_document_facts_raw (
    id BIGSERIAL PRIMARY KEY,
    source_document_id BIGINT NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    taxonomy TEXT NOT NULL,
    concept_name TEXT NOT NULL,
    unit TEXT,
    period_start DATE,
    period_end DATE,
    fiscal_year INTEGER,
    fiscal_period TEXT,
    value_numeric NUMERIC(30, 10),
    value_text TEXT,
    decimals INTEGER,
    context_ref TEXT,
    segment_name TEXT,
    is_custom_tag BOOLEAN NOT NULL DEFAULT FALSE,
    raw_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS market_data_raw (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES source_registry(id),
    data_type TEXT NOT NULL,
    trade_date DATE,
    trade_ts TIMESTAMPTZ,
    raw_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS financial_standardized (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    period_type TEXT NOT NULL,
    fiscal_year INTEGER,
    fiscal_period TEXT,
    period_start DATE,
    period_end DATE NOT NULL,
    revenue NUMERIC(24, 6),
    gross_profit NUMERIC(24, 6),
    ebitda NUMERIC(24, 6),
    ebit NUMERIC(24, 6),
    net_income NUMERIC(24, 6),
    operating_cash_flow NUMERIC(24, 6),
    capex NUMERIC(24, 6),
    free_cash_flow NUMERIC(24, 6),
    cash NUMERIC(24, 6),
    short_term_debt NUMERIC(24, 6),
    long_term_debt NUMERIC(24, 6),
    total_debt NUMERIC(24, 6),
    equity NUMERIC(24, 6),
    total_assets NUMERIC(24, 6),
    total_liabilities NUMERIC(24, 6),
    diluted_shares NUMERIC(24, 4),
    basic_shares NUMERIC(24, 4),
    sbc NUMERIC(24, 6),
    lease_liabilities NUMERIC(24, 6),
    pension_liabilities NUMERIC(24, 6),
    minority_interest NUMERIC(24, 6),
    goodwill NUMERIC(24, 6),
    intangibles NUMERIC(24, 6),
    tax_rate_effective NUMERIC(10, 6),
    net_debt NUMERIC(24, 6),
    source_document_id BIGINT REFERENCES source_documents(id),
    quality_flag_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_financial_standardized_period_type CHECK (period_type IN ('FY', 'Q', 'TTM', 'NTM', 'MID_CYCLE'))
);

CREATE TABLE IF NOT EXISTS financial_derived_metrics (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    period_type TEXT NOT NULL,
    fiscal_year INTEGER,
    fiscal_period TEXT,
    period_end DATE NOT NULL,
    gross_margin NUMERIC(12, 6),
    ebit_margin NUMERIC(12, 6),
    fcf_margin NUMERIC(12, 6),
    roe NUMERIC(12, 6),
    roic NUMERIC(12, 6),
    roa NUMERIC(12, 6),
    fcf_conversion NUMERIC(12, 6),
    accruals_ratio NUMERIC(12, 6),
    net_debt_to_ebitda NUMERIC(12, 6),
    interest_coverage NUMERIC(12, 6),
    working_capital_ratio NUMERIC(12, 6),
    book_value_per_share NUMERIC(24, 6),
    eps_diluted NUMERIC(24, 6),
    owner_earnings_estimate NUMERIC(24, 6),
    altman_z_score NUMERIC(12, 6),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_financial_derived_metrics_period_type CHECK (period_type IN ('FY', 'Q', 'TTM', 'NTM', 'MID_CYCLE'))
);

CREATE TABLE IF NOT EXISTS market_price_daily (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    trade_date DATE NOT NULL,
    open NUMERIC(24, 6),
    high NUMERIC(24, 6),
    low NUMERIC(24, 6),
    close NUMERIC(24, 6) NOT NULL,
    volume BIGINT,
    turnover NUMERIC(24, 6),
    adj_close NUMERIC(24, 6),
    dividend_adjustment_factor NUMERIC(18, 8),
    split_adjustment_factor NUMERIC(18, 8),
    source_id BIGINT NOT NULL REFERENCES source_registry(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS market_snapshot (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    snapshot_time TIMESTAMPTZ NOT NULL,
    last_price NUMERIC(24, 6) NOT NULL,
    prev_close NUMERIC(24, 6),
    open_price NUMERIC(24, 6),
    high_price NUMERIC(24, 6),
    low_price NUMERIC(24, 6),
    volume BIGINT,
    turnover NUMERIC(24, 6),
    market_cap_vendor NUMERIC(24, 6),
    pe_ttm_vendor NUMERIC(24, 6),
    pb_vendor NUMERIC(24, 6),
    dividend_yield_vendor NUMERIC(12, 6),
    eps_ttm_vendor NUMERIC(24, 6),
    bps_vendor NUMERIC(24, 6),
    float_shares_vendor NUMERIC(24, 4),
    total_shares_vendor NUMERIC(24, 4),
    source_id BIGINT NOT NULL REFERENCES source_registry(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS market_intraday_snapshot (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    snapshot_time TIMESTAMPTZ NOT NULL,
    last_price NUMERIC(24, 6) NOT NULL,
    volume BIGINT,
    turnover NUMERIC(24, 6),
    change_pct NUMERIC(12, 6),
    source_id BIGINT NOT NULL REFERENCES source_registry(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS valuation_method_catalog (
    id BIGSERIAL PRIMARY KEY,
    method_name TEXT NOT NULL,
    method_group TEXT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_valuation_method_catalog_method_name UNIQUE (method_name)
);

CREATE TABLE IF NOT EXISTS sector_template_config (
    id BIGSERIAL PRIMARY KEY,
    sector_template TEXT NOT NULL,
    primary_methods_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    primary_metrics_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    forbidden_methods_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    default_weights_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    risk_notes_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    safety_margin_rule_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sector_template_config_sector_template UNIQUE (sector_template)
);

CREATE TABLE IF NOT EXISTS valuation_parameter_set (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT REFERENCES security_master(id) ON DELETE CASCADE,
    sector_template TEXT,
    parameter_type TEXT NOT NULL,
    parameter_key TEXT NOT NULL,
    parameter_value JSONB NOT NULL,
    effective_from DATE,
    effective_to DATE,
    source_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_valuation_parameter_scope CHECK (security_id IS NOT NULL OR sector_template IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS data_quality_audit (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    audit_date DATE NOT NULL,
    latest_10k_date DATE,
    latest_10q_date DATE,
    has_recent_8k BOOLEAN NOT NULL DEFAULT FALSE,
    share_count_verified BOOLEAN NOT NULL DEFAULT FALSE,
    sbc_quantified BOOLEAN NOT NULL DEFAULT FALSE,
    guidance_status TEXT,
    net_debt_updated BOOLEAN NOT NULL DEFAULT FALSE,
    tax_rate_updated BOOLEAN NOT NULL DEFAULT FALSE,
    litigation_captured BOOLEAN NOT NULL DEFAULT FALSE,
    convertible_identified BOOLEAN NOT NULL DEFAULT FALSE,
    data_quality_score NUMERIC(10, 4),
    confidence_level NUMERIC(10, 4),
    missing_items_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    warning_flags_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS financial_quality_scores (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    period_type TEXT NOT NULL,
    period_end DATE NOT NULL,
    earnings_quality_score NUMERIC(10, 4),
    revenue_quality_score NUMERIC(10, 4),
    balance_sheet_score NUMERIC(10, 4),
    capital_efficiency_score NUMERIC(10, 4),
    capital_allocation_score NUMERIC(10, 4),
    total_quality_score NUMERIC(10, 4),
    quality_score_breakdown_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    red_flags_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    forensic_flags_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_financial_quality_scores_period_type CHECK (period_type IN ('FY', 'Q', 'TTM', 'NTM', 'MID_CYCLE'))
);

CREATE TABLE IF NOT EXISTS valuation_runs (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    valuation_date TIMESTAMPTZ NOT NULL,
    run_mode TEXT NOT NULL,
    current_price NUMERIC(24, 6) NOT NULL,
    fair_value_low NUMERIC(24, 6),
    fair_value_mid NUMERIC(24, 6),
    fair_value_high NUMERIC(24, 6),
    blended_intrinsic_value NUMERIC(24, 6),
    confidence_level NUMERIC(10, 4),
    margin_of_safety NUMERIC(12, 6),
    implied_expectation_label TEXT,
    sector_template TEXT,
    company_type TEXT,
    weighted_value NUMERIC(24, 6),
    final_verdict TEXT,
    buy_zone_low NUMERIC(24, 6),
    buy_zone_high NUMERIC(24, 6),
    hold_zone_low NUMERIC(24, 6),
    hold_zone_high NUMERIC(24, 6),
    avoid_zone_low NUMERIC(24, 6),
    avoid_zone_high NUMERIC(24, 6),
    report_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_valuation_runs_run_mode CHECK (run_mode IN ('scheduled', 'manual', 'api'))
);

CREATE TABLE IF NOT EXISTS risk_scores (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    valuation_run_id BIGINT NOT NULL REFERENCES valuation_runs(id) ON DELETE CASCADE,
    risk_type TEXT NOT NULL,
    probability NUMERIC(10, 4),
    impact NUMERIC(10, 4),
    score NUMERIC(10, 4),
    adjustment_type TEXT NOT NULL,
    adjustment_value NUMERIC(10, 4),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_risk_scores_adjustment_type CHECK (adjustment_type IN ('wacc', 'scenario_weight', 'mos'))
);

CREATE TABLE IF NOT EXISTS valuation_method_results (
    id BIGSERIAL PRIMARY KEY,
    valuation_run_id BIGINT NOT NULL REFERENCES valuation_runs(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES security_master(id) ON DELETE CASCADE,
    method_name TEXT NOT NULL,
    bear_value NUMERIC(24, 6),
    base_value NUMERIC(24, 6),
    bull_value NUMERIC(24, 6),
    weight NUMERIC(12, 6),
    is_primary_method BOOLEAN NOT NULL DEFAULT FALSE,
    input_snapshot_date DATE,
    assumptions_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    sensitivity_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scenario_results (
    id BIGSERIAL PRIMARY KEY,
    valuation_run_id BIGINT NOT NULL REFERENCES valuation_runs(id) ON DELETE CASCADE,
    scenario_name TEXT NOT NULL,
    probability_weight NUMERIC(12, 6),
    price_target NUMERIC(24, 6),
    revenue_cagr NUMERIC(12, 6),
    ebitda_margin NUMERIC(12, 6),
    fcf_margin NUMERIC(12, 6),
    notes_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scenario_results_scenario_name CHECK (scenario_name IN ('bear', 'base', 'bull', 'custom'))
);

CREATE TABLE IF NOT EXISTS reverse_dcf_results (
    id BIGSERIAL PRIMARY KEY,
    valuation_run_id BIGINT NOT NULL REFERENCES valuation_runs(id) ON DELETE CASCADE,
    implied_revenue_cagr NUMERIC(12, 6),
    implied_ebitda_margin NUMERIC(12, 6),
    implied_fcf_margin NUMERIC(12, 6),
    terminal_growth NUMERIC(12, 6),
    wacc NUMERIC(12, 6),
    implied_expectation_label TEXT,
    notes_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reverse_dcf_results_run UNIQUE (valuation_run_id)
);

CREATE TABLE IF NOT EXISTS report_blocks (
    id BIGSERIAL PRIMARY KEY,
    valuation_run_id BIGINT NOT NULL REFERENCES valuation_runs(id) ON DELETE CASCADE,
    block_type TEXT NOT NULL,
    block_content JSONB NOT NULL DEFAULT '{}'::JSONB,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type TEXT NOT NULL,
    security_id BIGINT REFERENCES security_master(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS normalization_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type TEXT NOT NULL,
    security_id BIGINT REFERENCES security_master(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS valuation_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type TEXT NOT NULL,
    security_id BIGINT REFERENCES security_master(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    valuation_run_id BIGINT REFERENCES valuation_runs(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_identifier_map_source_symbol
    ON security_identifier_map (source_name, source_symbol)
    WHERE source_symbol IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_identifier_map_cik
    ON security_identifier_map (cik)
    WHERE cik IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_identifier_map_isin
    ON security_identifier_map (isin)
    WHERE isin IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_identifier_map_cusip
    ON security_identifier_map (cusip)
    WHERE cusip IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_source_documents_source_accession
    ON source_documents (source_id, accession_no)
    WHERE accession_no IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_standardized_period
    ON financial_standardized (security_id, period_type, fiscal_year, fiscal_period, period_end);

CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_derived_metrics_period
    ON financial_derived_metrics (security_id, period_type, fiscal_year, fiscal_period, period_end);

CREATE UNIQUE INDEX IF NOT EXISTS uq_market_price_daily_security_date_source
    ON market_price_daily (security_id, trade_date, source_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_market_snapshot_security_time_source
    ON market_snapshot (security_id, snapshot_time, source_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_market_intraday_snapshot_security_time_source
    ON market_intraday_snapshot (security_id, snapshot_time, source_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_data_quality_audit_security_date
    ON data_quality_audit (security_id, audit_date);

CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_quality_scores_period
    ON financial_quality_scores (security_id, period_type, period_end);

CREATE UNIQUE INDEX IF NOT EXISTS uq_valuation_method_results_run_method
    ON valuation_method_results (valuation_run_id, method_name);

CREATE UNIQUE INDEX IF NOT EXISTS uq_scenario_results_run_scenario
    ON scenario_results (valuation_run_id, scenario_name);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_blocks_run_block
    ON report_blocks (valuation_run_id, block_type);

CREATE INDEX IF NOT EXISTS idx_security_master_sector_template
    ON security_master (sector_template, is_active);

CREATE INDEX IF NOT EXISTS idx_source_documents_security_filing_date
    ON source_documents (security_id, filing_date DESC);

CREATE INDEX IF NOT EXISTS idx_source_document_facts_raw_security_concept_period
    ON source_document_facts_raw (security_id, concept_name, period_end DESC);

CREATE INDEX IF NOT EXISTS idx_market_data_raw_security_trade_ts
    ON market_data_raw (security_id, trade_ts DESC);

CREATE INDEX IF NOT EXISTS idx_financial_standardized_security_period_end
    ON financial_standardized (security_id, period_end DESC);

CREATE INDEX IF NOT EXISTS idx_financial_derived_metrics_security_period_end
    ON financial_derived_metrics (security_id, period_end DESC);

CREATE INDEX IF NOT EXISTS idx_market_price_daily_security_trade_date
    ON market_price_daily (security_id, trade_date DESC);

CREATE INDEX IF NOT EXISTS idx_market_snapshot_security_snapshot_time
    ON market_snapshot (security_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_market_intraday_snapshot_security_snapshot_time
    ON market_intraday_snapshot (security_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_parameter_set_security_type_key
    ON valuation_parameter_set (security_id, parameter_type, parameter_key);

CREATE INDEX IF NOT EXISTS idx_valuation_parameter_set_sector_type_key
    ON valuation_parameter_set (sector_template, parameter_type, parameter_key);

CREATE INDEX IF NOT EXISTS idx_data_quality_audit_security_audit_date
    ON data_quality_audit (security_id, audit_date DESC);

CREATE INDEX IF NOT EXISTS idx_financial_quality_scores_security_period_end
    ON financial_quality_scores (security_id, period_end DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_runs_security_date
    ON valuation_runs (security_id, valuation_date DESC);

CREATE INDEX IF NOT EXISTS idx_risk_scores_run_id
    ON risk_scores (valuation_run_id);

CREATE INDEX IF NOT EXISTS idx_report_blocks_run_order
    ON report_blocks (valuation_run_id, display_order);

CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status_started_at
    ON ingestion_jobs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_normalization_jobs_status_started_at
    ON normalization_jobs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_jobs_status_started_at
    ON valuation_jobs (status, started_at DESC);

COMMIT;
