package com.eu.habbo.habbohotel.economy;

public record EconomyOperation(
        String operationId,
        int userId,
        Integer actorId,
        String operation,
        String reason,
        int currencyType,
        int delta,
        Integer itemId,
        String context) {

    public EconomyOperation {
        operationId = requireText(operationId, 96, "operationId");
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (actorId != null && actorId <= 0) throw new IllegalArgumentException("actorId must be positive");
        operation = requireText(operation, 64, "operation");
        reason = requireText(reason, 96, "reason");
        if (currencyType < EconomyLedger.CREDITS) throw new IllegalArgumentException("currencyType is invalid");
        if (delta == 0) throw new IllegalArgumentException("delta must not be zero");
        if (itemId != null && itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
        context = context == null ? "" : context;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.strip();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
