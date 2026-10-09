package com.eu.habbo.database.migration;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.backup.MariaDbMigrationBackup;
import com.eu.habbo.database.backup.MigrationBackup;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>A recognised database without history is baselined at the last migration
 *       whose tables and columns it already carries (see {@link SchemaEvidence}), so a
 *       hotel whose schema is ahead of its history is not asked to re-create objects
 *       that exist.</li>
 * </ul>
 */
public final class MigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

    public static final String MIGRATION_LOCATION = "classpath:db/migration";
    /** Existing installs record this version so the clean-install database is never imported over hotel data. */
    public static final String BASELINE_VERSION = "20260518000000";

    private static final String KEY_ON_STARTUP = "db.migrate.on_startup";

    private MigrationRunner() {}

    /** Startup entry point, called before database-backed configuration is loaded. */
    public static void runAtStartup(HikariDataSource runtimeDataSource, ConfigurationManager config) {
        if (!config.getBoolean(KEY_ON_STARTUP, true)) {
            LOGGER.warn(
                    "[migrate] {}=false — skipping automatic schema migration. The operator is responsible for schema state.",
                    KEY_ON_STARTUP);
            return;
        }

        migrateAtStartup(runtimeDataSource, config);
    }

    /** Applies migrations immediately, regardless of the startup config switch. */
    public static MigrateResult migrateAtStartup(HikariDataSource runtimeDataSource, ConfigurationManager config) {
        // The runtime datasource rewrites legacy plugin SQL. Migrations require an
        // unwrapped pool so their DDL cannot be silently translated.
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            return migrate(rawMigrationDataSource, MariaDbMigrationBackup.resolve(config, rawMigrationDataSource));
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

    /**
     * Records pending migrations whose objects the schema already carries as applied,
     * without running them, using the same raw connection invariant.
     */
    public static String reconcileAtStartup(HikariDataSource runtimeDataSource) {
        try (HikariDataSource rawMigrationDataSource = rawMigrationDataSource(runtimeDataSource)) {
            return reconcile(rawMigrationDataSource);
        }
    }

    /** Runs the action permitted for the detected schema state. */
    public static MigrateResult migrate(DataSource dataSource) {
        return migrate(dataSource, MigrationBackup.disabled());
    }

    static MigrateResult migrate(DataSource dataSource, MigrationBackup migrationBackup) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        Adoption adoption =
                state == SchemaPreflight.State.RECOGNISED_EXISTING ? Adoption.detect(dataSource) : Adoption.none();
        Flyway flyway = flyway(dataSource, adoption.baselineVersion());

        LOGGER.info("[migrate] Detected schema state: {}", state);
        try {
            if (state != SchemaPreflight.State.EMPTY && state != SchemaPreflight.State.UNKNOWN) {
                java.util.List<String> pending = java.util.Arrays.stream(
                                flyway.info().pending())
                        .filter(migration -> !isBaselineSkippedDuringAdoption(state, migration, adoption))
                        .map(migration -> migration.getVersion() == null
                                ? migration.getDescription()
                                : migration.getVersion().getVersion())
                        .toList();
                if (!pending.isEmpty()) migrationBackup.beforeMigrations(pending);
            }
            MigrateResult result =
                    switch (state) {
                        case UNKNOWN ->
                            throw new MigrationException(
                                    "Refusing to migrate: the database is non-empty but is not a recognised Arc/Polaris schema. "
                                            + "No changes were made. Check that db.database points at the correct database, or see the "
                                            + "conversion/recovery instructions before proceeding.");
                        case RECOGNISED_EXISTING -> {
                            LOGGER.warn(
                                    "[migrate] Existing Arcturus/Polaris hotel detected with no migration history. "
                                            + "Polaris will preserve the hotel data, record adoption baseline V{}, and apply the required upgrades now. "
                                            + "A full database backup before first startup is strongly recommended.",
                                    adoption.baselineVersion());
                            adoption.log();
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
            throw new MigrationException(
                    "Migration failed; the emulator will not start against a half-upgraded schema. "
                            + "Inspect the error, restore from backup or forward-fix, then retry."
                            + schemaAheadHint(e),
                    e);
        }
    }

    /**
     * A migration that fails because the object it creates already exists means the schema
     * is ahead of the recorded history; point the operator at the tool for that instead of
     * leaving them to edit migrations or drop the history by hand.
     */
    static String schemaAheadHint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && isAlreadyExists(sql)) {
                return " The failing statement creates something the schema already has, so the schema is ahead of "
                        + "flyway_schema_history. Run --migrations=validate to see which migrations the schema already "
                        + "carries, then --migrations=reconcile to record them as applied without running them.";
            }
        }
        return "";
    }

    private static boolean isAlreadyExists(SQLException e) {
        // MySQL/MariaDB: 1050 table exists (42S01), 1060 duplicate column (42S21), 1061 duplicate key name (42000).
        int code = e.getErrorCode();
        String state = e.getSQLState();
        return code == 1050 || code == 1060 || code == 1061 || "42S01".equals(state) || "42S21".equals(state);
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
            Adoption adoption =
                    state == SchemaPreflight.State.RECOGNISED_EXISTING ? Adoption.detect(dataSource) : Adoption.none();
            Flyway flyway = flyway(dataSource, adoption.baselineVersion());
            if (state == SchemaPreflight.State.MANAGED) {
                flyway.validate();
            }

            MigrationInfoService info = flyway.info();
            MigrationInfo current = info.current();
            out.append("Current version: ")
                    .append(current == null ? "(none)" : current.getVersion())
                    .append('\n');
            if (state == SchemaPreflight.State.RECOGNISED_EXISTING) {
                out.append("Adoption: record baseline V")
                        .append(adoption.baselineVersion())
                        .append('\n');
                adoption.describe(out);
            }

            MigrationInfo[] pending = info.pending();
            int pendingCount = 0;
            for (MigrationInfo migration : pending) {
                if (!isBaselineSkippedDuringAdoption(state, migration, adoption)) {
                    pendingCount++;
                }
            }
            out.append("Pending migrations: ").append(pendingCount).append('\n');
            for (MigrationInfo migration : pending) {
                if (!isBaselineSkippedDuringAdoption(state, migration, adoption)) {
                    out.append("  - V")
                            .append(migration.getVersion())
                            .append(' ')
                            .append(migration.getDescription())
                            .append('\n');
                }
            }
            if (state == SchemaPreflight.State.MANAGED && pendingCount > 0) {
                describeReconcilable(dataSource, flyway, out);
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
            throw new MigrationException(
                    "Migration status failed; the schema history could not be "
                            + "validated against the packaged migrations. No changes were made.",
                    e);
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
            throw new MigrationException(
                    "Migration repair failed; the schema history could not be realigned "
                            + "with the packaged migrations. No hotel data was changed.",
                    e);
        }
    }

    /**
     * Before adoption records its baseline, Flyway reports every packaged migration as
     * pending, including the ones the baseline will cover. Those are not going to run.
     */
    private static boolean isBaselineSkippedDuringAdoption(
            SchemaPreflight.State state, MigrationInfo migration, Adoption adoption) {
        return state == SchemaPreflight.State.RECOGNISED_EXISTING
                && migration.getVersion() != null
                && migration.getVersion().compareTo(MigrationVersion.fromVersion(adoption.baselineVersion())) <= 0;
    }

    /**
     * Where adoption records its baseline: the clean-install version, moved forward to the
     * last packaged migration whose objects the schema already carries.
     */
    record Adoption(String baselineVersion, SchemaEvidence.Assessment assessment) {

        static Adoption none() {
            return new Adoption(BASELINE_VERSION, null);
        }

        static Adoption detect(DataSource dataSource) {
            List<SchemaEvidence.Migration> candidates = new ArrayList<>();
            MigrationVersion baseline = MigrationVersion.fromVersion(BASELINE_VERSION);
            for (SchemaEvidence.Migration migration : SchemaEvidence.packaged(flyway(dataSource))) {
                if (MigrationVersion.fromVersion(migration.version()).compareTo(baseline) > 0)
                    candidates.add(migration);
            }
            try (Connection connection = dataSource.getConnection()) {
                SchemaEvidence.Assessment assessment = SchemaEvidence.assess(connection, candidates);
                String version = assessment.appliedThrough() == null ? BASELINE_VERSION : assessment.appliedThrough();
                return new Adoption(version, assessment);
            } catch (SQLException e) {
                throw new MigrationException("Could not inspect the schema to choose the adoption baseline", e);
            }
        }

        boolean movedForward() {
            return !BASELINE_VERSION.equals(baselineVersion);
        }

        void log() {
            if (assessment == null) return;
            if (movedForward()) {
                LOGGER.warn(
                        "[migrate] The schema already carries the tables and columns of migrations V{} through V{}; "
                                + "they are recorded as applied and will not run again: {}",
                        assessment.evidentlyApplied().get(0),
                        baselineVersion,
                        assessment.evidentlyApplied());
            }
            if (!assessment.presentBeyond().isEmpty()) {
                LOGGER.warn(
                        "[migrate] Later migrations whose objects also exist will still run and rely on their own guards: {}",
                        assessment.presentBeyond());
            }
        }

        void describe(StringBuilder out) {
            if (assessment == null || !movedForward()) return;
            out.append("Already present in the schema, recorded as applied without running: ")
                    .append(assessment.evidentlyApplied().size())
                    .append('\n');
            for (String version : assessment.evidentlyApplied()) {
                out.append("  = V").append(version).append('\n');
            }
        }
    }

    /**
     * Marks pending migrations whose objects the schema already carries as applied, without
     * running them. For a managed database whose history fell behind its schema: restored
     * from a dump taken without {@code flyway_schema_history}, or upgraded by hand.
     *
     * <p>Only the unbroken run of pending migrations from the first pending one is
     * recorded, and only when every table and column each of them creates exists. The
     * rows are written the way Flyway writes them, with the packaged checksum, so a
     * normal startup validates and applies the rest. Nothing else is changed.
     */
    public static String reconcile(DataSource dataSource) {
        SchemaPreflight.State state = SchemaPreflight.detect(dataSource);
        LOGGER.info("[migrate] Detected schema state: {}", state);

        if (state != SchemaPreflight.State.MANAGED) {
            throw new MigrationException(
                    "Refusing to reconcile: only a database with a Flyway schema history can be reconciled (state "
                            + state + "). A recognised hotel without history is adopted at the right point by a normal "
                            + "startup; nothing was changed.");
        }

        try {
            Flyway flyway = flyway(dataSource);
            List<MigrationInfo> reconcilable = reconcilable(dataSource, flyway);
            StringBuilder out = new StringBuilder();
            if (reconcilable.isEmpty()) {
                out.append("Nothing to reconcile: no pending migration's objects are already present.\n");
                return out.toString();
            }
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    int rank = nextInstalledRank(connection);
                    String installedBy = installedBy(connection);
                    for (MigrationInfo migration : reconcilable) {
                        recordAsApplied(connection, rank++, migration, installedBy);
                        out.append("Recorded as applied without running: V")
                                .append(migration.getVersion())
                                .append(' ')
                                .append(migration.getDescription())
                                .append('\n');
                    }
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }
            out.append("Reconciled ")
                    .append(reconcilable.size())
                    .append(" migration(s). Run a normal startup to apply the rest.\n");
            LOGGER.info("[migrate] {}", out.toString().trim());
            return out.toString();
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration reconcile failed; the schema history was not changed.", e);
        }
    }

    /** Pending migrations, from the first pending one onward, whose objects all exist. */
    private static List<MigrationInfo> reconcilable(DataSource dataSource, Flyway flyway) throws SQLException {
        MigrationInfo[] pending = flyway.info().pending();
        List<SchemaEvidence.Migration> packaged = SchemaEvidence.packaged(flyway);
        List<SchemaEvidence.Migration> candidates = new ArrayList<>();
        for (MigrationInfo migration : pending) {
            if (migration.getVersion() == null) continue;
            for (SchemaEvidence.Migration evidence : packaged) {
                if (evidence.version().equals(migration.getVersion().getVersion())) candidates.add(evidence);
            }
        }
        SchemaEvidence.Assessment assessment;
        try (Connection connection = dataSource.getConnection()) {
            assessment = SchemaEvidence.assess(connection, candidates);
        }
        List<String> applied = assessment.evidentlyApplied();
        List<MigrationInfo> reconcilable = new ArrayList<>();
        for (MigrationInfo migration : pending) {
            if (migration.getVersion() != null
                    && applied.contains(migration.getVersion().getVersion())) {
                reconcilable.add(migration);
            }
        }
        return reconcilable;
    }

    private static void describeReconcilable(DataSource dataSource, Flyway flyway, StringBuilder out)
            throws SQLException {
        List<MigrationInfo> reconcilable = reconcilable(dataSource, flyway);
        if (reconcilable.isEmpty()) return;
        out.append("Pending but already present in the schema (--migrations=reconcile records them as applied): ")
                .append(reconcilable.size())
                .append('\n');
        for (MigrationInfo migration : reconcilable) {
            out.append("  = V")
                    .append(migration.getVersion())
                    .append(' ')
                    .append(migration.getDescription())
                    .append('\n');
        }
    }

    private static int nextInstalledRank(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history")) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    private static String installedBy(Connection connection) throws SQLException {
        String user = connection.getMetaData().getUserName();
        if (user == null || user.isBlank()) return "polaris";
        int at = user.indexOf('@');
        return at > 0 ? user.substring(0, at) : user;
    }

    private static void recordAsApplied(Connection connection, int rank, MigrationInfo migration, String installedBy)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO flyway_schema_history "
                + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, "
                + "execution_time, success) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 0, 1)")) {
            statement.setInt(1, rank);
            statement.setString(2, migration.getVersion().getVersion());
            statement.setString(3, migration.getDescription());
            statement.setString(4, migration.getType().name());
            statement.setString(5, migration.getScript());
            if (migration.getChecksum() == null) {
                statement.setNull(6, java.sql.Types.INTEGER);
            } else {
                statement.setInt(6, migration.getChecksum());
            }
            statement.setString(7, installedBy);
            statement.executeUpdate();
        }
    }

    /** Package-visible so the contract generator can use the production Flyway configuration. */
    static Flyway flyway(DataSource dataSource) {
        return flyway(dataSource, BASELINE_VERSION);
    }

    /** The production configuration with the version adoption records as its baseline. */
    static Flyway flyway(DataSource dataSource, String baselineVersion) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                .baselineVersion(baselineVersion)
                .baselineDescription("Existing Arcturus/Polaris installation")
                .validateOnMigrate(true)
                // A pending migration is what --migrations=validate reports, not a validation
                // failure; migrate() already ignores pending ones in its own validation.
                .ignoreMigrationPatterns("*:pending")
                .outOfOrder(true)
                .callbacks(new SuppressNotesCallback())
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
