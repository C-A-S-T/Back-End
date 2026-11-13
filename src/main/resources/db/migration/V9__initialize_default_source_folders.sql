-- Apply this only after the team's V8 migration has been merged/applied.
-- Keep existing folders and saved_source.folder_id references unchanged.
ALTER TABLE backend.source_folder
    ADD COLUMN folder_category VARCHAR(30);

ALTER TABLE backend.source_folder
    ADD CONSTRAINT ck_source_folder_category
    CHECK (folder_category IS NULL OR folder_category IN ('HOUSING', 'GOV_SUPPORT', 'EDUCATION'));

-- Reuse exact-name legacy folders, preserving folder_id and the documents they contain.
UPDATE backend.source_folder
SET folder_category = CASE folder_name
    WHEN '청약·주거' THEN 'HOUSING'
    WHEN '정부 지원정책' THEN 'GOV_SUPPORT'
    WHEN '교육' THEN 'EDUCATION'
END
WHERE folder_category IS NULL
  AND folder_name IN ('청약·주거', '정부 지원정책', '교육');

ALTER TABLE backend.source_folder
    ADD CONSTRAINT uq_source_folder_user_category UNIQUE (user_id, folder_category);

-- Add only missing categories for already-existing users.
INSERT INTO backend.source_folder
    (user_id, folder_name, folder_category, created_at, updated_at)
SELECT u.user_id, defaults.folder_name, defaults.folder_category,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM backend.app_user AS u
CROSS JOIN (VALUES
    ('청약·주거', 'HOUSING'),
    ('정부 지원정책', 'GOV_SUPPORT'),
    ('교육', 'EDUCATION')
) AS defaults(folder_name, folder_category)
LEFT JOIN backend.source_folder AS f
    ON f.user_id = u.user_id AND f.folder_category = defaults.folder_category
WHERE f.folder_id IS NULL
ON CONFLICT (user_id, folder_name) DO NOTHING;
