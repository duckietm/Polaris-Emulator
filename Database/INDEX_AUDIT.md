# Query-driven database index contract

`Emulator/src/main/resources/db/index-contract.json` records the ordered index
prefixes required by audited emulator queries. The runtime auditor accepts an
operator or plugin index with a different name when its ordered left prefix
covers the requirement. Missing indexes are reported, but startup does not
create or remove indexes implicitly.

The timestamped Flyway migration under
`Emulator/src/main/resources/db/migration` creates only uncovered
requirements. It never drops indexes; shorter non-unique indexes covered by a
longer index are reported as review candidates. Released baseline migrations
remain immutable: fresh and existing hotels both receive this change through
the same new migration.

The MariaDB integration test creates a disposable schema, installs an
equivalent custom index, applies the Flyway migration twice, and verifies
complete coverage without duplicate creation. Set the loopback
`MIGRATION_TEST_DB_*` variables to opt into that test locally.
