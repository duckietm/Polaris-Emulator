package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which chest a user is depositing into (official Kigike / start-deposit flow). */
public final class ChestDepositSession {
    private static final Map<Integer, Integer> ACTIVE = new ConcurrentHashMap<>();

    private ChestDepositSession() {
    }

    public static void start(int userId, int chestItemId) {
        if (userId <= 0 || chestItemId <= 0) return;
        ACTIVE.put(userId, chestItemId);
    }

    public static int getChestItemId(int userId) {
        return ACTIVE.getOrDefault(userId, 0);
    }

    public static void clear(int userId) {
        ACTIVE.remove(userId);
    }

    public static boolean isDepositingInto(int userId, int chestItemId) {
        return ACTIVE.getOrDefault(userId, 0) == chestItemId;
    }
}
