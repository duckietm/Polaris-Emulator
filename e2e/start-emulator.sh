#!/usr/bin/env bash
set -euo pipefail

for name in E2E_DB_HOST E2E_DB_PORT E2E_DB_NAME E2E_DB_USER E2E_SSO_TICKET E2E_GAME_PORT E2E_RAW_PORT E2E_RCON_PORT; do
  [[ -n "${!name:-}" ]] || { echo "$name is required" >&2; exit 2; }
done

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
jar="${1:-$(find "$repo/Emulator/target" -name '*-jar-with-dependencies.jar' -print -quit)}"
[[ -f "$jar" ]] || { echo 'packaged emulator JAR not found' >&2; exit 2; }
runtime="${E2E_RUNTIME_DIR:-$(mktemp -d)}"
mkdir -p "$runtime/logging/errors"

sed \
  -e "s|\${E2E_DB_HOST}|$E2E_DB_HOST|g" \
  -e "s|\${E2E_DB_PORT}|$E2E_DB_PORT|g" \
  -e "s|\${E2E_DB_NAME}|$E2E_DB_NAME|g" \
  -e "s|\${E2E_DB_USER}|$E2E_DB_USER|g" \
  -e "s|\${E2E_DB_PASSWORD}|${E2E_DB_PASSWORD:-}|g" \
  -e "s|\${E2E_GAME_PORT}|$E2E_GAME_PORT|g" \
  -e "s|\${E2E_RAW_PORT}|$E2E_RAW_PORT|g" \
  -e "s|\${E2E_RCON_PORT}|$E2E_RCON_PORT|g" \
  "$repo/e2e/config.ini.template" > "$runtime/config.ini"

(cd "$runtime" && java -Dhabbo.console.style=plain -jar "$jar" >polaris.stdout.log 2>polaris.stderr.log) &
pid=$!
echo "$pid" > "$runtime/polaris.pid"
probe="http://127.0.0.1:$E2E_GAME_PORT/__e2e/session-count?userId=900001"

for _ in $(seq 1 360); do
  kill -0 "$pid" 2>/dev/null || { cat "$runtime/polaris.stderr.log" >&2; exit 1; }
  if curl --fail --silent "$probe" >/dev/null; then
    printf '{"pid":%s,"wsUrl":"ws://127.0.0.1:%s","probeUrl":"http://127.0.0.1:%s/__e2e","runtime":"%s"}\n' "$pid" "$E2E_GAME_PORT" "$E2E_GAME_PORT" "$runtime"
    exit 0
  fi
  sleep 0.25
done

kill "$pid" 2>/dev/null || true
echo 'Polaris readiness deadline exceeded' >&2
exit 1
