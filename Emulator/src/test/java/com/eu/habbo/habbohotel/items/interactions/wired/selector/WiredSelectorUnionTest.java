package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ordinary selectors in one stack add up, as in Habbo; each used to replace the one before. */
class WiredSelectorUnionTest {

    private static RoomUnit holding(int handItem) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getHandItem()).thenReturn(handItem);
        when(unit.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        return unit;
    }

    private static WiredEffectUsersHandItem selector(int handItem) {
        WiredEffectUsersHandItem box = new WiredEffectUsersHandItem(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {handItem, 0, 0}, "", new int[0], 0), null);
        return box;
    }

    @Test
    void twoSelectorsSelectBothTheirUsers() {
        RoomUnit cola = holding(5);
        RoomUnit coffee = holding(7);
        RoomUnit empty = holding(0);
        Room room = mock(Room.class);
        when(room.getRoomUnits()).thenReturn(new LinkedHashSet<>(List.of(cola, coffee, empty)));
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        selector(5).execute(ctx);
        selector(7).execute(ctx);

        assertEquals(Set.of(cola, coffee), ctx.targets().users());
    }

    @Test
    void handItemZeroIsAnEmptyHandAsInAir() {
        RoomUnit cola = holding(5);
        RoomUnit empty = holding(0);
        Room room = mock(Room.class);
        when(room.getRoomUnits()).thenReturn(new LinkedHashSet<>(List.of(cola, empty)));
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        selector(0).execute(ctx);

        assertEquals(Set.of(empty), ctx.targets().users());
    }
}
