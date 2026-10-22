#!/bin/sh
set -eu

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${CAST_DB_USER:?CAST_DB_USER is required}"
: "${CAST_DB_PASSWORD:?CAST_DB_PASSWORD is required}"

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=app_user="$CAST_DB_USER" \
  --set=app_password="$CAST_DB_PASSWORD" \
  --set=db_name="$POSTGRES_DB" <<'SQL'

CREATE ROLE :"app_user"
    WITH LOGIN PASSWORD :'app_password';

ALTER DATABASE :"db_name"
    OWNER TO :"app_user";

GRANT USAGE, CREATE
    ON SCHEMA public
    TO :"app_user";

SQL
