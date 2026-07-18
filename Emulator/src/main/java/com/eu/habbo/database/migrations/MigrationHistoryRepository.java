package com.eu.habbo.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class MigrationHistoryRepository {
    private static final Set<String> POLARIS_CORE_TABLES = Set.of(
            "users", "rooms", "items", "emulator_settings");

    private static final String CREATE_HISTORY_TABLE = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INT UNSIGNED NOT NULL PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                script_name VARCHAR(255) NOT NULL,
                checksum_sha256 CHAR(64) NOT NULL,
                installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                execution_ms BIGINT UNSIGNED NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    private static final String INSERT_HISTORY = """
            INSERT INTO schema_migrations
                (version, description, script_name, checksum_sha256, execution_ms)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final Connection connection;

    public MigrationHistoryRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public boolean historyTableExists() throws SQLException {
        return tableNames().contains("schema_migrations");
    }

    public boolean isRecognizablePolarisSchema() throws SQLException {
        return tableNames().containsAll(POLARIS_CORE_TABLES);
    }

    public void ensureHistoryTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_HISTORY_TABLE);
        }
    }

    public void baselineAt027() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_HISTORY)) {
            bindHistory(
                    statement,
                    MigrationCatalog.BASELINE_VERSION,
                    "historical baseline",
                    "<< baseline >>",
                    "0".repeat(64),
                    0L);
            statement.executeUpdate();
        }
    }

    public List<AppliedMigration> loadApplied() throws SQLException {
        String sql = """
                SELECT version, description, script_name, checksum_sha256, installed_on, execution_ms
                FROM schema_migrations
                ORDER BY version ASC
                """;
        List<AppliedMigration> applied = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                Timestamp installedOn = rows.getTimestamp("installed_on");
                applied.add(new AppliedMigration(
                        rows.getInt("version"),
                        rows.getString("description"),
                        rows.getString("script_name"),
                        rows.getString("checksum_sha256"),
                        installedOn.toInstant(),
                        rows.getLong("execution_ms")));
            }
        }
        return List.copyOf(applied);
    }

    public void recordApplied(MigrationDescriptor migration, long executionMs) throws SQLException {
        Objects.requireNonNull(migration, "migration");
        try (PreparedStatement statement = connection.prepareStatement(INSERT_HISTORY)) {
            bindHistory(
                    statement,
                    migration.version(),
                    migration.description(),
                    migration.scriptName(),
                    migration.checksumSha256(),
                    Math.max(0L, executionMs));
            statement.executeUpdate();
        }
    }

    private Set<String> tableNames() throws SQLException {
        Set<String> tables = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rows = metadata.getTables(
                connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (name != null) tables.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static void bindHistory(
            PreparedStatement statement,
            int version,
            String description,
            String scriptName,
            String checksum,
            long executionMs) throws SQLException {
        statement.setInt(1, version);
        statement.setString(2, description);
        statement.setString(3, scriptName);
        statement.setString(4, checksum);
        statement.setLong(5, executionMs);
    }
}
