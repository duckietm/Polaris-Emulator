# Arcturus-Morningstar-Extended — Claude project context

Java 21 / Maven emulator for the Nitro retro Habbo (server side). Netty WS,
MariaDB + HikariCP. Entry point `com.eu.habbo.Emulator`; boot order in
`habbohotel/GameEnvironment.load()`.

## Build / test

- `cd Emulator && mvn clean package` → `target/Habbo-<ver>-jar-with-dependencies.jar`.
- `mvn -q test -Dtest=ClassName` runs a single test. JUnit 5 + surefire exist
  (added with the furnidata feature; tests under `Emulator/src/test/java/...`).
- `pom.xml` keeps `release=21` but `source/target=19` intentionally — don't "fix".

## Configuration (IMPORTANT)

- Settings load from the **DB table `emulator_settings`** (`key`/`value`) at boot
  via `ConfigurationManager.loadFromDatabase()`, NOT from `config.ini` (that holds
  the DB connection + a few keys). Changing a setting needs an emulator restart.
- **Gotcha:** `getValue(key, default)` logs `ERROR "Config key not found"` for a
  missing key EVEN when a default is supplied. Optional keys must be inserted into
  `emulator_settings` to silence the error.

## Items / furni model

- `Item.name` = `items_base.item_name` = **classname** (technical: join key to
  furnidata + asset, `isPet/isBot`, wired `wf_` interaction fallback).
- `Item.fullName` = `items_base.public_name` = **display name**.
- `Item.getDisplayName()` sources the display name from furnidata via
  `FurnitureTextProvider` (index by lowercased base classname), fallback to
  `public_name`; never null. Server-pronounced names (catalog LTD alerts, gifts,
  wired `%furni.name%`, Watch&Earn) use it. There is no server-side description.
- Live furnidata: `FurnidataWatcher` (single daemon thread, debounce + min-interval
  throttle, delta diff) broadcasts `FurnitureDataReloadComposer` (outgoing header
  **10047**) to all clients. Config keys: `items.furnidata.{names.enabled,path,
  watch.enabled,watch.debounce.ms,watch.min.interval.ms,delta.cap}`. Fail-safe:
  missing/disabled → DB `public_name`.
- **Boolean config footgun:** the furnidata code reads booleans via
  `Boolean.parseBoolean(getValue(...))`, so values must be `true`/`false` (NOT
  Arcturus' usual `1`/`0`).

## Packets

- Incoming handlers in `messages/incoming/`, outgoing composers in
  `messages/outgoing/`, header ids in `messages/outgoing/Outgoing.java`. Custom
  ids live in the 10000+ range. A new packet must be paired with the renderer
  (`../Nitro_Render_V3`) at the same header id.

## Sister projects (same DEV folder)

- `../Nitro-V3` (React client), `../Nitro_Render_V3` (renderer),
  `../NitroV3-Housekeeping` (CMS). The DEV root is NOT a git repo; each component
  is its own repo.
