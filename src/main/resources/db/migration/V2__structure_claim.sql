ALTER TABLE claim
    ADD COLUMN claim_type VARCHAR(20) CHECK (claim_type IN ('DATE', 'AMOUNT', 'TARGET', 'CONDITION')),
    ADD COLUMN claim_value JSONB CHECK (claim_value IS NULL OR jsonb_typeof(claim_value) = 'object');
