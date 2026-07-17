# Implementation status — testing & migrations

Branch: `feature/testing-and-migrations` (off `main`). **Not pushed** — for local
review and testing first. Companion to `TESTING_AND_MIGRATIONS_PLAN.md`.

This is a foundation-first, verified first-cut. The core migration pipeline is
**proven against a real MariaDB**; the larger data/behavior work is scaffolded and
flagged for your review, since that's where local testing and judgement are needed.

## What's implemented and verified ✅

| Area | State | How it was verified |
|---|---|---|
| **pom deps** (Flyway core+mysql, Testcontainers, Mockito, Failsafe, datafaker, JaCoCo profile) | Done | `mvn verify` builds; 445 unit tests pass |
| **Test DB seam** (`Database(HikariDataSource)`, `Emulator.setDatabaseForTesting`) | Done | compiles; no public signature changed |
| **Flyway runner + fail-closed startup** (`MigrationRunner`, `SchemaPreflight`) | Done | see migration verification below |
| **V1 = Arc MS 3.5.5 baseline** (schema + data, obsolete `old_guilds_forums*` excluded) | Done | applied via Flyway to a throwaway MariaDB 11 |
| **V2 = 23 Polaris-only tables** | Done, verified | schema diff |
| **V3 = 26 Polaris-added columns** | Done, verified | schema diff |
| **V4 = `marketplace_items` → InnoDB** | Done, verified | engine asserted InnoDB after migrate |
| **Migration authoring guide** | Done | `Emulator/src/main/resources/db/migration/README.md` |
| **Testcontainers harness + `MigrationRunnerIT`** | Done | runs in CI; skips locally without Docker |
| **CI** (unit + integration + MariaDB 10.11/11 matrix) | Done | `.github/workflows/ci.yml` |
| **`UserFactory`** (test factory template) | Done | insert column set verified against real schema |

**Migration verification (Flyway, real MariaDB 11):** a fresh database migrated
with `V1+V2+V3+V4` reproduces the current Polaris schema **exactly — 143/143
tables, 1210/1210 columns, zero drift** — the chain is **idempotent** (second
`migrate` is a no-op), and it applies cleanly on a **data-populated Arc database**
(the converter path). Two real bugs were caught by this and fixed: V4 needed to
reset `ROW_FORMAT=FIXED` (InnoDB rejects it), and Flyway placeholder replacement
had to be disabled because the reference data contains `${image.library.url}`
template strings.

## How to test locally

```bash
cd Emulator
mvn verify            # unit tests + integration tests (needs Docker for the IT)
```

- **Integration tests skip (not fail) without Docker.** On my machine Testcontainers
  couldn't negotiate with **OrbStack** (docker-java pins API 1.32; OrbStack requires
  ≥1.40), so `MigrationRunnerIT` is `assumeTrue`-guarded and reports *skipped*. On
  standard Docker Desktop / colima / GitHub runners it runs normally. If you also hit
  the OrbStack issue, verify migrations directly with the Flyway plugin:

  ```bash
  # against any throwaway MariaDB you control
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
- **Phase 4 — deeper adoption preflight.** `SchemaPreflight` currently recognises
  empty / managed / recognised-existing / unknown via a minimal invariant set. The
  per-object fingerprinting and the Arc-vs-existing-Polaris split still need real
  production dumps to design against (gather 2–3).
- **Phase 6/7 — behavior integration tests** (marketplace rollback, token revocation).
  These need a lightweight Emulator test-bootstrap, which doesn't exist yet.
- **Phase 8 — dev seeder + more factories** (`RoomFactory`, `BotFactory`, `db seed-dev`).
- **Operator CLI** (`db check/status/migrate`) — `MigrationRunner.status()` exists;
  the console-command wiring does not.
- **Schema-equivalence CI gate** — proven manually; not yet a committed CI job.

## Commits on this branch

1. docs: the plan (Flyway free-tier, Arc baseline)
2. build: deps + test DB seam
3. feat(db): Flyway runner + fail-closed startup + preflight
4. feat(db): V1 Arc baseline + verified V2–V4 deltas + authoring guide
5. test(db): Testcontainers harness + migration IT; disable Flyway placeholders
6. ci: unit + integration + MariaDB matrix; `UserFactory`; this status doc
