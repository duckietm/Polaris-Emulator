package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbMigrationIntegrationTest {
    @Test
    void immutableEconomyLedgerMigrationRunsOnMariaDbAndRejectsChanges() throws Exception {
        String host = System.getenv("MIGRATION_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "Set MIGRATION_TEST_DB_HOST to opt into the MariaDB integration test");
        assertTrue(isLoopback(host), "Migration integration tests require a loopback MariaDB host");

        String port = environment("MIGRATION_TEST_DB_PORT", "3306");
        String user = environment("MIGRATION_TEST_DB_USER", "root");
        String password = environment("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "polaris_economy_test_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mariadb://" + host + ":" + port + "/";

        try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
            execute(admin, "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try (Connection connection = DriverManager.getConnection(serverUrl + schema, user, password)) {
            execute(connection, """
                    CREATE TABLE logs_economy (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      operation_id VARCHAR(96) NOT NULL UNIQUE,
                      user_id INT UNSIGNED NOT NULL,
                      operation VARCHAR(64) NOT NULL,
                      currency_type INT NOT NULL,
                      amount INT NOT NULL,
                      balance_before INT NOT NULL,
                      balance_after INT NOT NULL,
                      item_id INT UNSIGNED NULL,
                      context VARCHAR(255) NOT NULL DEFAULT '',
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);
            String migration = Files.readString(Path.of(
                    "../Database/Database Updates/028_immutable_economy_ledger.sql"));
            for (String statement : SqlScriptSplitter.split(migration)) execute(connection, statement);

            execute(connection, """
                    INSERT INTO logs_economy
                      (operation_id, user_id, actor_id, operation, reason, currency_type, amount,
                       balance_before, balance_after, context)
                    VALUES ('integration:1', 1, 2, 'grant', 'integration.test', -1, 5, 0, 5, '')
                    """);
            assertThrows(SQLException.class,
                    () -> execute(connection, "UPDATE logs_economy SET amount = 6 WHERE operation_id = 'integration:1'"));
            assertThrows(SQLException.class,
                    () -> execute(connection, "DELETE FROM logs_economy WHERE operation_id = 'integration:1'"));
        } finally {
            try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
                execute(admin, "DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void verifiesBaselineApplyIdempotencyDriftFailureAndLockingOnDisposableSchema()
            throws Exception {
        String host = System.getenv("MIGRATION_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "Set MIGRATION_TEST_DB_HOST to opt into the MariaDB integration test");
        assertTrue(isLoopback(host), "Migration integration tests require a loopback MariaDB host");

        String port = environment("MIGRATION_TEST_DB_PORT", "3306");
        String user = environment("MIGRATION_TEST_DB_USER", "root");
        String password = environment("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "polaris_migration_test_"
                + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mariadb://" + host + ":" + port + "/";
        String schemaUrl = serverUrl + schema;

        try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
            execute(admin, "CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try {
            verifyLifecycle(schemaUrl, user, password, schema);
        } finally {
            try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
                execute(admin, "DROP DATABASE IF EXISTS `" + schema + "`");
                assertFalse(schemaExists(admin, schema),
                        "Disposable migration schema must be removed after the test");
            }
        }
    }

    private static void verifyLifecycle(
            String schemaUrl,
            String user,
            String password,
            String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password)) {
            for (String table : new String[]{"users", "rooms", "items", "emulator_settings"}) {
                execute(connection, "CREATE TABLE `" + table + "` (id INT PRIMARY KEY) ENGINE=InnoDB");
            }

            MigrationCatalog version28 = catalog(
                    "028_integration_probe.sql",
                    "CREATE TABLE migration_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
            DatabaseMigrationRunner runner = new DatabaseMigrationRunner(connection, version28, 2);

            MigrationReport validation = runner.run(MigrationMode.VALIDATE);
            assertEquals(java.util.List.of(28), validation.pendingVersions());
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM schema_migrations WHERE version = 27"));

            MigrationReport firstApply = runner.run(MigrationMode.APPLY);
            MigrationReport secondApply = runner.run(MigrationMode.APPLY);
            assertEquals(java.util.List.of(28), firstApply.appliedVersions());
            assertEquals(java.util.List.of(), secondApply.appliedVersions());
            assertTrue(tableExists(connection, "migration_probe"));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM schema_migrations WHERE version = 28"));

            MigrationCatalog drifted = catalog(
                    "028_integration_probe.sql",
                    "CREATE TABLE migration_probe_changed (id INT PRIMARY KEY) ENGINE=InnoDB");
            assertThrows(MigrationValidationException.class,
                    () -> new DatabaseMigrationRunner(connection, drifted, 2)
                            .run(MigrationMode.VALIDATE));

            Map<String, String> failingScripts = new LinkedHashMap<>();
            failingScripts.put("028_integration_probe.sql",
                    "CREATE TABLE migration_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
            failingScripts.put("029_integration_failure.sql",
                    "CREATE TABLE migration_failure_probe (id INT PRIMARY KEY) ENGINE=InnoDB; "
                            + "THIS IS NOT VALID SQL");
            MigrationCatalog failing = MigrationCatalog.fromResources(failingScripts);
            assertThrows(MigrationExecutionException.class,
                    () -> new DatabaseMigrationRunner(connection, failing, 2)
                            .run(MigrationMode.APPLY));
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM schema_migrations WHERE version = 29"));

            try (Connection lockHolder = DriverManager.getConnection(schemaUrl, user, password)) {
                String lockName = "polaris-migrations-"
                        + MigrationCatalog.sha256(schema).substring(0, 40);
                assertEquals(1, namedLock(lockHolder, "SELECT GET_LOCK(?, 0)", lockName));
                try {
                    assertThrows(MigrationValidationException.class,
                            () -> new DatabaseMigrationRunner(connection, version28, 1)
                                    .run(MigrationMode.VALIDATE));
                } finally {
                    assertEquals(1, namedLock(lockHolder, "SELECT RELEASE_LOCK(?)", lockName));
                }
            }
        }
    }

    private static MigrationCatalog catalog(String name, String sql) {
        return MigrationCatalog.fromResources(Map.of(name, sql));
    }

    private static int namedLock(Connection connection, String sql, String lockName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static boolean schemaExists(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?")) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet result = connection.getMetaData().getTables(
                connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean isLoopback(String host) {
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
