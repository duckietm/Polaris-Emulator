# Polaris plugin author guide

Polaris loads plugin jars from `plugins/` through the established
parent-first plugin classloader. Existing Arcturus/Morningstar plugin jars are
supported without recompilation.

## Plugin metadata

Place `plugin.json` at the root of the jar:

```json
{
  "name": "Example plugin",
  "author": "Hotel developer",
  "main": "example.polaris.ExamplePlugin"
}
```

The main class extends `com.eu.habbo.plugin.HabboPlugin` and implements
`onEnable`, `onDisable`, and `hasPermission`. Keep plugin-owned resources in
the jar and load them through the plugin classloader.

## Events

Register listeners through the plugin manager and annotate callbacks with
`@EventHandler`. Legacy all-handler dispatch remains the default. Hotels may
opt into priority ordering and cancellation-aware dispatch with
`polaris.events.honor_priority=true`; plugins should behave correctly in both
modes unless they explicitly require the corrected mode.

## Database access

`Emulator.getDatabase().getDataSource()` remains a public
`HikariDataSource`-compatible API. New plugins should use the current schema
and prepared statements. The legacy SQL bridge supports a defined subset of
older table shapes, but unsafe translations deliberately pass through.

Plugin-owned tables and columns are allowed. Do not edit Polaris migrations;
ship separate idempotent setup owned by the plugin.

## Bundled dependency policy

The assembled jar currently keeps `netty-all` on the plugin-visible classpath.
Slimming that surface would require the precompiled plugin corpus to prove
compatibility first. The same rule keeps `resilience4j-circuitbreaker` even
where Polaris has no direct import.

Polaris deliberately uses Hibernate Validator without an Expression Language
implementation and configures `ParameterMessageInterpolator`; plugins must not
assume that the default EL-backed validator factory is available. Commons Math
3.6.1 remains in use for compatibility and is tracked for opportunistic
replacement rather than removal during routine modernization.

## Compatibility guidance

- Compile against a released Polaris API or the intended Morningstar baseline.
- Do not bundle classes in `com.eu.habbo.*` or shadow server dependencies.
- Treat bundled third-party libraries as compatibility-sensitive, not as a
  promise that every library will remain forever.
- Test the precompiled jar against the assembled
  `Polaris-*-jar-with-dependencies.jar`.
- Keep packet reads and writes aligned with the established client contract.

See `POLARIS.md` for the legacy bridge and compatibility details.
