package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;

/** The little world a condition needs: a room, one user standing in it, and a wired context. */
final class WiredConditionTestSupport {

    private WiredConditionTestSupport() {}

    static Item boxBase() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }

    /** A user with this id and rank, known to the room through their room unit. */
    static RoomUnit user(Room room, int userId, int rankId) {
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        when(unit.getId()).thenReturn(userId);
        when(rank.getId()).thenReturn(rankId);
        when(info.getId()).thenReturn(userId);
        when(info.getRank()).thenReturn(rank);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(room.getHabbo(userId)).thenReturn(habbo);
        return unit;
    }

    static WiredContext context(Room room, RoomUnit actor) {
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.CUSTOM, room);
        if (actor != null) builder.actor(actor);
        return new WiredContext(builder.build(), null, mock(WiredServices.class), new WiredState(20));
    }

    static WiredSettings settings(int[] ints, int... furniIds) {
        return new WiredSettings(ints, "", furniIds, 0);
    }

    /** A condition save with {@code quantifier} in the save packet's quantifier slot. */
    static WiredSettings quantified(int[] ints, int quantifier) {
        WiredSettings settings = settings(ints);
        settings.setQuantifierCode(quantifier);
        return settings;
    }
}
