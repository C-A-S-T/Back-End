-- User-owned memo; existing rows remain unchanged (NULL).
ALTER TABLE backend.saved_source ADD COLUMN memo VARCHAR(1000);
