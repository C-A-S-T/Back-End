-- The backend owns delivery state, task identity, and accepted-result deduplication.
CREATE TABLE backend.ai_job (
    job_id UUID PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE REFERENCES backend.research_task(task_id) ON DELETE RESTRICT,
    question TEXT NOT NULL,
    dispatched_at TIMESTAMPTZ,
    result_hash VARCHAR(64),
    outcome VARCHAR(12) CHECK (outcome IN ('SUCCESS','FAILURE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((result_hash IS NULL) = (outcome IS NULL)),
    CHECK (result_hash IS NULL OR result_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_ai_job_pending ON backend.ai_job(created_at)
    WHERE dispatched_at IS NULL AND result_hash IS NULL;
