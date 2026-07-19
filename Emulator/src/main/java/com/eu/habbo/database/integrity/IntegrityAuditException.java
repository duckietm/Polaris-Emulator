package com.eu.habbo.database.integrity;

public final class IntegrityAuditException extends IllegalStateException {
    public IntegrityAuditException(String message) {
        super(message);
    }

    public IntegrityAuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
