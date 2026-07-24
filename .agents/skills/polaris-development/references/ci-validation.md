# Polaris CI and local validation

Read `.github/workflows/ci.yml`, `.github/workflows/codeql.yml`, `Emulator/pom.xml`, and relevant scripts from current `origin/dev` before choosing commands. These files override this reference if the pipeline evolves.

## Baseline environment

- Use JDK 25 for exact parity with the main build-and-test job. Current Polaris also permits JDK 26 and CI runs a separate JDK 26 clean-package compatibility job; verify the current range in `Emulator/pom.xml`.
- Use the committed Maven wrapper. From the repository root, invoke `./Emulator/mvnw -f Emulator/pom.xml ...`.
- Docker is required for Testcontainers integration tests.
- CI formatting is ratcheted from the PR base. Check only the changed surface; do not reformat unrelated legacy code.

## Validation ladder

Run the narrowest useful test first, then expand:

```bash
./Emulator/mvnw -f Emulator/pom.xml test -Dtest=<TestClass[#method]>
./Emulator/mvnw -f Emulator/pom.xml test
./Emulator/mvnw -B -f Emulator/pom.xml -Pcoverage verify -Dspotless.ratchetFrom=origin/dev
```

The last command is the closest local equivalent of the primary GitHub CI job: compile, unit tests, Failsafe integration tests, packaging/contract checks, coverage, Spotless, and configured analysis. Run it before merge readiness unless the task is documentation-only and cannot affect executable or workflow behavior.

Also run the repository's build-handoff test when build/release scripts, Maven packaging, or artifact handoff changes:

```bash
./scripts/build-latest.test.sh
```

## Change-specific gates

| Change area | Additional required evidence |
|---|---|
| Migration, schema, DB compatibility | Regenerated runtime schema contract when appropriate; `MigrationRunnerIT` on MariaDB 10.11, 11.4, and 12.3; migration backup/drop/restore test |
| Plugin API, classloading, resources, dependencies | ABI tests, historical plugin fixtures, and `PackagedJarContractIT` against the assembled JAR |
| Packet or RCON | Relevant contract catalog/coverage tests and byte/field-order compatibility tests |
| Networking, concurrency, lifecycle | Focused race/lifecycle reproduction plus full `verify`; repeat or stress the flaky boundary when useful |
| Packaging, shading, service loading | JDK 25 full verify, isolated runnable-JAR contract, and JDK 26 `clean package` compatibility |
| Furni import or assets | Canonical furni verification script against the exact staged SQL/export and Nitro tree; retain the JSON report |
| E2E renderer behavior | Emulator fixture tests plus the companion renderer E2E path documented under `e2e/` |
| Workflow or build configuration | Validate YAML/scripts, run the affected local command, and inspect the resulting artifact rather than relying on syntax alone |

Run the migration matrix command with the current image value copied from `.github/workflows/ci.yml`, for example:

```bash
POLARIS_TEST_MARIADB_IMAGE='<current-ci-image>' \
  ./Emulator/mvnw -B -f Emulator/pom.xml test-compile \
  failsafe:integration-test failsafe:verify -Dit.test=MigrationRunnerIT
```

GitHub also runs CodeQL separately. Treat a local compile or unit pass as insufficient evidence for security-analysis status.

## Completion report

State:

- base SHA and final relationship to current `origin/dev`;
- focused and broad commands run, with pass/fail counts where available;
- fixtures/contracts added or intentionally reused;
- CI-only gates still pending;
- any unavailable Docker, JDK, OS, service, client, plugin corpus, or production-like evidence;
- residual risk that tests do not prove.
