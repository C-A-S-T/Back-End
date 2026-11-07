-- Preserve order and all existing strings; never invent normalized gold values.
UPDATE evaluation_case e
SET expected_claims = (
    SELECT COALESCE(jsonb_agg(
        CASE WHEN jsonb_typeof(item) = 'string'
             THEN jsonb_build_object('type', NULL, 'content', item, 'value', NULL)
             ELSE item END ORDER BY ordinal), '[]'::jsonb)
    FROM jsonb_array_elements(e.expected_claims) WITH ORDINALITY AS items(item, ordinal)
);
