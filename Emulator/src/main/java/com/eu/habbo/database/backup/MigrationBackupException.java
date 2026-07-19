package com.eu.habbo.database.backup;

public final class MigrationBackupException extends RuntimeException {
    public MigrationBackupException(String message) {
        super(message);
    }

    public MigrationBackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
