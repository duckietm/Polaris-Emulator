package com.eu.habbo.database.schema;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SchemaContract {
    private final Map<String, Set<String>> tables;

    public SchemaContract(Map<String, Set<String>> tables) {
        Objects.requireNonNull(tables, "tables");
        if (tables.isEmpty()) throw new IllegalArgumentException("Schema contract must not be empty");

        Map<String, Set<String>> normalized = new TreeMap<>();
        tables.forEach((table, columns) -> {
            String normalizedTable = normalizeName(table, "table");
            Objects.requireNonNull(columns, "columns for " + normalizedTable);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "Schema contract table must contain columns: " + normalizedTable);
            }
            Set<String> normalizedColumns = new TreeSet<>();
            for (String column : columns) {
                normalizedColumns.add(normalizeName(column, "column"));
            }
            normalized.put(normalizedTable, Collections.unmodifiableSet(normalizedColumns));
        });
        this.tables = Collections.unmodifiableMap(normalized);
    }

    public Map<String, Set<String>> tables() {
        return tables;
    }

    public int requiredColumnCount() {
        return tables.values().stream().mapToInt(Set::size).sum();
    }

    private static String normalizeName(String value, String kind) {
        Objects.requireNonNull(value, kind);
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException(kind + " name must not be blank");
        return normalized;
    }
}
