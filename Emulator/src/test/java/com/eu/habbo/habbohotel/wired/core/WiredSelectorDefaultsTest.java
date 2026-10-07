package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersHandItem;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ordinary selectors replace the trigger's default user and furni; they add up only among themselves. */
class WiredSelectorDefaultsTest {

    private static RoomUnit holding(int handItem) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getHandItem()).thenReturn(handItem);
        return unit;
    }

    private static WiredEffectUsersHandItem selector(int handItem) {
        WiredEffectUsersHandItem box = new WiredEffectUsersHandItem(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {handItem, 0, 0}, "", new int[0], 0), null);
        return box;
    }

    @Test
    void theTriggeringUserIsNotKeptBySelectorsThatDoNotPickThem() {
        RoomUnit actor = holding(0);
        RoomUnit cola = holding(5);
        RoomUnit coffee = holding(7);
        HabboItem triggerItem = mock(HabboItem.class);
        Room room = mock(Room.class);
        when(room.getRoomUnits()).thenReturn(new LinkedHashSet<>(List.of(actor, cola, coffee)));
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                triggerItem,
                mock(WiredServices.class),
                new WiredState(20));

        WiredTargets.Defaults defaults = ctx.targets().setDefaultsAside();
        selector(5).execute(ctx);
        selector(7).execute(ctx);
        ctx.targets().restoreDefaults(defaults);

        assertEquals(Set.of(cola, coffee), ctx.targets().users());
        // No furni selector ran, so effects still get the trigger's furni.
        assertEquals(Set.of(triggerItem), ctx.targets().items());
    }
}
