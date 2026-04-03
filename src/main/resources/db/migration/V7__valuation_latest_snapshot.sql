SET search_path TO fairvalue, public;

CREATE TABLE IF NOT EXISTS valuation_latest_snapshot (
    security_id BIGINT PRIMARY KEY REFERENCES security_master(id) ON DELETE CASCADE,
    market TEXT NOT NULL,
    latest_run_id BIGINT REFERENCES valuation_runs(id) ON DELETE SET NULL,
    as_of_time TIMESTAMPTZ NOT NULL,
    current_price NUMERIC(24, 6) NOT NULL,
    fair_value_low NUMERIC(24, 6),
    fair_value_mid NUMERIC(24, 6),
    fair_value_high NUMERIC(24, 6),
    upside_pct NUMERIC(12, 6),
    confidence_level NUMERIC(10, 4),
    margin_of_safety NUMERIC(12, 6),
    final_verdict TEXT,
    implied_expectation TEXT,
    value_trap_flag BOOLEAN NOT NULL DEFAULT FALSE,
    sector_template TEXT,
    company_type TEXT,
    quality_score NUMERIC(10, 4),
    data_quality_score NUMERIC(10, 4),
    data_version TEXT,
    summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    report_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    source_attribution_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_valuation_latest_snapshot_market_upside
    ON valuation_latest_snapshot (market, upside_pct DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_latest_snapshot_latest_run_id
    ON valuation_latest_snapshot (latest_run_id);
