package com.eu.habbo.database.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record IndexRequirement(
        String name,
        String table,
        List<String> columns,
        String purpose) {

    public IndexRequirement {
        name = normalize(name, "index name");
        table = normalize(table, "table name");
        Objects.requireNonNull(columns, "columns");
        if (columns.isEmpty()) throw new IllegalArgumentException("Index columns must not be empty");
        List<String> normalizedColumns = new ArrayList<>(columns.size());
        for (String column : columns) normalizedColumns.add(normalize(column, "column name"));
        columns = List.copyOf(normalizedColumns);
        purpose = Objects.requireNonNull(purpose, "purpose").trim();
        if (purpose.isEmpty()) throw new IllegalArgumentException("Index purpose must not be blank");
    }

    public String displayName() {
        return table + "." + name + "(" + String.join(",", columns) + ")";
    }

    private static String normalize(String value, String kind) {
        String normalized = Objects.requireNonNull(value, kind).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException(kind + " must not be blank");
        return normalized;
    }
}
