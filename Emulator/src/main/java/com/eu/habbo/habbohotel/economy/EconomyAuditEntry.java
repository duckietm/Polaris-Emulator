package com.eu.habbo.habbohotel.economy;

public record EconomyAuditEntry(
        String operationId,
        int userId,
        String operation,
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
                "furniture_redeem",
                currencyType,
                amount,
                balanceBefore,
                balanceAfter,
                itemId,
                itemName == null ? "" : itemName);
    }
}
