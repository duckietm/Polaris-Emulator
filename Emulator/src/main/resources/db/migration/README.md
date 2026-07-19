# Polaris database migrations

Flyway migrations use timestamp versions in chronological order. The oldest,
`V20260518000000__base_database.sql`, is the canonical **Polaris base
database**. Fresh installs run the complete chain; recognised Arcturus and
existing-Polaris installs record the base version without importing it, then
run the compatible additive changes. A schema-only Arcturus 3.5.5 fixture in
the test resources proves the legacy conversion path.

After Flyway reaches the current version, Polaris validates every table and
column in the packaged runtime contract. The contract is generated from the
fully migrated schema and checked against a fresh MariaDB database in CI.
Plugin tables and custom columns remain allowed.

After adding or changing a migration, regenerate the contract from the
`Emulator` directory:

```shell
mvn -Pupdate-runtime-schema-contract verify
```

The update profile starts a throwaway MariaDB, applies the complete migration
chain using the production Flyway configuration, discovers every resulting
table and column, and atomically updates `db/runtime-schema-contract.json`.
Normal `mvn verify` is check-only and fails if a migration changed the schema
without a matching contract update.

## Automatic pre-migration backup

Before Flyway changes a recognized existing or managed hotel with pending
migrations, Polaris creates a fail-closed logical backup using the official
`mariadb-dump` client. Empty databases do not need a backup. The resulting
`*.sql.gz` archive, SHA-256 sidecar and JSON manifest are written atomically;
credentials use a short-lived owner-only option file and never appear in the
command line or metadata. A dump failure or timeout prevents Flyway from
baselining or applying anything.

The dump target is derived from the same raw JDBC datasource that Flyway will
migrate, so embedded/test launchers and plugin wrappers cannot accidentally
back up a different database through stale or incomplete config keys.

Configure the archive directory, retention, timeout and executable with the
`db.migrations.backup.*` keys in `config.ini`. The feature is enabled by
default; disable it only when an independently verified backup system protects
the same deployment.

## Rules

1. **One logical change per migration.** Never edit a released migration —
   forward-fix with a new version. Flyway records each version on success and
   never re-runs it.
2. **Versions:** use UTC `VyyyyMMddHHmmss__description.sql`, matching Laravel's
   timestamp ordering while retaining Flyway's standard `V` marker. Generate a
   new timestamp for every migration; never reuse or renumber one.
3. **MariaDB only.** Native `IF [NOT] EXISTS` is allowed (and preferred) for
   additive changes; it is not valid on MySQL 8.
4. **Guard for adoption.** A migration may meet a database that already has the
   object (an Arc converter, or a hand-applied existing-Polaris DB). Guard so it
   is a safe no-op there.
5. **Destructive / table-rebuilding steps** (`DROP`, engine change, `MODIFY` on a
   large table) document lock/runtime/space/recovery and are separately reviewed.
   MariaDB DDL is not rollback-safe — recovery is forward-fix or restore.
6. **Java migrations are exceptional.** Use one only where the source schema is
   genuinely dynamic. The permission-normalization migration uses Flyway's
   supported Java-migration API to retain custom legacy permission columns;
   ordinary schema and data changes remain SQL.

## Patterns

### 1. Additive, name is trustworthy — MariaDB one-liner

```sql
CREATE TABLE IF NOT EXISTS `wired_emulator_settings` ( ... ) ENGINE=InnoDB;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `access_token_version` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD INDEX IF NOT EXISTS `idx_users_mail` (`mail`);
```

### 2. Might already exist with a different definition — state-aware

`IF NOT EXISTS` only checks the *name*. Where a converter could hold an older
shape, check the definition and upgrade/stop rather than silently accept it:

```sql
SET @def := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='motto');
SET @ddl := CASE
    WHEN @def IS NULL                      THEN 'ALTER TABLE `users` ADD COLUMN `motto` varchar(255) NOT NULL DEFAULT ''''
    WHEN @def = 'varchar(38)'              THEN 'ALTER TABLE `users` MODIFY `motto` varchar(255) NOT NULL DEFAULT '''''
    WHEN @def = 'varchar(255)'             THEN 'DO 0'  -- already correct
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''users.motto has an unexpected type; migrate manually'''
END;
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
```

### 3. Conditional DDL (engine change, guarded ALTER) — PREPARE/EXECUTE

```sql
SET @engine := (SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='marketplace_items');
SET @ddl := IF(@engine = 'MyISAM',
    'ALTER TABLE `marketplace_items` ENGINE=InnoDB ROW_FORMAT=DYNAMIC',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
```

### Data

- **Operator-owned setting:** `INSERT ... ON DUPLICATE KEY UPDATE` of owned
  columns only, or insert-if-absent. Never blanket `INSERT IGNORE` (it hides
  truncation and unrelated key conflicts).
- **Polaris-owned reference row:** explicit upsert of the owned columns.
- **Demo/sample content:** a dev seed, not a migration.
