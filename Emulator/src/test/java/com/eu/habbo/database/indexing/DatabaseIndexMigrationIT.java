package com.eu.habbo.database.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DatabaseIndexMigrationIT {

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
    void migrationReusesEquivalentIndexesAndIsIdempotentOnMariaDb() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("index_migration");
                Connection connection = dataSource.getConnection()) {
            IndexContract contract = IndexContractLoader.load(getClass().getClassLoader());
            createContractTables(connection, contract);
            execute(
                    connection,
                    "CREATE INDEX custom_badge_covering ON users_badges " + "(user_id, badge_code, slot_id)");

            String migration = Files.readString(
                    Path.of("src/main/resources/db/migration/V20260718190000__query_index_contract.sql"));
            executeMigration(connection, migration);

            IndexAuditReport first = new DatabaseIndexAuditor(connection, contract).audit();
            assertTrue(first.isComplete(), () -> "Missing indexes: " + first.missingRequirements());
            assertFalse(
                    indexExists(connection, "users_badges", "idx_users_badges_user_badge_slot"),
                    "Equivalent custom indexes must be reused instead of duplicated");
            int indexCount = indexCount(connection);

            executeMigration(connection, migration);
            assertEquals(indexCount, indexCount(connection), "Applying the index migration twice must not add indexes");
        }
    }

    private static void createContractTables(Connection connection, IndexContract contract) throws Exception {
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (IndexRequirement requirement : contract.requirements()) {
            columnsByTable
                    .computeIfAbsent(requirement.table(), ignored -> new LinkedHashSet<>())
                    .addAll(requirement.columns());
        }
        for (Map.Entry<String, Set<String>> table : columnsByTable.entrySet()) {
            StringBuilder sql =
                    new StringBuilder("CREATE TABLE `").append(table.getKey()).append("` (");
            int index = 0;
            for (String column : table.getValue()) {
                if (index++ > 0) sql.append(", ");
                sql.append('`')
                        .append(column)
                        .append("` ")
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
        String executableSql = migration
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        for (String statement : executableSql.split(";\\s*")) {
            if (!statement.isBlank()) execute(connection, statement);
        }
    }

    private static boolean indexExists(Connection connection, String table, String index) throws Exception {
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
}
