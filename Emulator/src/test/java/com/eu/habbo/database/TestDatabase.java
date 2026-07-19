package com.eu.habbo.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared MariaDB Testcontainers fixture for integration tests. */
public final class TestDatabase {

    /** CI overrides the pinned default for the supported-version matrix. */
    public static final String MARIADB_IMAGE =
            System.getenv().getOrDefault(
                    "POLARIS_TEST_MARIADB_IMAGE",
                    "mariadb:11.4.12@sha256:a794d9eb009e20de605858a11f32f63b4075cbd197c650436f0e3b457e4caed7");

    private static volatile MariaDBContainer<?> container;
    private static volatile HikariDataSource dataSource;

    private TestDatabase() {
    }

    /** Returns whether Testcontainers can reach a Docker daemon. */
    public static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized HikariDataSource sharedDataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        DockerImageName image = DockerImageName.parse(MARIADB_IMAGE)
                .asCompatibleSubstituteFor("mariadb");
        MariaDBContainer<?> c = new MariaDBContainer<>(image)
                .withDatabaseName("habbo_test")
                .withUsername("polaris")
                .withPassword("polaris");
        c.start();
        container = c;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(c.getJdbcUrl());
        config.setUsername(c.getUsername());
        config.setPassword(c.getPassword());
        config.setMaximumPoolSize(4);
        config.setPoolName("polaris-it");
        dataSource = new HikariDataSource(config);
        return dataSource;
    }

    /** Resets the shared test database and returns a caller-owned pool. */
    public static synchronized HikariDataSource freshDatabase(String label) {
        HikariDataSource shared = sharedDataSource();
        try (var connection = shared.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            java.util.List<String> views = new java.util.ArrayList<>();
            try (var rs = statement.executeQuery(
                    "SELECT TABLE_NAME FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()")) {
                while (rs.next()) {
                    views.add(rs.getString(1));
                }
            }
            for (String view : views) {
                statement.execute("DROP VIEW IF EXISTS `" + view + "`");
            }
            java.util.List<String> tables = new java.util.ArrayList<>();
            try (var rs = statement.executeQuery(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            throw new IllegalStateException("Could not reset test database for " + label, e);
        }

        // Keep the suite pool alive when the caller closes its handle.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(2);
        config.setPoolName("polaris-it-" + label);
        return new HikariDataSource(config);
    }
}
