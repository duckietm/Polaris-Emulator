package com.eu.habbo.habbohotel.games.snowwar;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry of SnowWar-specific per-player state, keyed by userId (README 8.1).
 */
public final class SnowWarPlayers {

    private static final ConcurrentHashMap<Integer, SnowWarAttributes> ATTRIBUTES = new ConcurrentHashMap<>();

    private SnowWarPlayers() {
    }

    public static SnowWarAttributes get(int userId) {
        return ATTRIBUTES.computeIfAbsent(userId, id -> new SnowWarAttributes());
    }

    public static void remove(int userId) {
        ATTRIBUTES.remove(userId);
    }
}
