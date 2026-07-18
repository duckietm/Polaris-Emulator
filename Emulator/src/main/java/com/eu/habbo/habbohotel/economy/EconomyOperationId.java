package com.eu.habbo.habbohotel.economy;

import java.util.UUID;

public final class EconomyOperationId {
    private EconomyOperationId() {
    }

    public static String create(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("economy operation namespace must not be blank");
        }
        String prefix = namespace.strip().replaceAll("[^a-zA-Z0-9:._-]", "-");
        String operationId = prefix + ":" + UUID.randomUUID();
        if (operationId.length() > 80) operationId = operationId.substring(operationId.length() - 80);
        return operationId;
    }
}
