package com.eu.habbo.database.integrity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ForeignKeyDiscovery {
    private static final String SQL = """
            SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
                   REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE()
              AND REFERENCED_TABLE_NAME IS NOT NULL
            ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION
            """;

    private ForeignKeyDiscovery() {
    }

    static List<RelationRequirement> discover(Connection connection, int timeoutSeconds)
            throws SQLException {
        Map<String, MutableForeignKey> keys = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(SQL)) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString("TABLE_NAME");
                    String constraint = rows.getString("CONSTRAINT_NAME");
                    String parentTable = rows.getString("REFERENCED_TABLE_NAME");
                    String key = table + '\u0000' + constraint;
                    MutableForeignKey foreignKey = keys.computeIfAbsent(
                            key,
                            ignored -> new MutableForeignKey(table, constraint, parentTable));
                    if (!foreignKey.parentTable.equals(parentTable)) {
                        throw new SQLException(
                                "Foreign key metadata changed parent table inside constraint " + constraint);
                    }
                    foreignKey.childColumns.add(rows.getString("COLUMN_NAME"));
                    foreignKey.parentColumns.add(rows.getString("REFERENCED_COLUMN_NAME"));
                }
            }
        }
        return keys.values().stream().map(MutableForeignKey::freeze).toList();
    }

    private static final class MutableForeignKey {
        private final String table;
        private final String constraint;
        private final String parentTable;
        private final List<String> childColumns = new ArrayList<>();
        private final List<String> parentColumns = new ArrayList<>();

        private MutableForeignKey(String table, String constraint, String parentTable) {
            this.table = table;
            this.constraint = constraint;
            this.parentTable = parentTable;
        }

        private RelationRequirement freeze() {
            return new RelationRequirement(
                    "fk." + checkIdComponent(table) + '.' + checkIdComponent(constraint),
                    table,
                    childColumns,
                    parentTable,
                    parentColumns,
                    List.of(),
                    "Declared foreign key " + table + "(" + String.join(",", childColumns)
                            + ") -> " + parentTable + "(" + String.join(",", parentColumns) + ")",
                    IntegrityCheckSource.DECLARED_FOREIGN_KEY);
        }

        private static String checkIdComponent(String value) {
            return value.replaceAll("[^A-Za-z0-9_-]", "_");
        }
    }
}
