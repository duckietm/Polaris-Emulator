package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.util.HotelDateTimeUtil;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The time in a room's wired timezone (the settings tab), or the hotel's when it has none, and when
 * the room's wired timers were last reset, to the millisecond.
 */
public final class WiredRoomTime {
    private static final Map<Integer, Long> TIMER_RESETS_MS = new ConcurrentHashMap<>();
    /** A Reset Timers waiting for the room tick it takes effect on. */
    private static final Map<Integer, Long> PENDING_RESETS_MS = new ConcurrentHashMap<>();

    private static final long ROOM_TICK_MS = 500L;
    /** Parsed timezones by the text rooms store; an unknown zone maps to an empty Optional. */
    private static final Map<String, Optional<ZoneId>> ZONES = new ConcurrentHashMap<>();

    private static final int MAX_CACHED_ZONES = 1_024;

    private WiredRoomTime() {}

    public static void forgetRoom(int roomId) {
        TIMER_RESETS_MS.remove(roomId);
        PENDING_RESETS_MS.remove(roomId);
    }

    /** Restarts the room timer at {@code nowMs} itself (a reset that is not waiting for a room tick). */
    public static void markTimersReset(Room room, long nowMs) {
        if (room != null) {
            PENDING_RESETS_MS.remove(room.getId());
            TIMER_RESETS_MS.put(room.getId(), nowMs);
        }
    }

    /**
     * Reset Timers: the room timer restarts on the first 500 ms room tick after {@code nowMs}, and until then reads
     * its old value. A second reset while one waits keeps the waiting one, so two resets in one firing restart the
     * timer once (observed on AIR WIN63-202610201032: a check 300 ms after the reset still sees the old timer).
     */
    public static void scheduleTimersReset(Room room, long lastRoomTick, long nowMs) {
        if (room == null) {
            return;
        }
        applyDueReset(room.getId(), nowMs);
        PENDING_RESETS_MS.putIfAbsent(room.getId(), firstTickAfter(lastRoomTick, nowMs));
    }

    /**
     * Milliseconds on the room's wired timer: since the last reset took effect; before any reset, since the room
     * loaded (its whole-second stamp).
     */
    public static long millisSinceTimerReset(Room room, long nowMs) {
        if (room == null) {
            return 0L;
        }

        applyDueReset(room.getId(), nowMs);
        Long resetMs = TIMER_RESETS_MS.get(room.getId());
        long since = (resetMs != null) ? resetMs : room.getLastTimerReset() * 1000L;
        return Math.max(0L, nowMs - since);
    }

    /** {@link #millisSinceTimerReset} in 500 ms pulses, what @wired_timer reads. */
    public static int pulsesSinceTimerReset(Room room, long nowMs) {
        return (int) Math.min(Integer.MAX_VALUE, millisSinceTimerReset(room, nowMs) / ROOM_TICK_MS);
    }

    private static void applyDueReset(int roomId, long nowMs) {
        Long pending = PENDING_RESETS_MS.get(roomId);
        if (pending == null || nowMs < pending) {
            return;
        }
        if (PENDING_RESETS_MS.remove(roomId, pending)) {
            // The timer only moves forward: a later restart already in place is kept.
            TIMER_RESETS_MS.merge(roomId, pending, Math::max);
        }
    }

    /** The first 500 ms room tick strictly after {@code instant}, on the grid of the room's last tick. */
    public static long firstTickAfter(long lastRoomTick, long instant) {
        if (lastRoomTick <= 0) {
            return instant + ROOM_TICK_MS;
        }
        if (lastRoomTick > instant) {
            // Ticks already ran since the instant: the earliest of them on the grid.
            return lastRoomTick - ((lastRoomTick - instant - 1) / ROOM_TICK_MS) * ROOM_TICK_MS;
        }
        return lastRoomTick + (((instant - lastRoomTick) / ROOM_TICK_MS) + 1) * ROOM_TICK_MS;
    }

    public static ZonedDateTime now(Room room) {
        return ZonedDateTime.now(zoneFor(room));
    }

    /** The time in {@code timezone} (a box's own zone), or in the room's zone when it is blank or unknown. */
    public static ZonedDateTime now(Room room, String timezone) {
        return ZonedDateTime.now(zoneFor(room, timezone));
    }

    /** A box's own zone, or the room's wired zone when the box names none or one that does not exist. */
    public static ZoneId zoneFor(Room room, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return zoneFor(room);
        }
        Optional<ZoneId> zone = cached(timezone);
        return zone.isPresent() ? zone.get() : zoneFor(room);
    }

    /** The timezone text a box may store: a known zone id, trimmed, or empty for the room's zone. */
    public static String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "";
        }
        String trimmed = timezone.trim();
        return cached(trimmed).isPresent() ? trimmed : "";
    }

    public static ZoneId zoneFor(Room room) {
        String timezone = (room != null) ? room.getWiredTimezone() : null;

        if (timezone == null || timezone.isBlank()) {
            return HotelDateTimeUtil.getZoneId();
        }

        return cached(timezone).orElseGet(HotelDateTimeUtil::getZoneId);
    }

    private static Optional<ZoneId> cached(String timezone) {
        Optional<ZoneId> zone = ZONES.get(timezone);
        if (zone == null) {
            zone = parse(timezone);
            if (ZONES.size() >= MAX_CACHED_ZONES) {
                ZONES.clear();
            }
            ZONES.put(timezone, zone);
        }
        return zone;
    }

    private static Optional<ZoneId> parse(String timezone) {
        try {
            return Optional.of(ZoneId.of(timezone.trim()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
