package com.eu.habbo.database.migration;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.backup.MariaDbMigrationBackup;
import com.eu.habbo.database.backup.MigrationBackup;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Runs Flyway migrations.
 *
 * <p>Safety invariants:
 * <ul>
 *   <li>Runs against the <b>raw</b> {@link HikariDataSource}, never the
 *       {@code LegacySqlBridge}-wrapped path, so migration DDL is not rewritten.</li>
 *   <li>Config is read from {@code config.ini}/environment only (never from
 *       {@code emulator_settings}, which lives in the very DB being migrated).</li>
 *   <li>Fail-closed: any preflight/validation/migration failure throws
 *       {@link MigrationException} so startup aborts.</li>
 *   <li>{@code baselineOnMigrate=false}: Polaris only baselines a database the
 *       preflight has explicitly recognised.</li>
 * </ul>
 */
public final class MigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

    public static final String MIGRATION_LOCATION = "classpath:db/migration";
    /** Existing installs record this version so the clean-install database is never imported over hotel data. */
    public static final String BASELINE_VERSION = "20260518000000";

    private static final String KEY_ON_STARTUP = "db.migrate.on_startup";

    private MigrationRunner() {
    }

    /** Startup entry point, called before database-backed configuration is loaded. */
    public static void runAtStartup(HikariDataSource runtimeDataSource, ConfigurationManager config) {
        if (!config.getBoolean(KEY_ON_STARTUP, true)) {
            LOGGER.warn("[migrate] {}=false — skipping automatic schema migration. The operator is responsible for schema state.", KEY_ON_STARTUP);
            return;
        }

        migrateAtStartup(runtimeDataSource, config);
    }

    /** Applies migrations immediately, regardless of the startup config switch. */
    public static MigrateResult migrateAtStartup(
            HikariDataSource runtimeDataSource,
            ConfigurationManager config) {
        // The runtime datasource rewrites legacy plugin SQL. Migrations require an
        // unwrapped pool so their DDL cannot be silently translated.
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            return migrate(
                    rawMigrationDataSource,
                    MariaDbMigrationBackup.resolve(config, rawMigrationDataSource));
        }
    }

    /** Read-only status/validation using the same raw connection invariant. */
    public static String statusAtStartup(HikariDataSource runtimeDataSource) {
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            return status(rawMigrationDataSource);
        }
    }

    /**
     * Realigns the Flyway schema history with the packaged migrations without
     * touching hotel data, using the same raw connection invariant.
     *
     * <p>Recovers a database whose {@code flyway_schema_history} drifted from
     * the migration files - most commonly when an already-applied migration was
     * edited upstream, so its recorded checksum no longer matches. Flyway's
     * {@code repair()} rewrites those checksums (and clears any failed rows) in
     * place, so the next normal startup can migrate again. This replaces the
     * destructive "delete every history row and re-run everything" workaround.
     */
    public static void repairAtStartup(HikariDataSource runtimeDataSource) {
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            repair(rawMigrationDataSource);
        }
    }

    /** Runs the action permitted for the detected schema state. */
    public static MigrateResult migrate(DataSource dataSource) {
        return migrate(dataSource, MigrationBackup.disabled());
    }

    static MigrateResult migrate(DataSource dataSource, MigrationBackup migrationBackup) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        Flyway flyway = flyway(dataSource);

        LOGGER.info("[migrate] Detected schema state: {}", state);
        try {
            if (state != SchemaPreflight.State.EMPTY && state != SchemaPreflight.State.UNKNOWN) {
                java.util.List<String> pending = java.util.Arrays.stream(flyway.info().pending())
                        .filter(migration -> !isBaselineSkippedDuringAdoption(state, migration))
                        .map(migration -> migration.getVersion() == null
                                ? migration.getDescription()
                                : migration.getVersion().getVersion())
                        .toList();
                if (!pending.isEmpty()) migrationBackup.beforeMigrations(pending);
            }
            MigrateResult result = switch (state) {
                case UNKNOWN -> throw new MigrationException(
                        "Refusing to migrate: the database is non-empty but is not a recognised Arc/Polaris schema. "
                                + "No changes were made. Check that db.database points at the correct database, or see the "
                                + "conversion/recovery instructions before proceeding.");
                case RECOGNISED_EXISTING -> {
                    LOGGER.warn("[migrate] Existing Arcturus/Polaris hotel detected with no migration history. "
                            + "Polaris will preserve the hotel data, record adoption baseline V{}, and apply the required upgrades now. "
                            + "A full database backup before first startup is strongly recommended.", BASELINE_VERSION);
                    flyway.baseline();
                    yield flyway.migrate();
                }
                case EMPTY, MANAGED -> flyway.migrate();
                default -> throw new MigrationException("Unhandled schema state: " + state);
            };
            RuntimeSchemaValidator.validate(dataSource);
            LOGGER.info("[migrate] Runtime schema invariants validated.");
            return result;
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration failed; the emulator will not start against a half-upgraded schema. "
                    + "Inspect the error, restore from backup or forward-fix, then retry.", e);
        }
    }

    /** Read-only summary for an operator command. Never baselines or migrates. */
    public static String status(DataSource dataSource) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        StringBuilder out = new StringBuilder();
        out.append("Schema state: ").append(state).append('\n');

        if (state == SchemaPreflight.State.UNKNOWN) {
            out.append("Compatible: no; Polaris will not modify this database.\n");
            return out.toString();
        }

        try {
            Flyway flyway = flyway(dataSource);
            if (state == SchemaPreflight.State.MANAGED) {
                flyway.validate();
            }

            MigrationInfoService info = flyway.info();
            MigrationInfo current = info.current();
            out.append("Current version: ").append(current == null ? "(none)" : current.getVersion()).append('\n');
            if (state == SchemaPreflight.State.RECOGNISED_EXISTING) {
                out.append("Adoption: record baseline V").append(BASELINE_VERSION).append('\n');
            }

            MigrationInfo[] pending = info.pending();
            int pendingCount = 0;
            for (MigrationInfo migration : pending) {
                if (!isBaselineSkippedDuringAdoption(state, migration)) {
                    pendingCount++;
                }
            }
            out.append("Pending migrations: ").append(pendingCount).append('\n');
            for (MigrationInfo migration : pending) {
                if (!isBaselineSkippedDuringAdoption(state, migration)) {
                    out.append("  - V").append(migration.getVersion()).append(' ')
                            .append(migration.getDescription()).append('\n');
                }
            }
            // A fully migrated database must also satisfy the runtime contract, so
            // --migrations=validate detects manual schema drift, not just history drift.
            if (state == SchemaPreflight.State.MANAGED && pendingCount == 0) {
                RuntimeSchemaValidator.validate(dataSource);
                out.append("Runtime schema: compatible\n");
            }
            return out.toString();
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration status failed; the schema history could not be "
                    + "validated against the packaged migrations. No changes were made.", e);
        }
    }

    /** Repairs the schema history in place. Never migrates or baselines. */
    public static void repair(DataSource dataSource) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        LOGGER.info("[migrate] Detected schema state: {}", state);

        if (state == SchemaPreflight.State.UNKNOWN) {
            throw new MigrationException(
                    "Refusing to repair: the database is non-empty but is not a recognised Arc/Polaris schema. "
                            + "No changes were made.");
        }
        if (state == SchemaPreflight.State.EMPTY || state == SchemaPreflight.State.RECOGNISED_EXISTING) {
            LOGGER.warn("[migrate] No Flyway schema history to repair (state {}); nothing to do.", state);
            return;
        }

        try {
            Flyway flyway = flyway(dataSource);
            flyway.repair();
            LOGGER.info("[migrate] Schema history repaired: recorded checksums realigned with the packaged "
                    + "migrations and any failed rows cleared. Run a normal startup to apply pending migrations.");
        } catch (Exception e) {
            throw new MigrationException("Migration repair failed; the schema history could not be realigned "
                    + "with the packaged migrations. No hotel data was changed.", e);
        }
    }

    private static boolean isBaselineSkippedDuringAdoption(
            SchemaPreflight.State state,
            MigrationInfo migration) {
        return state == SchemaPreflight.State.RECOGNISED_EXISTING
                && migration.getVersion() != null
                && BASELINE_VERSION.equals(migration.getVersion().getVersion());
    }

    /** Package-visible so the contract generator can use the production Flyway configuration. */
    static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription("Existing Arcturus/Polaris installation")
                .validateOnMigrate(true)
                .outOfOrder(false)
                // Reference data contains literal ${...} client template strings.
                .placeholderReplacement(false)
                .load();
    }

    private static HikariDataSource rawMigrationDataSource(HikariDataSource runtime) {
        HikariConfig migrateConfig = new HikariConfig();
        migrateConfig.setJdbcUrl(runtime.getJdbcUrl());
        migrateConfig.setUsername(runtime.getUsername());
        migrateConfig.setPassword(runtime.getPassword());
        Properties migrateProperties = new Properties();
        migrateProperties.putAll(runtime.getDataSourceProperties());
        // The runtime pool tunes for short gameplay queries (socketTimeout=30s), but
        // adoption DDL such as rebuilding a hotel's chat-log tables can legitimately
        // run for minutes. Migrations get no per-statement timeout.
        migrateProperties.setProperty("socketTimeout", "0");
        migrateConfig.setDataSourceProperties(migrateProperties);
        migrateConfig.setMaximumPoolSize(2);
        migrateConfig.setMinimumIdle(0);
        migrateConfig.setPoolName("polaris-migrate");
        LOGGER.debug("[migrate] Opened an unwrapped migration datasource using the configured database account.");
        return new HikariDataSource(migrateConfig);
    }
}
