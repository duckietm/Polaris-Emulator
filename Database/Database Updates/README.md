# Legacy database updates

The numbered production updates formerly stored at the top of this directory
are now immutable Flyway migrations in:

`Emulator/src/main/resources/db/migration/`

Polaris applies them automatically. **Do not run this directory manually on a
new or existing hotel.**

The remaining subdirectories are retained as historical source material:

- `Own_Database_RunFirst/` contains old aggregates, duplicate scripts and manual
  experiments whose required effects were reconciled into the timestamped chain.
- `Items_Base/` contains old bulk reference-data maintenance scripts. The Polaris
  base database already contains their stable mappings; later wired mappings and the
  pet-breeding correction are versioned migrations.
- `Set Rooms wallitems/` is an optional repair script, not a Polaris schema
  migration.

The destructive room-207 wired laboratory script was moved to
`Database/Dev Seeds/room_207_wiredlab.sql`; it is development-only.

Future production database changes must be added as a new Flyway migration.
Use a new UTC timestamp and never edit a released migration.
