-- Existing documents remain unhashed until their original bytes are available.
ALTER TABLE document ADD COLUMN content_hash VARCHAR(64)
    CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX uq_document_content_hash
    ON document(content_hash) WHERE content_hash IS NOT NULL;
