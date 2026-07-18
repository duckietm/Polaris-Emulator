package com.eu.habbo.database.migrations;

public final class MigrationExecutionException extends IllegalStateException {
    private final int version;
    private final String scriptName;
    private final int statementNumber;

    public MigrationExecutionException(
            int version,
            String scriptName,
            int statementNumber,
            Throwable cause) {
        super("Migration " + version + " (" + scriptName + ") failed at statement "
                + statementNumber, cause);
        this.version = version;
        this.scriptName = scriptName;
        this.statementNumber = statementNumber;
    }

    public int version() {
        return version;
    }

    public String scriptName() {
        return scriptName;
    }

    public int statementNumber() {
        return statementNumber;
    }
}
