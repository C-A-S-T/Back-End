CREATE TABLE ai.research_job (
    job_id UUID PRIMARY KEY,
    task_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_research_job_task UNIQUE (task_id),
    CONSTRAINT ck_ai_research_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_ai_research_job_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_ai_research_job_status
    ON ai.research_job (status, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ai.research_job TO cast_ai;
