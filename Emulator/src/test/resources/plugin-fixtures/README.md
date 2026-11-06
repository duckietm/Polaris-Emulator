# Legacy plugin behavior fixtures

`LegacyPluginFixtureCompiler` compiles the Morningstar fixture as Java 8
bytecode against the committed Arcturus Morningstar 3.5.5 API baseline and the
Trove 3.0.3 reference jar. It does not compile against current Polaris
classes. It also compiles a second fixture against the released Polaris API
baseline. `PackagedJarContractIT` then loads both resulting plugin jars,
individually, through the real plugin manager with only the assembled Polaris
jar as their parent classpath.

Both fixtures keep the old `com.eu.habbo` names on purpose: they stand for jars
compiled before the rename, and they load because the plugin manager rewrites
them with `polaris/plugin/legacy-names.txt` (see `RemappingPluginClassLoader`).

`polaris-plugin` is compiled against the assembled jar with the current names
(`polaris.*`, `PolarisPlugin`, `User`), so a plugin written today is proven to
load through the same plugin manager.

The fixture covers plugin metadata, lifecycle, resources, event registration,
the legacy database-data-source signature, and representative bundled
libraries. The packaged probe separately freezes released Polaris's
`THashMap extends HashMap` behavior.

The extended Morningstar Trove probe deliberately records the current linkage
gap: the assembled jar contains Polaris's partial `THashMap`, but not
`TIntObjectHashMap` or the rest of Trove. Replacing the shim with Trove 3.0.3
would fix that side while breaking the released-Polaris superclass and
inherited `Map` contract. Keep the dependency test-scoped until a bridge makes
both probes succeed without changing either established descriptor.
