# Plugin ABI baselines

Plugins are loaded from `plugins/` with a `URLClassLoader` that delegates to the
emulator's own classloader (`com.eu.habbo.plugin.PluginManager`). That means plugin
jars link against the **entire public/protected surface** of the emulator at the
bytecode level — there is no separate "plugin API". That surface is a frozen ABI:
it may grow, it must never break. This directory makes that rule mechanically
enforced instead of aspirational.

There are two baselines, enforced by `PluginAbiCompatibilityTest` (a unit test,
so it runs on every `mvn test` / `mvn verify` and in CI):

1. **Arcturus Morningstar 3.5.5** (`arcturus-morningstar-3.5.5-api.jar`) — the
   ancestor release most third-party plugin jars were compiled against.
   Polaris had already diverged from it in ~450 member signatures before this
   gate existed; that historical divergence is pinned in
   `accepted-divergence-morningstar.txt` and must not grow.
2. **The latest released Polaris jar** (`polaris-release-api.jar`) — plugins
   compiled against Polaris itself may use API added since 3.5.5, so every
   *shipped* Polaris surface is equally frozen. Its accepted list
   (`accepted-divergence-polaris.txt`) is kept **near-empty by policy**: only
   internal machinery that no plugin could plausibly touch may appear there
   (currently: the v4.2.60→dev restructure of the internal
   `database.migrations`/`database.schema` packages).

## How the gates work

The test packages `target/classes` into a jar and compares it against each
baseline with [japicmp](https://siom79.github.io/japicmp/), covering the public
and protected surface. Every binary-incompatible difference (removed/renamed
method, changed field type, changed superclass, added `final`, static↔instance
flip, …) becomes a stable one-line token, e.g.

```
method com.eu.habbo.habbohotel.rooms.Room#getUserCount() METHOD_REMOVED
```

Any token not covered by the baseline's tolerance (the accepted list for 3.5.5,
nothing for the Polaris baseline) fails the test with a message listing exactly
what broke.

Note that *binary* compatibility is stricter than source compatibility: e.g.
changing a return type to a supertype, or replacing a public field with a getter,
compiles fine against existing plugin *source* but crashes existing plugin *jars*
with `NoSuchMethodError`/`NoSuchFieldError` at runtime.

## When a gate fails

Restore the old signature and delegate to your new code instead of changing it.
The playbook that is always ABI-safe:

- add new classes, methods, and overloads;
- change method *bodies* freely;
- change anything `private`/package-private freely;
- keep old public/protected members as thin forwarders to the new structure;
- deprecate with `@Deprecated` (never remove).

## Maintenance (maintainer approval required)

Re-pin the accepted-divergence lists (per policy this should essentially never
happen outside a release — the committed diff review *is* the approval):

```
mvn test -Dtest=PluginAbiCompatibilityTest -Dpolaris.abi.regenerate=true
```

**After each release**, update the Polaris baseline to the newly shipped jar so
API added in that release becomes protected for plugins compiled against it.
Because additions are binary-compatible, this never loosens the gate:

```
gh release download vX.Y.Z -p "Polaris-*-jar-with-dependencies.jar" -D /tmp/polaris-release
cd $(mktemp -d) && unzip -q /tmp/polaris-release/Polaris-*-jar-with-dependencies.jar "com/eu/habbo/*"
jar --create --file <repo>/Emulator/abi-baseline/polaris-release-api.jar com
```

then regenerate the accepted lists (the polaris one should collapse to empty)
and record the release version + jar sha256 in this README's provenance section.

## Out of scope

Third-party libraries shaded into the emulator jar (Netty, Gson, …) are also
visible to plugins, but their versions have already been bumped far past 3.5.5.
Plugins linking those directly are outside these gates' guarantees.

## Baseline provenance

- `arcturus-morningstar-3.5.5-api.jar`: `com/eu/habbo/**` (2016 classes)
  extracted from the shipped compiled release `Habbo-3.5.5-jar-with-dependencies.jar`
  (sha256 `5eae6985088e7a974fe2b778c8be30b2bb40b595676d246809891727dee0a0a4`),
  supplied 2026-07-18. Note: the shipped build is a superset of the `3-5-5`
  source tag (it additionally contains WebSocket/SSL support classes).
  Baseline jar sha256 `e8bb43388606f54f45a2ace7880d70ca96b6bacff1b71fa3a83a1f83e692b368`.
- `polaris-release-api.jar`: `com/eu/habbo/**` (2967 classes) extracted from the
  official GitHub release asset `Polaris-4.2.60-jar-with-dependencies.jar`
  (sha256 `460266d4b6d10821ae970f734dd679d8239ea4ea853323571edb8b4c39912532`,
  verified against the published `.sha256` asset), release v4.2.60, 2026-07-18.
  Baseline jar sha256 `e6de9dda00528eaccc31defcc05c01ff9c7edc4172d6a25111ae0478eba047e5`.
