package com.eu.habbo.habbohotel.economy;

public record EconomyAuditEntry(
        String operationId,
        int userId,
        Integer actorId,
        String operation,
        String reason,
        int currencyType,
        int amount,
        int balanceBefore,
        int balanceAfter,
        Integer itemId,
        String context) {

    public static EconomyAuditEntry redemption(int userId, int itemId, int currencyType, int amount,
                                                int balanceBefore, int balanceAfter, String itemName) {
        if (userId <= 0 || itemId <= 0 || amount <= 0) {
            throw new IllegalArgumentException("redemption audit values must be positive");
        }

        return new EconomyAuditEntry(
                "furniture-redeem:" + itemId,
                userId,
                userId,
                "furniture_redeem",
                "furniture.redeem",
                currencyType,
                amount,
                balanceBefore,
                balanceAfter,
                itemId,
                itemName == null ? "" : itemName);
    }

    public static EconomyAuditEntry from(EconomyOperation operation, int balanceBefore, int balanceAfter) {
        return new EconomyAuditEntry(
                operation.operationId(),
                operation.userId(),
                operation.actorId(),
                operation.operation(),
                operation.reason(),
                operation.currencyType(),
                operation.delta(),
                balanceBefore,
                balanceAfter,
                operation.itemId(),
                operation.context());
    }
}
