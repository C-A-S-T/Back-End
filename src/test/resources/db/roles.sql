CREATE ROLE cast_migrator LOGIN PASSWORD 'migration-test-password';
CREATE ROLE cast_app LOGIN PASSWORD 'backend-test-password';
CREATE ROLE cast_ai LOGIN PASSWORD 'ai-test-password';
ALTER DATABASE cast_persistence_test OWNER TO cast_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO cast_migrator;
ALTER ROLE cast_app SET search_path = backend;
ALTER ROLE cast_ai SET search_path = ai;
ALTER ROLE cast_migrator SET search_path = public;
