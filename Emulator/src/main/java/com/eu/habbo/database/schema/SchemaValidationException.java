package com.eu.habbo.database.schema;

public final class SchemaValidationException extends IllegalStateException {
    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
