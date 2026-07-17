package com.eu.habbo.database.migration;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.compat.LegacyBridgeDataSource;
import com.eu.habbo.database.compat.LegacySqlBridge;
import com.eu.habbo.database.compat.LegacySqlTranslator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@link MigrationRunner} (Flyway + the packaged V1..Vn migrations)
 * against a throwaway MariaDB. It verifies the currently packaged migration
 * chain and proves that fresh and Arcturus-converter paths converge through V4.
 * If a migration is broken (as the V4 ROW_FORMAT bug was), this fails instead
 * of shipping.
 */
class MigrationRunnerIT {

    /** Local builds may skip without Docker; CI must fail if its DB tests cannot run. */
    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable t) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI", t);
            }
            assumeTrue(false, "Docker/Testcontainers not available — skipping DB integration test: " + t.getMessage());
        }
    }

    @Test
    void freshDatabaseMigratesThroughTheCurrentPackagedVersion() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_fresh")) {
            // Empty DB -> EMPTY state.
            assertEquals(SchemaPreflight.State.EMPTY, SchemaPreflight.detect(ds));

            MigrationRunner.migrate(ds);

            // Baseline table and a representative Polaris-only table both exist.
            assertTrue(tableExists(ds, "users"), "Arc baseline table users must exist");
            assertTrue(tableExists(ds, "permission_ranks"), "Polaris table permission_ranks must exist");
            assertTrue(tableExists(ds, "wired_emulator_settings"), "Polaris table wired_emulator_settings must exist");

            // Polaris-added column on a shared table.
            assertTrue(columnExists(ds, "users", "auth_ticket_expires_at"),
                    "Polaris column users.auth_ticket_expires_at must exist");

            // V4 engine conversion took effect.
            assertEquals("InnoDB", tableEngine(ds, "marketplace_items"),
                    "marketplace_items must be InnoDB after V4");

            // Now MANAGED, and a second migrate is a no-op (Flyway history).
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);
        }
    }

    @Test
    void realArcturusFixtureConvergesWithFreshInstallAndPreservesHotelData() throws Exception {
        requireDocker();

        List<String> freshManifest;
        try (HikariDataSource fresh = TestDatabase.freshDatabase("mig_manifest_fresh")) {
            MigrationRunner.migrate(fresh);
            freshManifest = schemaManifest(fresh);
        }

        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_arc_355")) {
            installArcturusFixture(ds);

            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);

            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            assertTrue(tableExists(ds, "flyway_schema_history"), "adoption must create the Flyway history");
            assertTrue(tableExists(ds, "old_guilds_forums"), "unused Arc tables must be tolerated and preserved");
            assertTrue(tableExists(ds, "old_guilds_forums_comments"), "unused Arc tables must be tolerated and preserved");

            // Ignoring the two obsolete Arc-only tables, both supported install paths
            // must produce the same tables, columns, indexes, constraints, engines,
            // row formats and collations.
            assertEquals(freshManifest, schemaManifest(ds));

            // Representative operator-owned hotel data survives the conversion.
            assertEquals("Keep this hotel data",
                    stringValue(ds, "SELECT motto FROM users WHERE id = 4242"));
            assertEquals(98765,
                    intValue(ds, "SELECT credits FROM users WHERE id = 4242"));
            assertEquals("Existing Arcturus Room",
                    stringValue(ds, "SELECT name FROM rooms WHERE id = 4242"));
            assertEquals("existing-item-data",
                    stringValue(ds, "SELECT extra_data FROM items WHERE id = 4242"));
            assertEquals(777,
                    intValue(ds, "SELECT amount FROM users_currency WHERE user_id = 4242 AND type = 5"));
            assertEquals("keep-me",
                    stringValue(ds, "SELECT value FROM emulator_settings WHERE `key` = 'fixture.operator.setting'"));
        }
    }

    @Test
    void unknownNonEmptyDatabaseIsRefusedWithoutMutation() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_unknown")) {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE `something_unrelated` (`id` INT PRIMARY KEY)");
            }
            assertEquals(SchemaPreflight.State.UNKNOWN, SchemaPreflight.detect(ds));

            try {
                MigrationRunner.migrate(ds);
                org.junit.jupiter.api.Assertions.fail("expected refusal");
            } catch (MigrationException expected) {
                // no flyway history was created
                assertTrue(!tableExists(ds, "flyway_schema_history"), "unknown DB must not be touched");
            }
        }
    }

    @Test
    void startupMigrationsBypassTheLegacySqlBridge() throws Exception {
        requireDocker();
        try (HikariDataSource database = TestDatabase.freshDatabase("mig_raw_datasource")) {
            AtomicInteger translatedStatements = new AtomicInteger();
            LegacySqlBridge bridge = new LegacySqlBridge();
            bridge.registerTranslator(new LegacySqlTranslator() {
                @Override
                public boolean appliesTo(String lowerCaseSql) {
                    return true;
                }

                @Override
                public String translate(String sql, com.eu.habbo.database.compat.TranslationContext context) {
                    translatedStatements.incrementAndGet();
                    return sql;
                }
            });

            HikariConfig runtimeConfig = new HikariConfig();
            runtimeConfig.setJdbcUrl(database.getJdbcUrl());
            runtimeConfig.setUsername(database.getUsername());
            runtimeConfig.setPassword(database.getPassword());
            runtimeConfig.setMaximumPoolSize(2);
            runtimeConfig.setPoolName("polaris-it-legacy-wrapper");

            Path configFile = Files.createTempFile("polaris-migration", ".ini");
            Files.writeString(configFile, "db.migrate.on_startup=true\n", StandardCharsets.UTF_8);
            try (LegacyBridgeDataSource runtime = new LegacyBridgeDataSource(runtimeConfig, bridge)) {
                MigrationRunner.runAtStartup(runtime, new ConfigurationManager(configFile.toString()));
            } finally {
                Files.deleteIfExists(configFile);
            }

            assertEquals(0, translatedStatements.get(),
                    "Flyway SQL must never be offered to the legacy plugin bridge");
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(database));
        }
    }

    private static boolean tableExists(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(HikariDataSource ds, String table, String column) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            st.setString(1, table);
            st.setString(2, column);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String tableEngine(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void installArcturusFixture(HikariDataSource ds) throws Exception {
        // V1 is generated from the supplied BaseDB MS 3.5.5 dump. Apply only that
        // baseline, remove Flyway's bookkeeping to model an unmanaged live hotel,
        // then layer on committed operator data and the two obsolete source tables.
        Flyway.configure()
                .dataSource(ds)
                .locations(MigrationRunner.MIGRATION_LOCATION)
                .target(MigrationRunner.BASELINE_VERSION)
                .placeholderReplacement(false)
                .load()
                .migrate();

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE `flyway_schema_history`");
        }
        executeSqlResource(ds, "db/fixture/arcturus_ms_3_5_5_running_hotel.sql");
    }

    private static void executeSqlResource(HikariDataSource ds, String resource) throws Exception {
        InputStream input = MigrationRunnerIT.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("Missing SQL fixture resource: " + resource);
        }

        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.stripLeading().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
        }

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String statement : sql.toString().split(";")) {
                if (!statement.isBlank()) {
                    s.execute(statement);
                }
            }
        }
    }

    private static List<String> schemaManifest(HikariDataSource ds) throws Exception {
        List<String> manifest = new ArrayList<>();
        String retainedTables = "TABLE_NAME NOT IN ('old_guilds_forums','old_guilds_forums_comments')";

        appendRows(ds, manifest,
                "SELECT 'T', TABLE_NAME, ENGINE, ROW_FORMAT, TABLE_COLLATION "
                        + "FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND TABLE_TYPE='BASE TABLE' AND " + retainedTables + " ORDER BY TABLE_NAME",
                5);
        appendRows(ds, manifest,
                "SELECT 'C', TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_DEFAULT, IS_NULLABLE, "
                        + "COLUMN_TYPE, CHARACTER_SET_NAME, COLLATION_NAME, EXTRA, COLUMN_COMMENT "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND " + retainedTables + " ORDER BY TABLE_NAME, ORDINAL_POSITION",
                11);
        appendRows(ds, manifest,
                "SELECT 'I', TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, NON_UNIQUE, COLUMN_NAME, "
                        + "COLLATION, SUB_PART, INDEX_TYPE "
                        + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND " + retainedTables + " ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX",
                9);
        appendRows(ds, manifest,
                "SELECT 'K', TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE "
                        + "FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND " + retainedTables + " ORDER BY TABLE_NAME, CONSTRAINT_NAME",
                4);
        return manifest;
    }

    private static void appendRows(HikariDataSource ds, List<String> target, String query, int columns) throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(query)) {
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        row.append('|');
                    }
                    String value = rs.getString(i);
                    row.append(value == null ? "<NULL>" : value);
                }
                target.add(row.toString());
            }
        }
    }

    private static String stringValue(HikariDataSource ds, String query) throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int intValue(HikariDataSource ds, String query) throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : Integer.MIN_VALUE;
        }
    }
}
