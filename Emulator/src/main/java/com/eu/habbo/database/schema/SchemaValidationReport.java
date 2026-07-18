package com.eu.habbo.database.schema;

public record SchemaValidationReport(int requiredTables, int requiredColumns) {
    public SchemaValidationReport {
        if (requiredTables < 1) throw new IllegalArgumentException("requiredTables must be positive");
        if (requiredColumns < 1) throw new IllegalArgumentException("requiredColumns must be positive");
    }
}
