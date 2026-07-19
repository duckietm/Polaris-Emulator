#!/usr/bin/env bash
set -euo pipefail

: "${E2E_DB_HOST:?E2E_DB_HOST is required}"
: "${E2E_DB_PORT:?E2E_DB_PORT is required}"
: "${E2E_DB_NAME:?E2E_DB_NAME is required}"
: "${E2E_DB_USER:?E2E_DB_USER is required}"
: "${E2E_SSO_TICKET:?E2E_SSO_TICKET is required}"
: "${E2E_SECOND_SSO_TICKET:?E2E_SECOND_SSO_TICKET is required}"

[[ "$E2E_DB_HOST" == '127.0.0.1' || "$E2E_DB_HOST" == 'localhost' || "$E2E_DB_HOST" == '::1' ]] || { echo 'E2E_DB_HOST must use a loopback host' >&2; exit 2; }
[[ "$E2E_DB_NAME" =~ ^polaris_e2e_[A-Za-z0-9_]+$ ]] || { echo 'E2E_DB_NAME must start with polaris_e2e_' >&2; exit 2; }
[[ "$E2E_SSO_TICKET" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'invalid E2E_SSO_TICKET' >&2; exit 2; }
[[ "$E2E_SECOND_SSO_TICKET" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'invalid E2E_SECOND_SSO_TICKET' >&2; exit 2; }

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base_database="$repo/Emulator/src/main/resources/db/migration/V20260518000000__base_database.sql"

mysql_args=("--host=$E2E_DB_HOST" "--port=$E2E_DB_PORT" "--user=$E2E_DB_USER")
# MYSQL_PWD keeps the password out of the process list.
[[ -z "${E2E_DB_PASSWORD:-}" ]] || export MYSQL_PWD="$E2E_DB_PASSWORD"
mysql "${mysql_args[@]}" \
  --execute="DROP DATABASE IF EXISTS \`$E2E_DB_NAME\`; CREATE DATABASE \`$E2E_DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql "${mysql_args[@]}" \
  "--database=$E2E_DB_NAME" --default-character-set=utf8mb4 < "$base_database"
{
  printf "SET @e2e_sso_ticket='%s';\n" "$E2E_SSO_TICKET"
  printf "SET @e2e_second_sso_ticket='%s';\n" "$E2E_SECOND_SSO_TICKET"
  cat "$repo/e2e/seed.sql"
} |
  mysql "${mysql_args[@]}" "--database=$E2E_DB_NAME"
