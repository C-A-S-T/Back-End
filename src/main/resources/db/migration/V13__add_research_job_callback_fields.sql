-- V13: Persist AI research callback delivery state.
-- Preserve existing research jobs and processing state.

ALTER TABLE ai.research_job
    ADD COLUMN callback_payload JSONB,
    ADD COLUMN callback_status VARCHAR(20),
    ADD COLUMN callback_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN callback_next_retry_at TIMESTAMPTZ,
    ADD COLUMN callback_last_error TEXT;

ALTER TABLE ai.research_job
    ADD CONSTRAINT ck_ai_research_job_callback_status
        CHECK (
            callback_status IN (
                'PENDING',
                'SENDING',
                'RETRY',
                'DELIVERED',
                'FAILED_PERMANENT'
            )
        ),
    ADD CONSTRAINT ck_ai_research_job_callback_attempt_count
        CHECK (callback_attempt_count >= 0);
