package com.eu.habbo.database.integrity;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseIntegrityAuditorIT {
    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable error) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI", error);
            }
            assumeTrue(false, "Docker/Testcontainers is unavailable: " + error.getMessage());
        }
    }

    @Test
    void detectsLogicalDuplicatesLogicalOrphansAndDiscoveredForeignKeyOrphans()
            throws Exception {
        try (DatabaseFixture fixture = databaseFixture()) {
            HikariDataSource dataSource = fixture.dataSource();
            MigrationRunner.migrate(dataSource);
            IntegrityContract contract = IntegrityContractLoader.load(getClass().getClassLoader());

            IntegrityAuditReport clean;
            try (Connection connection = dataSource.getConnection()) {
                clean = new DatabaseIntegrityAuditor(connection, contract, 2, 30).audit();
            }
            assertTrue(clean.isHealthy(), () -> "Fresh migrated database is not clean: " + clean);

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE integrity_parent (id INT PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("""
                        CREATE TABLE integrity_child (
                            id INT PRIMARY KEY,
                            parent_id INT NOT NULL,
                            CONSTRAINT fk_integrity_parent
                                FOREIGN KEY (parent_id) REFERENCES integrity_parent(id)
                        ) ENGINE=InnoDB
                        """);
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                statement.execute("INSERT INTO integrity_child (id, parent_id) VALUES (1, 404)");
                statement.execute("INSERT INTO users_ignored (user_id, target_id) VALUES (999999, 0), (999999, 0)");
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }

            IntegrityAuditReport report;
            try (Connection connection = dataSource.getConnection()) {
                report = new DatabaseIntegrityAuditor(connection, contract, 2, 30).audit();
            }

            assertFalse(report.isHealthy());
            assertFinding(report, "users.ignored-owner", IntegrityFindingType.ORPHAN);
            IntegrityFinding duplicates = assertFinding(
                    report, "duplicates.ignored-user", IntegrityFindingType.DUPLICATE);
            assertEquals(1, duplicates.affectedRows());
            assertEquals(1, duplicates.groups());
            assertFinding(
                    report,
                    "fk.integrity_child.fk_integrity_parent",
                    IntegrityFindingType.ORPHAN);
            assertTrue(report.findings().stream().allMatch(finding -> finding.samples().size() <= 2));
            assertThrows(
                    IntegrityAuditException.class,
                    () -> DatabaseIntegrityAudit.enforce(report, IntegrityAuditMode.STRICT));
        }
    }

    private static DatabaseFixture databaseFixture() throws Exception {
        String host = System.getenv("MIGRATION_TEST_DB_HOST");
        if (host == null || host.isBlank()) {
            requireDocker();
            return new DatabaseFixture(
                    TestDatabase.freshDatabase("integrity_audit"), null, null, null, null);
        }
        assertTrue(isLoopback(host), "Local MariaDB integration requires a loopback host");
        String port = environment("MIGRATION_TEST_DB_PORT", "3306");
        String user = environment("MIGRATION_TEST_DB_USER", "root");
        String password = environment("MIGRATION_TEST_DB_PASSWORD", "");
        String database = "polaris_integrity_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mariadb://" + host + ':' + port + '/';
        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(serverUrl + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setPoolName("polaris-integrity-external-it");
        return new DatabaseFixture(
                new HikariDataSource(config), serverUrl, database, user, password);
    }

    private static boolean isLoopback(String host) {
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static IntegrityFinding assertFinding(
            IntegrityAuditReport report,
            String checkId,
            IntegrityFindingType type) {
        IntegrityFinding finding = report.findings().stream()
                .filter(candidate -> candidate.checkId().equals(checkId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing finding " + checkId + " in " + report.findings()));
        assertEquals(type, finding.type());
        return finding;
    }

    private record DatabaseFixture(
            HikariDataSource dataSource,
            String serverUrl,
            String database,
            String user,
            String password) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            dataSource.close();
            if (serverUrl == null) return;
            try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        }
    }
}
