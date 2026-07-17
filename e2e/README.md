# Polaris end-to-end runtime

This directory provides the disposable Polaris side of the renderer end-to-end tests. It recreates an isolated schema, imports the Polaris base database, seeds synthetic test data, then starts Polaris on dedicated loopback ports. Normal Polaris startup applies any migrations newer than the base database.

The scripts never require production credentials or production data. `prepare-database` only accepts a loopback database host and a database name beginning with `polaris_e2e_`. It deletes and recreates that database on every run.

## Prerequisites

- JDK 25 and Maven
- a local MariaDB server and command-line client
- Node.js 22 and Yarn in the companion renderer repository
- free ports for the WebSocket, raw game server and RCON listeners

Build the emulator first:

```powershell
mvn -B -f Emulator/pom.xml package -DskipTests
```

## Environment

The runtime and database scripts share these variables:

| Variable | Example | Purpose |
| --- | --- | --- |
| `E2E_MYSQL` | `E:\laragon\bin\mysql\mariadb-12.2.2-winx64\bin\mysql.exe` | Windows MariaDB client path |
| `E2E_DB_HOST` | `127.0.0.1` | Loopback database host |
| `E2E_DB_PORT` | `3306` | Database port |
| `E2E_DB_NAME` | `polaris_e2e_inventory` | Disposable schema |
| `E2E_DB_USER` | `root` | Local database user |
| `E2E_DB_PASSWORD` | `root` | Local database password; may be empty |
| `E2E_SSO_TICKET` | `e2e-inventory-ticket` | Ticket assigned to fixture user `900001` |
| `E2E_USER_ID` | `900001` | Fixture user used by probes and tests |
| `E2E_ROOM_ID` | `900002` | Fixture room |
| `E2E_GAME_PORT` | `31999` | WebSocket listener and HTTP probe |
| `E2E_RAW_PORT` | `31998` | Raw game listener |
| `E2E_RCON_PORT` | `32000` | RCON listener |
| `E2E_WS_URL` | `ws://127.0.0.1:31999` | Renderer WebSocket URL |
| `E2E_PROBE_URL` | `http://127.0.0.1:31999/__e2e` | Test-only probe root |
| `E2E_RUNTIME_DIR` | `%TEMP%\polaris-e2e-inventory` | Generated config, PID and logs |

## Windows PowerShell

Set the variables above, then prepare and start Polaris:

```powershell
./e2e/prepare-database.ps1
$runtime = "$env:TEMP\polaris-e2e-inventory"
$process = ./e2e/start-emulator.ps1 -RuntimeDirectory $runtime | ConvertFrom-Json
```

Run the selected renderer scenario from the renderer repository. For the inventory lifecycle, verify the final database state before stopping the exact process returned by the start script:

```powershell
yarn test:e2e:inventory
../emulatore/e2e/verify-inventory-state.ps1
Stop-Process -Id $process.pid
```

Use `try` / `finally` around test execution in automation so the recorded PID is always stopped. Do not stop Java processes by name.

## Linux and macOS

```bash
mvn -B -f Emulator/pom.xml package -DskipTests
bash e2e/prepare-database.sh
bash e2e/start-emulator.sh
# Run the renderer scenario from its repository.
yarn test:e2e:inventory
bash ../emulatore/e2e/verify-inventory-state.sh
kill "$(cat "${E2E_RUNTIME_DIR}/polaris.pid")"
```

No workflow in this repository currently invokes this harness. It is run with the companion renderer repository when a full client-to-database test is needed.

## Fixtures and diagnostics

- user `900001` authenticates with `E2E_SSO_TICKET`
- room `900002` is the deterministic room-entry target
- floor item `900004` starts in user `900001`'s inventory and uses base item `18`
- the inventory verifier requires item `900004` to finish as `user_id=900001, room_id=0`
- `polaris.stdout.log` and `polaris.stderr.log` are written below `E2E_RUNTIME_DIR`

If startup times out, inspect both logs and confirm that ports `31998`, `31999` and `32000` are free. If database preparation fails, confirm that MariaDB is running, the client path is correct and the configured user can create the disposable schema.
