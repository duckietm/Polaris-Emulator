# Implementation status — testing & migrations

Branch: `feature/testing-and-migrations`, targeting `dev`.

The production migration implementation is complete through `V30` and is proven
against real MariaDB 10.11 and 11 containers. Broader test-suite and seeder work
remains separate from the migration deliverable.

## What's implemented and verified ✅

| Area | State | How it was verified |
|---|---|---|
| **pom deps** (Flyway core+mysql, Testcontainers, Mockito, Failsafe, datafaker, JaCoCo profile) | Done | `mvn test`: 548 tests pass |
| **Test DB seam** (`Database(HikariDataSource)`, `Emulator.setDatabaseForTesting`) | Done | both seams are package-private; no public plugin signature changed |
| **Flyway runner + fail-closed startup** (`MigrationRunner`, `SchemaPreflight`) | Done | migrations use a short-lived raw pool with the existing DB credentials; migration failure escapes `main` and closes the runtime pool |
| **V1 = Arc MS 3.5.5 baseline** (schema + data, obsolete `old_guilds_forums*` excluded) | Done | compared with the supplied `retro-hotel-files/BaseDB MS 3.5.5.sql`; four MySQL-only collations normalized for MariaDB 10.11 |
| **V2 = 23 Polaris-only tables** | Done, verified | schema diff |
| **V3 = 26 Polaris-added columns** | Done, verified | schema diff |
| **V4 = `marketplace_items` → InnoDB** | Done, verified | engine asserted InnoDB after migrate |
| **V5 = state-aware permission normalization** | Done, verified | dynamic Arc/plugin columns migrate; populated Polaris normalized tables remain canonical |
| **V6–V26 = current Polaris feature updates** | Done, verified | former numbered loose updates moved into the immutable Flyway chain; destructive preview/demo actions removed |
| **V27–V29 = current `dev` updates** | Done, verified | messenger history, economy audit, and client-release contract retained without the custom runner |
| **V30 = pet-breeding reference correction** | Done, verified | known Arc off-by-one data upgraded; custom/non-legacy rows retained |
| **Custom runner replacement** | Done | the duplicate Java runner, SQL splitter, loose scripts, and `db.migrations.*` controls from `dev` are removed; Flyway is the only migration path |
| **Migration authoring guide** | Done | `Emulator/src/main/resources/db/migration/README.md` |
| **Operator controls** | Done | automatic apply remains default; `--migrations-only`, `--migrations=apply`, and read-only `--migrations=validate` |
| **Testcontainers harness + `MigrationRunnerIT`** | Done, verified | 5 IT tests: fresh/current/idempotent, real Arc fixture conversion and full schema convergence, unknown refusal, raw datasource isolation, and safe takeover from the `dev` custom runner |
| **CI** (unit + integration + MariaDB 10.11.14/11.4.12 LTS matrix) | Done | exact images pinned by digest; container startup failures fail CI; PRs to any branch run CI |
| **`UserFactory`** (test factory template) | Done | valid bcrypt test hash and resettable deterministic sequence |

**What migration verification proves:** a fresh database and a committed
running-hotel Arcturus fixture converge after `V1…V30` / baseline+`V2…V30`.
The gate compares tables, complete column definitions, indexes, constraints,
engines, row formats, and collations while tolerating the two obsolete Arc-only
forum tables. It also asserts preservation of a representative user, room, item,
currency balance, and operator setting. A second migrate is a no-op.

`FullDatabase.sql` is retained as historical evidence, not a second install
source. The migration chain additionally contains the post-dump and current-dev
features, so equality with that older dump is neither expected nor used as the
definition of correctness.

## How to test locally

```bash
cd Emulator
mvn verify            # unit tests + integration tests (needs a Docker daemon)
```

- **The integration tests run locally against a throwaway MariaDB** (Testcontainers).
  Verified passing on **OrbStack** with no env vars — all 5 `MigrationRunnerIT` tests
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

## Operational review items ⚠️

- **`FullDatabase.sql` still hardcodes `CREATE DATABASE `habbo`; USE `habbo`;`** — a
  fresh import must target a `habbo` database or the statements go there. Unrelated
  papercut noticed during verification; worth fixing separately.

## Not yet done (remaining phases)

- **Phase 4 — broader compatibility fixtures.** `SchemaPreflight` currently
  recognises empty / managed / recognised-existing / unknown through eight stable
  Arc-family tables and their identity columns. Extra plugin/CMS tables and
  columns are tolerated. Add 2–3 anonymized real-world Polaris/Arc snapshots
  before claiming every hand-customized historical install is supported.
- **Phase 6/7 — behavior integration tests** (marketplace rollback, token revocation).
  These need a lightweight Emulator test-bootstrap, which doesn't exist yet.
- **Phase 8 — dev seeder + more factories** (`RoomFactory`, `BotFactory`, `db seed-dev`).
- **Optional in-process console aliases** (`db status/migrate`) — startup/deployment
  controls are complete; aliases inside a running hotel are not necessary for safe
  adoption and can be added later if operators request them.

## Current verification

- `mvn -B test`: 548 tests, 0 failures, 0 skips.
- `MigrationRunnerIT` on pinned MariaDB 10.11.14: 5 tests, 0 failures, 0 skips.
- `MigrationRunnerIT` on pinned MariaDB 11.4.12 LTS: 5 tests, 0 failures, 0 skips.
- Direct source audit: supplied Arc dump has 122 tables; V1 retains all 120
  active tables and intentionally tolerates/preserves the two empty
  `old_guilds_forums*` leftovers during conversion.
