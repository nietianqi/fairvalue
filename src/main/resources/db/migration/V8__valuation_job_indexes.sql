CREATE INDEX IF NOT EXISTS idx_valuation_jobs_job_type_created_at
    ON fairvalue.valuation_jobs (job_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_jobs_security_created_at
    ON fairvalue.valuation_jobs (security_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_valuation_jobs_job_type_status_created_at
    ON fairvalue.valuation_jobs (job_type, status, created_at DESC);
