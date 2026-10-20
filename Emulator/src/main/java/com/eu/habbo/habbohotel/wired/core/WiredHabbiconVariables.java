package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUseHabbiconComposer;

/**
 * AIR's {@code @habbicon} (user, the habbicon shown over the head; given, changed and removed by wired) and {@code
 * @event.habbicon} (context, the habbicon the event is about). A user holds {@code @habbicon} while the client shows
 * the bubble, from triggering one or from a box giving one; the change fires Variable Changed, whose event carries the
 * habbicon as {@code @event.habbicon}.
 */
public final class WiredHabbiconVariables {
    static final String HABBICON = "@habbicon";
    static final String EVENT_HABBICON = "@event.habbicon";

    /** The highest habbicon id a client may trigger (TriggerHabbiconEvent). */
    static final int MAX_HABBICON_ID = 1_000_000;

    private WiredHabbiconVariables() {}

    /** The habbicon the unit shows now, 0 for none; null for a unit that cannot show one (a pet). */
    static Integer read(Room room, RoomUnit unit) {
        if (room == null || unit == null || !canShow(unit) || room.getWiredRuntime() == null) return null;
        return room.getWiredRuntime().getHabbicons().current(unit.getId(), System.currentTimeMillis());
    }

    static boolean has(Room room, RoomUnit unit) {
        Integer value = read(room, unit);
        return value != null && value > 0;
    }

    /**
     * Show {@code habbiconId} over the unit, as triggering it does but without the ownership check (a wired give, like
     * a hand item); 0 or less takes the shown one away.
     */
    static boolean write(Room room, RoomUnit unit, int habbiconId) {
        if (room == null || unit == null || !canShow(unit) || room.getWiredRuntime() == null) return false;
        if (habbiconId > MAX_HABBICON_ID) return false;

        int value = Math.max(0, habbiconId);
        room.getWiredRuntime().getHabbicons().show(unit.getId(), value, System.currentTimeMillis());
        room.sendComposer(new RoomUseHabbiconComposer(unit, value).compose());
        return true;
    }

    /**
     * A user triggered {@code habbiconId} (the bubble is already sent): they hold it as {@code @habbicon}, and Variable
     * Changed boxes watching it fire.
     */
    public static void used(Room room, RoomUnit unit, int habbiconId) {
        Integer previous = read(room, unit);
        if (previous == null || habbiconId <= 0) return;

        room.getWiredRuntime().getHabbicons().show(unit.getId(), habbiconId, System.currentTimeMillis());
        WiredInternalVariableSupport.emitUserChange(room, unit, HABBICON, previous, habbiconId);
    }

    /** {@code @event.habbicon}: the event's habbicon, absent when the event is about none. */
    static Integer readEvent(WiredContext ctx) {
        if (ctx == null || ctx.event() == null || ctx.event().getHabbiconId() <= 0) return null;
        return ctx.event().getHabbiconId();
    }

    /** Only avatars show habbicons: users and bots. */
    private static boolean canShow(RoomUnit unit) {
        RoomUnitType type = unit.getRoomUnitType();
        return type == null || type == RoomUnitType.USER || type == RoomUnitType.BOT;
    }
}
