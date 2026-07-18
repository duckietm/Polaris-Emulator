package com.eu.habbo.database.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DatabaseSchemaValidator {
    private final Connection connection;
    private final SchemaContract contract;

    public DatabaseSchemaValidator(Connection connection, SchemaContract contract) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.contract = Objects.requireNonNull(contract, "contract");
    }

    public SchemaValidationReport validate() {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, String> actualTables = loadTables(metadata);
            List<String> missingTables = new ArrayList<>();
            List<String> missingColumns = new ArrayList<>();

            for (Map.Entry<String, Set<String>> required : contract.tables().entrySet()) {
                String actualTable = actualTables.get(required.getKey());
                if (actualTable == null) {
                    missingTables.add(required.getKey());
                    continue;
                }
                Set<String> actualColumns = loadColumns(metadata, actualTable);
                for (String column : required.getValue()) {
                    if (!actualColumns.contains(column)) {
                        missingColumns.add(required.getKey() + "." + column);
                    }
                }
            }

            if (!missingTables.isEmpty() || !missingColumns.isEmpty()) {
                throw new SchemaValidationException(buildFailure(missingTables, missingColumns));
            }
            return new SchemaValidationReport(
                    contract.tables().size(), contract.requiredColumnCount());
        } catch (SQLException error) {
            throw new SchemaValidationException("Unable to inspect the database schema", error);
        }
    }

    private Map<String, String> loadTables(DatabaseMetaData metadata) throws SQLException {
        Map<String, String> tables = new HashMap<>();
        try (ResultSet rows = metadata.getTables(
                connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (name != null) tables.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return tables;
    }

    private Set<String> loadColumns(DatabaseMetaData metadata, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet rows = metadata.getColumns(
                connection.getCatalog(), null, table, "%")) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME");
                if (name != null) columns.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static String buildFailure(List<String> tables, List<String> columns) {
        List<String> details = new ArrayList<>();
        if (!tables.isEmpty()) details.add("missing tables: " + String.join(", ", tables));
        if (!columns.isEmpty()) details.add("missing columns: " + String.join(", ", columns));
        return "Database schema validation failed; " + String.join("; ", details);
    }
}
