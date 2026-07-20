# Polaris — the emulator rename & legacy plugin compatibility

The Emulator formerly known as *Arcturus Morningstar Extended* is now called
**Polaris**. This fork has grown far beyond its origins — a new name makes
that clear without abandoning the plugins, tools and knowledge the community
has built up over the years.

This document covers two things: what the rename actually changed, and how the
**legacy bridge** keeps old plugins running against the new database schema.

## What changed

| Surface | Before | After |
|---|---|---|
| Product name / version string | `Arcturus Morningstar Extended 4.x` | `Polaris 4.x` |
| Maven artifact | `com.eu.habbo:Habbo` | `com.eu.habbo:Polaris` |
| Built jar | `Habbo-<v>-jar-with-dependencies.jar` | `Polaris-<v>-jar-with-dependencies.jar` |
| Console banner, GUI dashboard, housekeeping version | Arcturus branding | Polaris branding |

## What stays the same — on purpose

Existing plugin jars must keep loading without a recompile, so the binary API
surface is untouched:

- The Java packages stay `com.eu.habbo.*` — `HabboPlugin`, `Emulator`, the
  event system, everything a plugin compiles against.
- `Emulator.getDatabase().getDataSource()` still returns a `HikariDataSource`.
- The `plugin.json` format and the `plugins/` folder work exactly as before.

In short: if your plugin ran on Morningstar Extended, drop the same jar into
`plugins/` and it runs on Polaris.

## The legacy bridge

Renaming the server was the easy part. The harder problem is the database:
several structures were renamed or restructured along the way, and old plugins
talk to the database with raw SQL. The most important change is the legacy
`permissions` table, which is now split into:

- `permission_ranks` — one row per rank (metadata such as `rank_name`, `badge`, …)
- `permission_definitions` — one row per permission key, with one
  `rank_<id>` column per rank

The legacy bridge solves this transparently. It wraps every pooled database
connection, and each SQL statement passes through a chain of translators
before it reaches the database. Statements written against the old names are
rewritten into their new-schema equivalents on the fly; modern SQL passes
through with virtually zero overhead.

### Permissions translation (automatic)

Active only when the database uses the normalized permissions schema — on a
legacy database, everything passes through untouched. Examples of what old
plugins send and what actually runs:

| Legacy statement from an old plugin | Executed as |
|---|---|
| `ALTER TABLE permissions ADD cmd_x ENUM('0','1') NOT NULL DEFAULT '0'` | `INSERT INTO permission_definitions (permission_key, max_value, comment) VALUES ('cmd_x', 1, …)` |
| `ALTER TABLE permissions DROP COLUMN cmd_x` | `DELETE FROM permission_definitions WHERE permission_key IN ('cmd_x')` |
| `UPDATE permissions SET cmd_x = '1' WHERE id >= 5` | `UPDATE permission_definitions SET rank_5 = CASE …, rank_6 = CASE … WHERE permission_key IN ('cmd_x')` |
| `SELECT cmd_x FROM permissions WHERE id = 5` | `SELECT MAX(CASE WHEN permission_key = 'cmd_x' THEN rank_5 END) AS cmd_x FROM permission_definitions` |
| `SELECT * FROM permissions`, or metadata columns only | redirected to `permission_ranks` |
| `INSERT INTO permissions (id, rank_name, …)`, `DELETE FROM permissions WHERE id = 7` | redirected to `permission_ranks` |

Legacy semantics are preserved where plugins depend on them. The classic
pattern — try to `ALTER TABLE permissions ADD …` and treat an `SQLException`
as "permission already registered" — still behaves identically, because
inserting an existing `permission_key` also throws. The bundled
`Plugins/Wordguesser` example uses exactly this pattern and runs unmodified.

Statements the bridge cannot map *safely* are deliberately left alone rather
than guessed at: prepared-statement placeholders (`?`) in a legacy `WHERE`,
statements mixing rank metadata with permission columns, and
`CHANGE`/`MODIFY` clauses all pass through untouched.

### Plain table renames (configurable)

Tables that were renamed 1:1 don't need code — add the mapping to
`config.ini` (or emulator_settings) and the bridge handles it:

```
polaris.legacy.bridge.table_renames=old_table:new_table;other_old:other_new
```

The old name is only replaced in *table position* (after `FROM`, `JOIN`,
`INTO`, `UPDATE`, `TABLE`, …), so columns that happen to share the name are
left alone. Changes to this setting are picked up without a restart.

### Configuration

| Key | Default | Meaning |
|---|---|---|
| `polaris.legacy.bridge.enabled` | `1` | Master switch for the bridge. |
| `polaris.legacy.bridge.log` | `1` | Log each distinct legacy statement the bridge translates — a handy way to spot outdated plugins. |
| `polaris.legacy.bridge.table_renames` | *(empty)* | Extra 1:1 table renames, `old:new;old2:new2`. |

Every distinct translation is logged once, so hotel owners can see exactly
which plugins still speak the old dialect:

```
Polaris legacy bridge -> translated legacy SQL from an old plugin.
    old: ALTER TABLE `permissions` ADD `cmd_randomword` ENUM('0','1') ...
    new: INSERT INTO permission_definitions (permission_key, max_value, comment) VALUES ('cmd_randomword', 1, ...)
```

## Plugin event dispatch

Plugin events keep the legacy unordered, all-handlers behavior by default.
Hotels can opt into the corrected annotation contract with:

```
polaris.events.honor_priority=true
```

In corrected mode, handlers run from `LOWEST` through `MONITOR`, and a handler
with `ignoreCancelled = true` is skipped once the event is cancelled. Reloads
publish a complete handler table atomically, so dispatch observes either the
old table or the new one rather than a partially rebuilt set.

### Writing new plugins

The bridge exists for backwards compatibility — it is not the recommended
API. New code should target the new schema directly (`permission_ranks`,
`permission_definitions`). If your plugin needs its own translation rules,
register a custom translator at runtime:

```java
Emulator.getDatabase().getLegacySqlBridge().registerTranslator(myTranslator);
```

### Implementation map

| Class (in `com.eu.habbo.database.compat`) | Role |
|---|---|
| `LegacySqlBridge` | Connection wrapping, translator chain, once-per-statement logging, config. |
| `LegacyPermissionsSqlTranslator` | Semantic translation of the old `permissions` table. |
| `LegacyTableRenameTranslator` | Config-driven 1:1 table renames. |
| `LegacyBridgeDataSource` | `HikariDataSource` subclass that hands out bridged connections. |
| `TranslationContext` / `LegacySqlTranslator` | The interfaces custom translators implement against. |
