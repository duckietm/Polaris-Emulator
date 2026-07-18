package com.eu.habbo.database.indexing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIndexMigrationIntegrationTest {
    @Test
    void migrationReusesEquivalentIndexesAndIsIdempotentOnMariaDb() throws Exception {
        String host = System.getenv("MIGRATION_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "Set MIGRATION_TEST_DB_HOST to opt into the MariaDB integration test");
        assertTrue(isLoopback(host), "Migration integration tests require a loopback MariaDB host");

        String port = environment("MIGRATION_TEST_DB_PORT", "3306");
        String user = environment("MIGRATION_TEST_DB_USER", "root");
        String password = environment("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "polaris_index_test_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mariadb://" + host + ":" + port + "/";

        try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
            execute(admin, "CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try (Connection connection = DriverManager.getConnection(serverUrl + schema, user, password)) {
            IndexContract contract = IndexContractLoader.load(getClass().getClassLoader());
            createContractTables(connection, contract);
            execute(connection, "CREATE INDEX custom_badge_covering ON users_badges "
                    + "(user_id, badge_code, slot_id)");

            String migration = Files.readString(Path.of(
                    "src/main/resources/db/migration/V20260718190000__query_index_contract.sql"));
            executeMigration(connection, migration);

            IndexAuditReport first = new DatabaseIndexAuditor(connection, contract).audit();
            assertTrue(first.isComplete(), () -> "Missing indexes: " + first.missingRequirements());
            assertFalse(indexExists(connection, "users_badges",
                    "idx_users_badges_user_badge_slot"),
                    "Equivalent custom indexes must be reused instead of duplicated");
            int indexCount = indexCount(connection);

            executeMigration(connection, migration);
            assertEquals(indexCount, indexCount(connection),
                    "Applying the index migration twice must not add indexes");
        } finally {
            try (Connection admin = DriverManager.getConnection(serverUrl, user, password)) {
                execute(admin, "DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    private static void createContractTables(Connection connection, IndexContract contract)
            throws Exception {
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (IndexRequirement requirement : contract.requirements()) {
            columnsByTable.computeIfAbsent(requirement.table(), ignored -> new LinkedHashSet<>())
                    .addAll(requirement.columns());
        }
        for (Map.Entry<String, Set<String>> table : columnsByTable.entrySet()) {
            StringBuilder sql = new StringBuilder("CREATE TABLE `")
                    .append(table.getKey()).append("` (");
            int index = 0;
            for (String column : table.getValue()) {
                if (index++ > 0) sql.append(", ");
                sql.append('`').append(column).append("` ")
                        .append(isTextColumn(column) ? "VARCHAR(64)" : "INT")
                        .append(" NOT NULL");
            }
            sql.append(") ENGINE=InnoDB");
            execute(connection, sql.toString());
        }
    }

    private static boolean isTextColumn(String column) {
        return column.equals("badge_code") || column.equals("achievement_name");
    }

    private static void executeMigration(Connection connection, String migration) throws Exception {
        String executableSql = migration.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        for (String statement : executableSql.split(";\\s*")) {
            if (!statement.isBlank()) execute(connection, statement);
        }
    }

    private static boolean indexExists(Connection connection, String table, String index)
            throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    private static int indexCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(DISTINCT TABLE_NAME, INDEX_NAME)
                     FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                     """)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
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
