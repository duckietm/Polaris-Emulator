# Versioned database migrations

This directory is the authoritative source for Polaris schema migrations. Maven packages its top-level SQL files into the executable JAR; runtime migration behavior must not depend on a separate source checkout.

## Naming and ordering

- Use `NNN_lowercase_description.sql`.
- The first managed migration after the historical baseline is `028`.
- Every new version must use the next contiguous number. Do not fill the historical 013/014 gaps.
- Descriptions for version 028 and later use only lowercase ASCII letters, digits and underscores.
- Keep migration files at the top level of this directory.

Applied migrations and their `schema_migrations` rows are immutable: they must not be edited, renamed, reordered or assigned a replacement checksum after merge. Add a new corrective migration instead.

## SQL author checklist

Before opening a pull request:

1. Choose the next contiguous version and a lowercase filename.
2. Write retry-safe MariaDB SQL. MariaDB can auto-commit DDL, so a partially failed script may be retried.
3. Do not use `DELIMITER`, stored-procedure bodies or another syntax unsupported by the migration splitter.
4. Keep each statement deterministic and avoid production-specific data or credentials.
5. Test explicit apply twice against a disposable existing database; the second run must be a no-op.
6. Test a clean installation from `Database/Default Database/FullDatabase.sql`.
7. When clean installs require the new schema immediately, update `FullDatabase.sql` in the same change.
8. Confirm failure leaves the migration pending and does not insert its history row.
9. Never log passwords or interpolate operator-provided identifiers into SQL.

The runner computes the SHA-256 checksum from the exact packaged UTF-8 file. Changing whitespace or comments after application also changes the checksum and correctly blocks startup for review.

## Local apply-only command

Build the fat JAR, provide loopback database environment variables, and run:

```powershell
java -cp Emulator/target/Polaris-4.2.50-jar-with-dependencies.jar com.eu.habbo.database.migrations.MigrationTool --migrations=apply --migrations-only
```

Use `validate` during normal startup. Use `off` only as an explicit emergency compatibility escape hatch because it disables migration guarantees.
