package com.eu.habbo.database.migration;

/** Aborts startup after a migration or compatibility failure. */
public class MigrationException extends RuntimeException {
    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
