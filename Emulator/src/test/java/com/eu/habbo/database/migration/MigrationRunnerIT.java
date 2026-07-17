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

/** Verifies the packaged migration chain against a real MariaDB. */
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

            // Base table and representative Polaris-only tables both exist.
            assertTrue(tableExists(ds, "users"), "base table users must exist");
            assertTrue(tableExists(ds, "permission_ranks"), "Polaris table permission_ranks must exist");
            assertTrue(tableExists(ds, "wired_emulator_settings"), "Polaris table wired_emulator_settings must exist");
            assertTrue(tableExists(ds, "habbo_mentions"), "current mentions schema must exist");
            assertTrue(tableExists(ds, "wheel_prizes"), "current wheel schema must exist");
            assertTrue(tableExists(ds, "users_earnings_claims"), "current earnings schema must exist");
            assertTrue(tableExists(ds, "furnidata_edit_log"), "current furnidata audit schema must exist");
            assertTrue(tableExists(ds, "messenger_messages"), "dev messenger history schema must exist");
            assertTrue(tableExists(ds, "logs_economy"), "dev economy audit schema must exist");

            // Polaris-added column on a shared table.
            assertTrue(columnExists(ds, "users", "auth_ticket_expires_at"),
                    "Polaris column users.auth_ticket_expires_at must exist");
            assertTrue(columnExists(ds, "users", "background_border_id"),
                    "current profile background schema must exist");
            assertTrue(columnExists(ds, "users", "access_token_version"),
                    "credential revocation state must exist");

            // The engine conversion took effect.
            assertEquals("InnoDB", tableEngine(ds, "marketplace_items"),
                    "marketplace_items must be InnoDB after migration");

            // The dynamic Arc permissions conversion must produce usable Polaris
            // rows, not merely empty marker tables.
            assertEquals(7, intValue(ds, "SELECT COUNT(*) FROM permission_ranks"));
            assertTrue(intValue(ds, "SELECT COUNT(*) FROM permission_definitions") >= 200);
            assertEquals(1, intValue(ds,
                    "SELECT rank_7 FROM permission_definitions WHERE permission_key = 'acc_supporttool'"));
            assertEquals(5, intValue(ds, "SELECT COUNT(*) FROM pet_breeding"));
            assertEquals(100, intValue(ds, """
                    SELECT COUNT(*) FROM pet_breeding_races
                    WHERE pet_type IN (24, 25, 28, 29, 30)
                    """));
            assertEquals("breeding_nest", stringValue(ds, """
                    SELECT interaction_type FROM items_base
                    WHERE item_name = 'pet_breeding_bear'
                    """));

            // Now MANAGED, and a second migrate is a no-op (Flyway history).
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            assertTrue(MigrationRunner.status(ds).contains("Pending migrations: 0"));
            MigrationRunner.migrate(ds);
        }
    }

    @Test
    void realArcturusFixtureMigratesAndPreservesHotelData() throws Exception {
        requireDocker();

        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_arc_355")) {
            installArcturusFixture(ds);

            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            String status = MigrationRunner.status(ds);
            assertTrue(status.contains(
                    "Adoption: record baseline V" + MigrationRunner.BASELINE_VERSION));
            // Adoption applies every packaged migration except the skipped baseline.
            assertTrue(status.contains("Pending migrations: " + (packagedMigrationCount() - 1)));
            assertTrue(!tableExists(ds, "flyway_schema_history"),
                    "read-only status must not adopt or mutate the hotel");
            MigrationRunner.migrate(ds);

            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            assertTrue(tableExists(ds, "flyway_schema_history"), "adoption must create the Flyway history");
            assertTrue(tableExists(ds, "old_guilds_forums"), "unused Arc tables must be tolerated and preserved");
            assertTrue(tableExists(ds, "old_guilds_forums_comments"), "unused Arc tables must be tolerated and preserved");

            // Conversion guarantees the schema Polaris requires without rewriting
            // unrelated legacy defaults, collations or storage engines.
            assertTrue(tableExists(ds, "permission_ranks"));
            assertTrue(tableExists(ds, "wired_emulator_settings"));
            assertTrue(tableExists(ds, "habbo_mentions"));
            assertTrue(tableExists(ds, "messenger_messages"));
            assertTrue(tableExists(ds, "logs_economy"));
            assertTrue(columnExists(ds, "users", "auth_ticket_expires_at"));
            assertTrue(columnExists(ds, "users", "background_border_id"));
            assertTrue(columnExists(ds, "users", "access_token_version"));
            assertEquals("InnoDB", tableEngine(ds, "marketplace_items"));
            assertEquals(7, intValue(ds, "SELECT COUNT(*) FROM permission_ranks"));
            assertTrue(intValue(ds, "SELECT COUNT(*) FROM permission_definitions") >= 200);

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
    void customDevMigrationHistoryIsAdoptedWithoutOverwritingCanonicalPermissions() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_existing_permissions")) {
            Flyway.configure()
                    .dataSource(ds)
                    .locations(MigrationRunner.MIGRATION_LOCATION)
                    .target("20260528020000")
                    .placeholderReplacement(false)
                    .load()
                    .migrate();

            int existingFrankResponses;
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("UPDATE permission_ranks SET rank_name = 'Custom Member' WHERE id = 1");
                s.execute("""
                        INSERT INTO permission_definitions
                            (permission_key, max_value, comment, rank_1)
                        VALUES ('custom_operator_permission', 1, 'keep this canonical definition', 1)
                        """);
                s.execute("""
                        UPDATE wired_emulator_settings
                        SET value = '321'
                        WHERE `key` = 'wired.engine.maxStepsPerStack'
                        """);
                s.execute("""
                        INSERT INTO emulator_settings (`key`, `value`)
                        VALUES ('websockets.whitelist', 'legacy-host.example')
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                        """);
                s.execute("""
                        INSERT INTO emulator_settings (`key`, `value`)
                        VALUES ('ws.whitelist', 'operator-host.example')
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                        """);
                s.execute("""
                        INSERT INTO pet_actions (pet_type, pet_name, offspring_type)
                        VALUES (99, 'Plugin Pet', 123)
                        """);
                s.execute("""
                        INSERT INTO pet_breeding_races (pet_type, rarity_level, breed)
                        VALUES (99, 9, 999)
                        """);
                existingFrankResponses = intValue(ds,
                        "SELECT COUNT(*) FROM bot_chat_responses WHERE bot_type = 'frank'");
                s.execute("DROP TABLE flyway_schema_history");
                s.execute("""
                        CREATE TABLE schema_migrations (
                            version INT NOT NULL PRIMARY KEY,
                            description VARCHAR(255) NOT NULL,
                            applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                s.execute("""
                        INSERT INTO schema_migrations (version, description)
                        VALUES (27, 'custom dev runner baseline')
                        """);
            }

            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);

            assertEquals("Custom Member",
                    stringValue(ds, "SELECT rank_name FROM permission_ranks WHERE id = 1"));
            assertEquals("keep this canonical definition",
                    stringValue(ds, """
                            SELECT comment FROM permission_definitions
                            WHERE permission_key = 'custom_operator_permission'
                            """));
            assertEquals(1, intValue(ds, """
                    SELECT rank_1 FROM permission_definitions
                    WHERE permission_key = 'custom_operator_permission'
                    """));
            assertEquals("321", stringValue(ds, """
                    SELECT value FROM wired_emulator_settings
                    WHERE `key` = 'wired.engine.maxStepsPerStack'
                    """));
            assertEquals("operator-host.example", stringValue(ds, """
                    SELECT value FROM emulator_settings
                    WHERE `key` = 'ws.whitelist'
                    """));
            assertEquals(existingFrankResponses, intValue(ds,
                    "SELECT COUNT(*) FROM bot_chat_responses WHERE bot_type = 'frank'"));
            assertEquals(123, intValue(ds,
                    "SELECT offspring_type FROM pet_actions WHERE pet_type = 99"));
            assertEquals(1, intValue(ds, """
                    SELECT COUNT(*) FROM pet_breeding_races
                    WHERE pet_type = 99 AND rarity_level = 9 AND breed = 999
                    """));
            assertTrue(tableExists(ds, "schema_migrations"),
                    "the prior custom runner history must remain harmless and untouched");
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

    /** Counts the packaged SQL and Java migrations from the source tree. */
    private static long packagedMigrationCount() throws Exception {
        long count = 0;
        for (Path directory : List.of(
                Path.of("src/main/resources/db/migration"),
                Path.of("src/main/java/db/migration"))) {
            try (var files = Files.list(directory)) {
                count += files
                        .filter(p -> p.getFileName().toString().matches("V\\d{14}__\\w+\\.(sql|java)"))
                        .count();
            }
        }
        return count;
    }

    private static void installArcturusFixture(HikariDataSource ds) throws Exception {
        executeSqlResource(ds, "db/fixture/arcturus_ms_3_5_5_schema.sql");
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
            for (String statement : splitSqlStatements(sql.toString())) {
                if (!statement.isBlank()) {
                    s.execute(statement);
                }
            }
        }
    }

    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            statement.append(current);

            if (quote != 0) {
                if (current == '\\' && i + 1 < sql.length()) {
                    statement.append(sql.charAt(++i));
                } else if (current == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        statement.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == ';') {
                statements.add(statement.substring(0, statement.length() - 1));
                statement.setLength(0);
            }
        }

        if (!statement.isEmpty()) {
            statements.add(statement.toString());
        }
        return statements;
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
