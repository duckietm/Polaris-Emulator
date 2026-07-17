package com.eu.habbo.database.migration;

import com.eu.habbo.core.ConfigurationManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Runs Flyway migrations. Uses only free Flyway features (versioned migrations,
 * the {@code baseline} command, validation, repair). The Arc MS 3.5.5 schema is
 * {@code V1}; every Polaris change is {@code V2..Vn}.
 *
 * <p>Design invariants:
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
    /** Existing installs are baselined at the Arc baseline version so V1 is skipped. */
    public static final String BASELINE_VERSION = "1";

    private static final String KEY_ON_STARTUP = "db.migrate.on_startup";

    private MigrationRunner() {
    }

    /**
     * Startup entry point. Called after the database connection is established and
     * <b>before</b> configuration is loaded from the database, so schema/setting
     * migrations are applied before anything reads them.
     */
    public static void runAtStartup(HikariDataSource runtimeDataSource, ConfigurationManager config) {
        if (!config.getBoolean(KEY_ON_STARTUP, true)) {
            LOGGER.warn("[migrate] {}=false — skipping automatic schema migration. The operator is responsible for schema state.", KEY_ON_STARTUP);
            return;
        }

        // Production's runtime datasource is a LegacyBridgeDataSource. Its
        // getConnection() method wraps every JDBC statement so old plugin SQL can
        // be translated. Flyway must not pass through that compatibility layer:
        // a future migration mentioning a legacy table name could otherwise be
        // silently rewritten. Reuse the normal DB credentials in a tiny,
        // short-lived raw pool instead.
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            migrate(rawMigrationDataSource);
        }
    }

    /**
     * Runs the appropriate action for the detected schema state. Package-visible
     * for integration tests, which call it directly against a Testcontainers DB.
     */
    public static MigrateResult migrate(DataSource dataSource) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        Flyway flyway = flyway(dataSource);

        LOGGER.info("[migrate] Detected schema state: {}", state);
        try {
            switch (state) {
                case UNKNOWN -> throw new MigrationException(
                        "Refusing to migrate: the database is non-empty but is not a recognised Arc/Polaris schema. "
                                + "No changes were made. Check that db.database points at the correct database, or see the "
                                + "conversion/recovery instructions before proceeding.");
                case RECOGNISED_EXISTING -> {
                    LOGGER.warn("[migrate] Existing Arcturus/Polaris hotel detected with no migration history. "
                            + "Polaris will preserve the hotel data, record the Arcturus V{} baseline, and apply the required upgrades now. "
                            + "A full database backup before first startup is strongly recommended.", BASELINE_VERSION);
                    flyway.baseline();
                    return flyway.migrate();
                }
                case EMPTY, MANAGED -> {
                    return flyway.migrate();
                }
                default -> throw new MigrationException("Unhandled schema state: " + state);
            }
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration failed; the emulator will not start against a half-upgraded schema. "
                    + "Inspect the error, restore from backup or forward-fix, then retry.", e);
        }
    }

    /** Read-only summary for a {@code db status} / {@code db check} operator command. */
    public static String status(DataSource dataSource) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        StringBuilder out = new StringBuilder();
        out.append("Schema state: ").append(state).append('\n');

        if (state == SchemaPreflight.State.MANAGED) {
            MigrationInfoService info = flyway(dataSource).info();
            MigrationInfo current = info.current();
            out.append("Current version: ").append(current == null ? "(none)" : current.getVersion()).append('\n');
            MigrationInfo[] pending = info.pending();
            out.append("Pending migrations: ").append(pending.length).append('\n');
            for (MigrationInfo p : pending) {
                out.append("  - V").append(p.getVersion()).append(" ").append(p.getDescription()).append('\n');
            }
        }
        return out.toString();
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription("Arcturus MS 3.5.5 baseline")
                .validateOnMigrate(true)
                .outOfOrder(false)
                // Emulator reference data legitimately contains ${...} template
                // strings (e.g. ${image.library.url}); do not treat them as Flyway
                // placeholders.
                .placeholderReplacement(false)
                .load();
    }

    private static HikariDataSource rawMigrationDataSource(HikariDataSource runtime) {
        HikariConfig migrateConfig = new HikariConfig();
        migrateConfig.setJdbcUrl(runtime.getJdbcUrl());
        migrateConfig.setUsername(runtime.getUsername());
        migrateConfig.setPassword(runtime.getPassword());
        migrateConfig.setDataSourceProperties(runtime.getDataSourceProperties());
        migrateConfig.setMaximumPoolSize(2);
        migrateConfig.setMinimumIdle(0);
        migrateConfig.setPoolName("polaris-migrate");
        LOGGER.debug("[migrate] Opened an unwrapped migration datasource using the configured database account.");
        return new HikariDataSource(migrateConfig);
    }
}
