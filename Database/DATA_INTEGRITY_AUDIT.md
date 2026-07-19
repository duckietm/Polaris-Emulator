# Database data-integrity audit

Polaris performs a read-only duplicate and orphan audit after Flyway validates
or migrates the runtime schema. The default `warn` mode reports problems but
does not prevent the hotel from starting. The audit never deletes, merges or
repairs rows.

Coverage combines two sources:

- every physical foreign key discovered from `information_schema` at runtime,
  including constraints added by plugins;
- the versioned logical contract in
  `Emulator/src/main/resources/db/integrity-contract.json` for operational
  relations and uniqueness rules that the legacy schema does not enforce.

Historical logs are deliberately excluded from the logical contract because
they may retain identifiers after an account or room is deleted. New logical
checks must be based on an actual runtime invariant, not inferred from a column
name such as `user_id`. The pre-provisioned saved-search and window-setting
rows for the first account IDs are also excluded from orphan checks; their
duplicate-key invariants remain audited.

## Modes

Set `db.integrity.audit.mode` in `config.ini`:

- `off` disables the audit explicitly;
- `warn` logs every bounded finding and query error, then continues startup;
- `strict` aborts startup when any finding or audit error exists.

The command-line option overrides configuration for one run. A safe deployment
or CI validation command is:

```shell
java -jar Polaris-*-jar-with-dependencies.jar --migrations-only --integrity=strict
```

`--migrations=validate --integrity=strict` performs the same read-only data
audit without applying pending migrations. Use `sample_limit` to bound example
keys in logs and `query_timeout_seconds` to cap every individual check.
`max_duration_seconds` bounds the complete startup audit; reaching that budget
is an audit error and therefore blocks strict mode.

Treat strict failures as a repair backlog. Review the reported keys, back up
the database, repair them with an operator-reviewed migration or maintenance
procedure, and run strict mode again. Polaris intentionally has no automatic
repair path because choosing which duplicate row or orphaned object to retain
is domain-specific and destructive.
