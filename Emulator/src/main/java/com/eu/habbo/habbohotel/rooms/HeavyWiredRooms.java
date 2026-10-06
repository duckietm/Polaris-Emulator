package com.eu.habbo.habbohotel.rooms;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rooms whose wired the monitor marked heavy. Their timers and signal chains run on the wired heavy
 * workers, so they cannot slow down the other rooms on their usual worker. A room stays heavy until
 * it has been calm for {@link #CALM_BEFORE_RELEASE_MS}, so a bursty room does not hop between workers.
 */
public final class HeavyWiredRooms {
    public static final long CALM_BEFORE_RELEASE_MS = 30_000L;

    /** 0 while heavy, else when the room calmed down. */
    private static final ConcurrentHashMap<Integer, Long> CALM_SINCE = new ConcurrentHashMap<>();

    private HeavyWiredRooms() {}

    public static void mark(int roomId, boolean heavy, long now) {
        if (heavy) {
            CALM_SINCE.put(roomId, 0L);
        } else {
            CALM_SINCE.computeIfPresent(
                    roomId, (ignored, calmSince) -> calmSince == 0L ? Math.max(1L, now) : calmSince);
        }
    }

    public static boolean isHeavy(int roomId, long now) {
        Long calmSince = CALM_SINCE.get(roomId);
        if (calmSince == null) {
            return false;
        }
        if (calmSince == 0L || now - calmSince < CALM_BEFORE_RELEASE_MS) {
            return true;
        }
        CALM_SINCE.remove(roomId, calmSince);
        return false;
    }

    public static void forget(int roomId) {
        CALM_SINCE.remove(roomId);
    }

    public static void forgetAll() {
        CALM_SINCE.clear();
    }

    public static int count() {
        return CALM_SINCE.size();
    }
}
