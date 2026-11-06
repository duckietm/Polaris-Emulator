# Contributing to Polaris

Polaris is used by live hotels and keeps the established Arcturus/Morningstar
integration surface compatible. Existing plugin jars must continue to load
without recompilation, and clients, CMSs, proxies, and databases must not need
coordinated changes.

## Development setup

Use JDK 25 or 26 and the Maven wrapper in `Emulator/` (any 3.x release from 3.9.11 on). Builds continue
to emit Java 25 bytecode, and release artifacts are produced on JDK 25. Docker
is needed for the Testcontainers integration suite.

```bash
./Emulator/mvnw -f Emulator/pom.xml test
./Emulator/mvnw -f Emulator/pom.xml verify
```

Run the smallest focused test first. Refactors follow a test-first workflow:
add characterization tests against the unchanged implementation, confirm they
pass, and keep them unchanged through the extraction. Bug fixes start with a
narrow failing reproduction.

New tests check behaviour, not source text: call the code and assert on what it
returns, sends or stores. A test that reads a `.java` file and looks for a
fragment (`source.contains("...")`) fails on every rename or reformat while the
behaviour is unchanged, and passes when equivalent code is written differently.
The existing ones are converted when the code they guard is next touched.

## Compatibility

- Keep public `polaris.*` classes, signatures, fields, live collections,
  plugin metadata, and plugin classloading behavior compatible. A rename of a
  public name needs its entry in `polaris/plugin/legacy-names.txt`, or old
  plugin jars stop linking.
- Preserve packet field order and types.
- Keep database migrations self-contained in Polaris and never edit a released
  migration.
- Test plugin-facing changes against the assembled jar and representative
  precompiled fixtures, not only the Maven dependency classpath.
- Do not remove or relocate bundled dependencies without a plugin-classpath
  compatibility decision.

Read `POLARIS.md` before changing plugin compatibility, the legacy SQL bridge,
or public integration behavior.

## Git and pull requests

Use `feature/`, `bugfix/`, or `refactor/` branch prefixes and Conventional
Commits. Keep each pull request reviewable, with a concise title and factual
description. Add simple manual hotel steps only when automated checks cannot
cover the behavior.
