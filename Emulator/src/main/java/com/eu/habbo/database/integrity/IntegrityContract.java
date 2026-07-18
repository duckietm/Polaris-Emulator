package com.eu.habbo.database.integrity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record IntegrityContract(
        int schemaVersion,
        List<RelationRequirement> logicalRelations,
        List<DuplicateRequirement> duplicateKeys) {

    public IntegrityContract {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported integrity contract version: " + schemaVersion);
        }
        logicalRelations = List.copyOf(logicalRelations);
        duplicateKeys = List.copyOf(duplicateKeys);
        if (logicalRelations.isEmpty() && duplicateKeys.isEmpty()) {
            throw new IllegalArgumentException("Integrity contract contains no checks");
        }
        Set<String> ids = new HashSet<>();
        logicalRelations.forEach(check -> addId(ids, check.id()));
        duplicateKeys.forEach(check -> addId(ids, check.id()));
    }

    private static void addId(Set<String> ids, String id) {
        if (!ids.add(id)) throw new IllegalArgumentException("Duplicate integrity check id: " + id);
    }
}
