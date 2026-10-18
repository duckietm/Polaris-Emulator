package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.threading.ThreadPooling;

/**
 * The glow of Habbo's Teleport to Room: the user wears it from the firing until the forward, and
 * arrives in the linked room still wearing it until that room's third tick. Only the effect packet
 * is sent; the user's own effect is never changed, so it comes back when the glow ends.
 */
public final class RoomForwardGlow {
    public static final int EFFECT_ID = 234;
    static final long ROOM_TICK_MS = 500L;
    static final int ARRIVAL_TICKS = 3;

    private RoomForwardGlow() {}

    /** Shows the glow on this user to the room. */
    public static void show(Room room, RoomUnit unit) {
        if (room != null && unit != null) {
            room.sendComposer(new RoomUserEffectComposer(unit, EFFECT_ID).compose());
        }
    }

    /** Shows the user's own effect again, if they are still in this room. */
    public static void end(Room room, RoomUnit unit) {
        if (room != null && unit != null && unit.getRoom() == room && room.isLoaded()) {
            room.sendComposer(new RoomUserEffectComposer(unit).compose());
        }
    }

    /** The delay until the given tick after {@code now}, the next room tick being the first. */
    static long untilTick(Room room, long now, int tick) {
        long lastCycle = room.getCycleTimestamp();
        long next = (lastCycle <= 0 || lastCycle > now)
                ? now + ROOM_TICK_MS
                : lastCycle + (((now - lastCycle) / ROOM_TICK_MS) + 1) * ROOM_TICK_MS;
        return Math.max(0L, next + (Math.max(1, tick) - 1) * ROOM_TICK_MS - now);
    }

    /** A user forwarded by Teleport to Room enters this room glowing, until its third tick. */
    static void onEnter(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null || habbo.getRoomUnit() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!habbo.getHabboInfo().takeRoomForwardGlow(room.getId(), now)) {
            return;
        }

        RoomUnit unit = habbo.getRoomUnit();
        show(room, unit);

        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) {
            threading.run(() -> end(room, unit), untilTick(room, now, ARRIVAL_TICKS));
        }
    }
}
