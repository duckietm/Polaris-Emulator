#!/usr/bin/env bash
set -euo pipefail

for name in E2E_DB_HOST E2E_DB_PORT E2E_DB_NAME E2E_DB_USER; do
  [[ -n "${!name:-}" ]] || { echo "$name is required" >&2; exit 2; }
done

[[ "$E2E_DB_HOST" == '127.0.0.1' || "$E2E_DB_HOST" == 'localhost' || "$E2E_DB_HOST" == '::1' ]] || { echo 'E2E_DB_HOST must use a loopback host' >&2; exit 2; }
[[ "$E2E_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'invalid E2E_DB_NAME' >&2; exit 2; }

password=()
[[ -z "${E2E_DB_PASSWORD:-}" ]] || password=("--password=$E2E_DB_PASSWORD")

for _ in $(seq 1 40); do
  state="$(mysql "--host=$E2E_DB_HOST" "--port=$E2E_DB_PORT" "--user=$E2E_DB_USER" "${password[@]}" "--database=$E2E_DB_NAME" --batch --skip-column-names --execute "SELECT CONCAT(user_id, ':', room_id) FROM items WHERE id = 900004")"
  [[ "$state" == '900001:0' ]] && { echo 'Inventory item 900004 persisted as 900001:0'; exit 0; }
  sleep 0.25
done

echo 'Inventory item 900004 did not persist as 900001:0' >&2
exit 1
