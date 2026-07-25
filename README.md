# Polaris

**Polaris** is a complete, self-contained hotel package: a modern Java game
server (formerly known as *Arcturus Morningstar Extended*), the Nitro browser
client, a ready-to-import database and all the assets needed to bring a hotel
online.

> ### 📢 Disclaimer
> This repository is provided as an educational resource for learning purposes
> only. The creators and contributors are not responsible for any misuse or
> unintended consequences arising from its use. By using these files you agree
> to take full responsibility for your actions and to comply with all
> applicable laws and regulations.

## Requirements

- **Emulator:** Java 21 (JDK) and Maven
- **Database:** MariaDB

## Quick start

### 1. Database

Create an empty MariaDB database and put its name and credentials in
`config.ini`. Polaris creates the schema automatically on first startup; you do
not need to import a database dump.

Already running an Arcturus Morningstar 3.5.5 or older Polaris hotel? **Make a
full database backup first**, point Polaris at that database, and start the
emulator. A recognized hotel is adopted and upgraded automatically while its
users, rooms, items, currencies, settings, and custom tables are preserved.
Polaris refuses to modify a non-empty database that does not look like an
Arcturus/Polaris hotel.

Automatic migrations are enabled by default. Advanced deployments can set
`db.migrate.on_startup=false` (or `DB_MIGRATE_ON_STARTUP=false`) after applying
migrations separately.

Deployment and troubleshooting commands:

```bash
# Apply migrations and exit without starting the hotel
java -jar Polaris-<version>-jar-with-dependencies.jar --migrations-only

# Read-only compatibility, checksum and pending-migration report
java -jar Polaris-<version>-jar-with-dependencies.jar --migrations=validate
```

`--migrations=apply` explicitly applies migrations before normal startup. There
is no interactive startup prompt, because that would hang Docker, systemd and
hosting-panel restarts.

### 2. Game server

```
cd emulator
mvn package
```

The build produces `target/Polaris-<version>-jar-with-dependencies.jar`.
Place a `config.ini` with your database credentials next to the jar (or set
the `DB_HOSTNAME`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME` and `DB_PASSWORD`
environment variables) and start it with:

```
java -jar Polaris-<version>-jar-with-dependencies.jar
```

## Plugins & backwards compatibility

Polaris keeps its promise to existing plugin authors:

- Plugin jars built for Arcturus/Morningstar **load unchanged** — the Java API
  still lives in the `com.eu.habbo.*` packages.
- Raw SQL written against pre-Polaris table names is translated on the fly by
  the **legacy bridge**. The best-known example: the old `permissions` table
  is now split into `permission_ranks` and `permission_definitions`, and the
  bridge rewrites old-style statements (such as
  `ALTER TABLE permissions ADD cmd_x ...`) into their new-schema equivalents
  automatically.

How it works, what it covers and how to configure it is documented in
[`POLARIS.md`](POLARIS.md).

## Credits

- **TheGeneral** — the original Arcturus emulator
- **The Arcturus Morningstar team** ([krews.org](https://krews.org)) — the community fork Polaris grew from
- **DuckieTM**, **simoleo89**, **Medievalshell**, **Lorenzo (the wired master)**, **Remco**, **Dennis (DennisObject)**
- **Dippy** — Improved wired architecture base
- **Seth / iSetht** — Opacity & Gravity wireds
- **Puffin** — the MyBoBBa catalogue assets, **xlRaiko** — the clothing pack

## Community

[![Discord](https://img.shields.io/badge/Discord-Join%20our%20community-5865F2?logo=discord&logoColor=white)](https://discord.gg/a2nUFkwqx)

Need help, want to contribute, or just want to chat?
