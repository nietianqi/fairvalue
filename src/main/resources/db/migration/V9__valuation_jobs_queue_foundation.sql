SET search_path TO fairvalue, public;

ALTER TABLE fairvalue.valuation_jobs
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;

ALTER TABLE fairvalue.valuation_jobs
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE fairvalue.valuation_jobs
    ADD COLUMN IF NOT EXISTS worker_id TEXT;

CREATE INDEX IF NOT EXISTS idx_valuation_jobs_queue_ready
    ON fairvalue.valuation_jobs (job_type, status, priority DESC, available_at ASC, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_valuation_jobs_worker_status
    ON fairvalue.valuation_jobs (worker_id, status, started_at DESC);
