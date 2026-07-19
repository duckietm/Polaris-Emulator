package com.eu.habbo.database.integrity;

import java.util.List;
import java.util.Objects;

public record RelationRequirement(
        String id,
        String childTable,
        List<String> childColumns,
        String parentTable,
        List<String> parentColumns,
        List<String> ignoreZeroColumns,
        String description,
        IntegrityCheckSource source) {

    public RelationRequirement {
        id = IntegrityIdentifiers.checkId(id);
        childTable = IntegrityIdentifiers.identifier(childTable, "child table");
        childColumns = IntegrityIdentifiers.identifiers(childColumns, "child columns");
        parentTable = IntegrityIdentifiers.identifier(parentTable, "parent table");
        parentColumns = IntegrityIdentifiers.identifiers(parentColumns, "parent columns");
        ignoreZeroColumns = ignoreZeroColumns == null
                ? List.of()
                : List.copyOf(ignoreZeroColumns);
        ignoreZeroColumns.forEach(column ->
                IntegrityIdentifiers.identifier(column, "zero-sentinel column"));
        if (childColumns.size() != parentColumns.size()) {
            throw new IllegalArgumentException(
                    "Relation " + id + " must have the same number of child and parent columns");
        }
        if (!childColumns.containsAll(ignoreZeroColumns)) {
            throw new IllegalArgumentException(
                    "Relation " + id + " ignores zero on a non-child column");
        }
        if (ignoreZeroColumns.stream().distinct().count() != ignoreZeroColumns.size()) {
            throw new IllegalArgumentException(
                    "Relation " + id + " contains duplicate zero-sentinel columns");
        }
        description = IntegrityIdentifiers.description(description);
        source = Objects.requireNonNull(source, "source");
    }

    String signature() {
        return childTable + ':' + String.join(",", childColumns)
                + "->" + parentTable + ':' + String.join(",", parentColumns);
    }
}
