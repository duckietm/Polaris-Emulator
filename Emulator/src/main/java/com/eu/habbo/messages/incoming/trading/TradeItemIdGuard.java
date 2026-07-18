package com.eu.habbo.messages.incoming.trading;

import java.util.HashSet;
import java.util.Set;

final class TradeItemIdGuard {
    private TradeItemIdGuard() {
    }

    static boolean arePositiveAndUnique(int[] itemIds) {
        if (itemIds == null || itemIds.length == 0) return false;

        Set<Integer> uniqueIds = new HashSet<>(itemIds.length);
        for (int itemId : itemIds) {
            if (itemId <= 0 || !uniqueIds.add(itemId)) return false;
        }

        return true;
    }
}
