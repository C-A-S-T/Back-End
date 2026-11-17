-- Favorite was never part of the source archive UI. Preserve saved-source records.
-- V6 moved saved_source to backend; V1-V11 remain immutable.
DROP INDEX backend.idx_saved_source_favorite;

ALTER TABLE backend.saved_source
    DROP COLUMN is_favorite;
