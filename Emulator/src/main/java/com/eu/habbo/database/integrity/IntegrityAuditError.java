package com.eu.habbo.database.integrity;

public record IntegrityAuditError(String checkId, String message) {
    public IntegrityAuditError {
        checkId = IntegrityIdentifiers.checkId(checkId);
        message = IntegrityIdentifiers.description(message);
    }
}
