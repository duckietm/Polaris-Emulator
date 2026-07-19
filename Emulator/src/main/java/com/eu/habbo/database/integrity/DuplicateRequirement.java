package com.eu.habbo.database.integrity;

import java.util.List;

public record DuplicateRequirement(
        String id,
        String table,
        List<String> columns,
        String description) {

    public DuplicateRequirement {
        id = IntegrityIdentifiers.checkId(id);
        table = IntegrityIdentifiers.identifier(table, "duplicate table");
        columns = IntegrityIdentifiers.identifiers(columns, "duplicate columns");
        description = IntegrityIdentifiers.description(description);
    }
}
