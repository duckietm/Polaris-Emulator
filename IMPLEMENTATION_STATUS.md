# Implementation status — testing & migrations

Branch: `feature/testing-and-migrations` (off `main`). **Not pushed** — for local
review and testing first. Companion to `TESTING_AND_MIGRATIONS_PLAN.md`.

This is a foundation-first, verified first cut. The V1–V4 migration pipeline is
proven against real MariaDB 10.11 and 11 containers. The remaining Arc→current
Polaris schema/reference-data work is still Phase 3 and is not represented as
finished here.

## What's implemented and verified ✅

| Area | State | How it was verified |
|---|---|---|
| **pom deps** (Flyway core+mysql, Testcontainers, Mockito, Failsafe, datafaker, JaCoCo profile) | Done | `mvn test`: 446 tests pass |
| **Test DB seam** (`Database(HikariDataSource)`, `Emulator.setDatabaseForTesting`) | Done | both seams are package-private; no public plugin signature changed |
| **Flyway runner + fail-closed startup** (`MigrationRunner`, `SchemaPreflight`) | Done | migrations use a short-lived raw pool with the existing DB credentials; migration failure escapes `main` and closes the runtime pool |
| **V1 = Arc MS 3.5.5 baseline** (schema + data, obsolete `old_guilds_forums*` excluded) | Done | compared with the supplied `retro-hotel-files/BaseDB MS 3.5.5.sql`; four MySQL-only collations normalized for MariaDB 10.11 |
| **V2 = 23 Polaris-only tables** | Done, verified | schema diff |
| **V3 = 26 Polaris-added columns** | Done, verified | schema diff |
| **V4 = `marketplace_items` → InnoDB** | Done, verified | engine asserted InnoDB after migrate |
| **Migration authoring guide** | Done | `Emulator/src/main/resources/db/migration/README.md` |
| **Testcontainers harness + `MigrationRunnerIT`** | Done, verified | 4 IT tests pass against both pinned versions: fresh migrate/idempotency, real Arc fixture conversion/data preservation/schema convergence, unknown-DB refusal, and raw-datasource/legacy-bridge isolation |
| **CI** (unit + integration + MariaDB 10.11.14/11.4.12 LTS matrix) | Done | exact images pinned by digest; container startup failures fail CI; PRs to any branch run CI |
| **`UserFactory`** (test factory template) | Done | valid bcrypt test hash and resettable deterministic sequence |

**What migration verification proves:** a fresh database and a committed
running-hotel Arcturus fixture converge after `V1+V2+V3+V4` / baseline+`V2+V3+V4`.
The gate compares tables, complete column definitions, indexes, constraints,
engines, row formats, and collations while tolerating the two obsolete Arc-only
forum tables. It also asserts preservation of a representative user, room, item,
currency balance, and operator setting. A second migrate is a no-op.

This is **not yet a claim that V1–V4 equals the current `FullDatabase.sql` in
every definition or required Polaris reference row**. Direct comparison found
that the dump contains later/historical changes which still belong in reviewed
Phase 3 migrations. The earlier 143-table/1210-column statement proved only
name/count parity and has been retired.

## How to test locally

```bash
cd Emulator
mvn verify            # unit tests + integration tests (needs a Docker daemon)
```

- **The integration tests run locally against a throwaway MariaDB** (Testcontainers).
  Verified passing on **OrbStack** with no env vars — all 4 `MigrationRunnerIT` tests
  green. (This needed Testcontainers **1.21.4**; 1.20.6 couldn't negotiate OrbStack's
  Docker API and the tests skipped.) On any standard Docker/colima/CI it also runs.
- **Without a Docker daemon** integration tests may skip for a local developer.
  In CI, an unavailable or failed Testcontainer is a hard failure. You can also
  verify migrations directly with the Flyway plugin:

  ```bash
  mvn org.flywaydb:flyway-maven-plugin:11.10.0:migrate \
    -Dflyway.url="jdbc:mariadb://127.0.0.1:3306/scratch" -Dflyway.user=root -Dflyway.password=... \
    -Dflyway.locations="filesystem:src/main/resources/db/migration" \
    -Dflyway.placeholderReplacement=false -Dflyway.baselineOnMigrate=false
  ```
- **Coverage:** `mvn -Pcoverage verify` (JaCoCo is opt-in so it never breaks the default build).

## First-cut / needs your review ⚠️

- **`FullDatabase.sql` still hardcodes `CREATE DATABASE `habbo`; USE `habbo`;`** — a
  fresh import must target a `habbo` database or the statements go there. Unrelated
  papercut noticed during verification; worth fixing separately.
- **V1 is schema + Arc reference data.** Fresh installs get Arc's data. Polaris-specific
  reference data (its own `emulator_settings` rows, `permission_ranks`/`_definitions`
  content, catalog additions over Arc) is **not yet migrated** — that's the data-triage
  work below.
- **`marketplace_items` is InnoDB now, but the Java transaction refactor (Phase 6) is
  not done.** V4 makes the atomicity *possible*; `MarketPlace.buyItem` still needs the
  currency debit brought onto the transaction to be fully all-or-nothing (see plan §6).

## Not yet done (remaining phases)

- **Phase 3 completion — data + post-FullDatabase features.** Convert the Polaris
  reference-data inserts and the post-FullDatabase feature migrations (earnings,
  `access_token_version`, `background_border_id`, wired extensions) into `V5+`. The
  loose `Database Updates/*.sql` scripts are **intentionally retained** — do not delete
  them until this is complete and the CI schema-equivalence gate is green.
- **Phase 4 — broader compatibility fixtures.** `SchemaPreflight` currently
  recognises empty / managed / recognised-existing / unknown through eight stable
  Arc-family tables and their identity columns. Extra plugin/CMS tables and
  columns are tolerated. Add 2–3 anonymized real-world Polaris/Arc snapshots
  before claiming every hand-customized historical install is supported.
- **Phase 6/7 — behavior integration tests** (marketplace rollback, token revocation).
  These need a lightweight Emulator test-bootstrap, which doesn't exist yet.
- **Phase 8 — dev seeder + more factories** (`RoomFactory`, `BotFactory`, `db seed-dev`).
- **Operator CLI** (`db check/status/migrate`) — `MigrationRunner.status()` exists;
  the console-command wiring does not.
- **Final target-manifest gate** — the committed test proves fresh/Arc path
  convergence through V4. The final current-Polaris schema/data manifest remains
  Phase 3 and must be green before the loose SQL scripts are retired.

## Current verification

- `mvn -B test`: 446 tests, 0 failures, 0 skips.
- `MigrationRunnerIT` on pinned MariaDB 10.11.14: 4 tests, 0 failures, 0 skips.
- `MigrationRunnerIT` on pinned MariaDB 11.4.12 LTS: 4 tests, 0 failures, 0 skips.
- Direct source audit: supplied Arc dump has 122 tables; V1 retains all 120
  active tables and intentionally tolerates/preserves the two empty
  `old_guilds_forums*` leftovers during conversion.
