# Polaris fixtures and contracts

Select and extend the fixture that matches the affected boundary. Do not create parallel truth sources.

## Database and migrations

- The migration chain in `Emulator/src/main/resources/db/migration/` is authoritative. `Database/` is historical/dev seed material, not a production migration source.
- `Emulator/src/test/resources/db/fixture/` represents legacy Arcturus Morningstar adoption. Use the schema and running-hotel fixtures through the existing migration integration tests; do not hand-edit them merely to conceal a migration failure.
- After an intentional migration/schema change, regenerate `Emulator/src/main/resources/db/runtime-schema-contract.json` with:

  ```bash
  ./Emulator/mvnw -f Emulator/pom.xml -Pupdate-runtime-schema-contract verify
  ```

  Review the contract diff semantically. A regenerated file is evidence of the new schema, not proof the migration is safe.
- Migration work requires `MigrationRunnerIT` across the CI MariaDB matrix and the backup/restore test described in the CI reference.

## Plugin compatibility

- `Emulator/abi-baseline/` contains the Morningstar and released-Polaris API baselines.
- `Emulator/src/test/resources/plugin-fixtures/` contains real precompiled-behavior sources and resources. `LegacyPluginFixtureCompiler` compiles them against their historical baseline, and `PackagedJarContractIT` loads them through the assembled Polaris JAR.
- Extend representative plugin fixtures for plugin-visible changes. Source/reflection assertions are supplemental; they do not replace loading precompiled fixtures through the packaged artifact.
- Never regenerate accepted ABI divergence files or their reviewed digests just to pass CI. That requires explicit maintainer approval and semantic review of every divergence.

## Packet and RCON contracts

- `protocol/packet-field-contracts.json` and `protocol/rcon-contract.json` freeze field order and types.
- Java examples under `Emulator/src/test/resources/packet-contracts/` exercise the signature extractors.
- Update a contract only for an intentional protocol change already compatible with supported clients. If compatibility is uncertain, preserve the wire format and adapt internally.

## Furni consistency fixture

Use `docs/FURNI_IMPORT_PIPELINE.md` as the canonical cross-project acceptance procedure. A furni import is one reviewed package containing:

- the intended `items_base` and `catalog_items` rows;
- the matching `FurnitureData.json` entry;
- the `.nitro` bundle and icon;
- the `.swf` when the deployment requires legacy assets;
- correct redeemable-credit logic when the classname encodes currency.

Validate the exact staged database/export and Nitro assets with `scripts/verify-furni-import.sh` or `.ps1`. Archive the JSON report. Exit `0` means consistent, `1` means findings, and `2` means the audit could not run. CI always uploads `Emulator/target/furni-consistency-fixture.json`; inspect that artifact when the gate fails. Never accept a database-only or files-only import.

## E2E and behavioral fixtures

- Use `e2e/` only with its disposable `polaris_e2e_*` schema and dedicated ports. Never point fixture setup at hotel-owner production data.
- Extend existing `*ContractTest`, characterization, lifecycle, concurrency, and packaged-JAR tests at the boundary being changed.
- Keep fixtures deterministic and minimal. Make intended domain state explicit rather than relying on defaults, local IDs, credentials, or an existing developer database.
- A changed expectation is legitimate only when the product contract intentionally changed. Explain that change in the test and review; otherwise preserve the fixture and fix the implementation.
