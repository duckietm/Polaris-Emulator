package com.eu.habbo.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers helpers for integration tests. Starts a single throwaway
 * MariaDB for the whole suite (disposed by Ryuk at JVM exit) and builds a Hikari
 * datasource against it. The image is pinned; CI can override the tag for the
 * supported-version matrix.
 */
public final class TestDatabase {

    /** Pinned supported image; CI's version matrix overrides it via
     *  the POLARIS_TEST_MARIADB_IMAGE environment variable. */
    public static final String MARIADB_IMAGE =
            System.getenv().getOrDefault("POLARIS_TEST_MARIADB_IMAGE", "mariadb:11");

    private static volatile MariaDBContainer<?> container;
    private static volatile HikariDataSource dataSource;

    private TestDatabase() {
    }

    /**
     * True when Testcontainers can reach a Docker daemon. Integration tests use
     * this with {@code assumeTrue} so they SKIP (not fail) on a machine without a
     * usable Docker environment, while still running in CI.
     */
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
        MariaDBContainer<?> c = new MariaDBContainer<>(DockerImageName.parse(MARIADB_IMAGE))
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

    /** A fresh, empty datasource on its own database on the same container. */
    public static synchronized HikariDataSource freshDatabase(String name) {
        sharedDataSource();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + name + "`");
            statement.execute("CREATE DATABASE `" + name + "`");
        } catch (Exception e) {
            throw new IllegalStateException("Could not create test database " + name, e);
        }

        HikariConfig config = new HikariConfig();
        String base = container.getJdbcUrl();
        String rebased = base.replaceFirst("/" + container.getDatabaseName() + "(\\?|$)", "/" + name + "$1");
        config.setJdbcUrl(rebased);
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(2);
        config.setPoolName("polaris-it-" + name);
        return new HikariDataSource(config);
    }
}
