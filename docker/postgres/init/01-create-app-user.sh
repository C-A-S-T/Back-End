#!/bin/sh
set -eu
: "${POSTGRES_USER:?required}"
: "${POSTGRES_DB:?required}"
: "${CAST_DB_PASSWORD:?required}"
: "${CAST_AI_DB_PASSWORD:?required}"
: "${CAST_MIGRATION_DB_PASSWORD:?required}"

# Re-runnable administrator provisioning for this CAST database only.
# Stop the backend and AI writers before applying this to an existing volume.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
    --set=db_name="$POSTGRES_DB" --set=app_password="$CAST_DB_PASSWORD" \
    --set=ai_password="$CAST_AI_DB_PASSWORD" --set=migration_password="$CAST_MIGRATION_DB_PASSWORD" <<'SQL'
SELECT 'CREATE ROLE cast_migrator LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='cast_migrator') \gexec
SELECT 'CREATE ROLE cast_app LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='cast_app') \gexec
SELECT 'CREATE ROLE cast_ai LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='cast_ai') \gexec
ALTER ROLE cast_migrator WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'migration_password';
ALTER ROLE cast_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
ALTER ROLE cast_ai WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'ai_password';
REVOKE cast_migrator FROM cast_app, cast_ai;
REVOKE cast_app FROM cast_ai;
REVOKE cast_ai FROM cast_app;
-- The old local setup made cast_app the owner. Runtime accounts must cease to own DDL objects.
-- Limit ownership changes to known CAST tables in the connected database.
-- REASSIGN OWNED would also affect shared objects (other databases/tablespaces).
SELECT format('ALTER TABLE %I.%I OWNER TO cast_migrator', schemaname, tablename)
FROM pg_tables WHERE tableowner='cast_app'
    AND schemaname IN ('public', 'backend', 'ai')
    AND tablename IN ('flyway_schema_history', 'app_user', 'user_consent', 'research_query',
        'research_task', 'answer', 'claim', 'verification', 'evidence', 'document',
        'document_chunk', 'retrieval_run', 'retrieval_result', 'evaluation_case',
        'source_folder', 'saved_source', 'ai_job') \gexec
ALTER DATABASE :"db_name" OWNER TO cast_migrator;
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO cast_app, cast_ai;
REVOKE CREATE ON SCHEMA public FROM PUBLIC, cast_app, cast_ai;
GRANT USAGE, CREATE ON SCHEMA public TO cast_migrator;
ALTER ROLE cast_app IN DATABASE :"db_name" SET search_path = backend;
ALTER ROLE cast_ai IN DATABASE :"db_name" SET search_path = ai;
ALTER ROLE cast_migrator IN DATABASE :"db_name" SET search_path = public;
SQL
