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

The main class extends `polaris.plugin.PolarisPlugin` and implements
`onEnable`, `onDisable`, and `hasPermission`. Keep plugin-owned resources in
the jar and load them through the plugin classloader.

### Plugins compiled against the old names

Before the rename the emulator lived in `com.eu.habbo.*`, the base class was
`HabboPlugin` and users were `Habbo` (`getHabbo()`, `HabboInfo`, `HabboItem`...).
Those jars still load without a recompile: the plugin manager rewrites each
class as it loads it, using `polaris/plugin/legacy-names.txt` in the emulator
jar. Packages, renamed classes, renamed methods and the renamed packet
constants are all translated; a method of the plugin's own is renamed only when
its class extends or implements an emulator class. String constants that are
exactly an old class name or an old method name are translated too, so
`Class.forName("com.eu.habbo...")` and `getMethod("getHabbo")` keep working.
Names built at runtime (string concatenation) cannot be translated.

## Permissions

`hasPermission(User, String)` is not a check the plugin runs: it is the emulator asking the plugin. Whenever a
user's rank (and their own permission overrides) deny a key, every installed plugin is asked whether it grants that
key anyway, and the client's permission map is built the same way. Return `true` only for keys the plugin owns and
decides, and `false` for everything else:

```java
@Override
public boolean hasPermission(User user, String key) {
    return key.equals("cmd_example") && isExampleModerator(user);
}
```

Do not answer with `user.hasPermission(key)`: that asks the emulator, which asks the plugins again. Polaris answers
such a nested question from the rank alone, so it ends instead of overflowing the stack, but it can never grant
anything. To check a permission the plugin needs for its own command, call `user.hasPermission` from the command,
not from this method.

## Talking to your own client panel

A plugin and a panel in the client exchange JSON on a channel of their own, without packets of their own:

```java
PluginChannel.send(user, "casino.table", "{\"open\":true,\"bet\":10}");

@EventHandler
public static void onPanel(UserPluginChannelEvent event) {
    if (!event.channel.equals("casino.table")) return;
    // event.payload is what the panel sent; validate it like any player input.
}
```

On the client, a panel listens with `onChannel("casino.table", data => ...)` and answers with
`sendChannel("casino.table", { action: "spin" })`, both on `window.OctanePlugins`.

- Name channels after the plugin, lower case: letters, digits, `.`, `-`, `_`, at most 64 characters.
- A payload is at most 16384 characters either way. `PluginChannel.send` returns false and sends nothing
  for a bad channel, an oversized payload or an offline user.
- The emulator drops a client message with a bad channel or payload, and limits how often a client sends
  them. Everything else reaches the plugins as `UserPluginChannelEvent`; the content is yours to check.
- On the wire these are `PLUGIN_CHANNEL_MESSAGE` (10200) and `PLUGIN_CHANNEL_ACTION` (10201), in the
  `plugin_channel` range of the packet contract.

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

## Game managers and live collections

Several game managers keep an in-memory collection of loaded state alive for
the lifetime of the hotel. `CatalogManager` is one of them: its public catalog
pages and items stay mutable in place, so existing code that walks or edits
them directly keeps working. Internally, lookups are now served from a
snapshot that is rebuilt only when `catalogVersion()` moves - not just catalog
pages and items, but vouchers, clothing and club items too. A plugin that
adds, removes, or moves catalog pages or items directly - rather than going
through `CatalogAdminCacheSync`, `CatalogManager.moveCatalogItem`, or a full
catalog reload, all of which already call it - must call
`CatalogManager.markCatalogChanged()` afterwards so the snapshot picks up the
edit.

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
- Do not bundle classes in `polaris.*` (or the old `com.eu.habbo.*`) or shadow server dependencies.
- Treat bundled third-party libraries as compatibility-sensitive, not as a
  promise that every library will remain forever.
- Test the precompiled jar against the assembled
  `Polaris-*-jar-with-dependencies.jar`.
- Keep packet reads and writes aligned with the established client contract.

See `POLARIS.md` for the legacy bridge and compatibility details.
