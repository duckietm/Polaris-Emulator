package com.eu.habbo.database.migrations;

public class MigrationValidationException extends IllegalStateException {
    public MigrationValidationException(String message) {
        super(message);
    }

    public MigrationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
