package com.eu.habbo.messages.incoming.catalog;

final class TargetOfferPurchaseGuard {
    private TargetOfferPurchaseGuard() {
    }

    static int purchasableAmount(int requestedAmount, int purchaseLimit, int alreadyPurchased) {
        if (requestedAmount <= 0 || purchaseLimit <= 0) {
            return 0;
        }

        int remaining = purchaseLimit - Math.max(alreadyPurchased, 0);
        if (remaining <= 0) {
            return 0;
        }

        return Math.min(requestedAmount, remaining);
    }
}
