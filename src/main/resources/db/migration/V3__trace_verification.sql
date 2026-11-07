ALTER TABLE verification
    ADD COLUMN verification_method VARCHAR(20) CHECK (verification_method IN ('RULE', 'LLM', 'HYBRID')),
    ADD COLUMN rule_result VARCHAR(24) CHECK (rule_result IN ('SUPPORTED', 'CONTRADICTED', 'PARTIAL', 'INSUFFICIENT')),
    ADD COLUMN llm_result VARCHAR(24) CHECK (llm_result IN ('SUPPORTED', 'CONTRADICTED', 'PARTIAL', 'INSUFFICIENT')),
    ADD COLUMN confidence DOUBLE PRECISION CHECK (confidence >= 0 AND confidence <= 1),
    ADD COLUMN run_id BIGINT REFERENCES retrieval_run(run_id) ON DELETE RESTRICT;
