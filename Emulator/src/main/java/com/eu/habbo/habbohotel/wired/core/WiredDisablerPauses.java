package com.eu.habbo.habbohotel.wired.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long a room's Wired Disabler has held its wired, in whole 500 ms room ticks. While a disabler is on no wired
 * runs, and delays already running are paused: they resume with the time they had left (observed on AIR). The room
 * cycle samples the disabler once per tick, so each tick it is seen on pauses the room's delays by 500 ms.
 */
public final class WiredDisablerPauses {
    static final long ROOM_TICK_MS = 500L;

    private static final Map<Integer, Long> PAUSED_MS = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> DISABLED = new ConcurrentHashMap<>();

    private WiredDisablerPauses() {}

    /** One room tick: {@code disabled} says whether a Wired Disabler is on now. */
    public static void onRoomTick(int roomId, boolean disabled) {
        if (disabled) {
            PAUSED_MS.merge(roomId, ROOM_TICK_MS, Long::sum);
            DISABLED.put(roomId, Boolean.TRUE);
        } else {
            DISABLED.remove(roomId);
        }
    }

    /** All the time the room's delays were paused so far. */
    public static long pausedMs(int roomId) {
        return PAUSED_MS.getOrDefault(roomId, 0L);
    }

    /** Whether the disabler was on at the room's last tick. */
    public static boolean isPaused(int roomId) {
        return DISABLED.containsKey(roomId);
    }

    public static void forgetRoom(int roomId) {
        PAUSED_MS.remove(roomId);
        DISABLED.remove(roomId);
    }
}
