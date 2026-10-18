package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Walk triggers fire in the tick that announces the step, and the conditions they run ("triggerer is on furni",
 * "furnis have avatars") count the stepping user on the tile it steps to, as in Habbo.
 */
class RoomUnitWalkOnLocationTest {

    @Test
    void aWalkOnTriggerSeesTheUserOnTheTileItStepsTo() throws Exception {
        RoomTile from = new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true);
        RoomTile to = new RoomTile((short) 4, (short) 3, (short) 0, RoomTileState.OPEN, true);
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomItemManager items = mock(RoomItemManager.class);
        HabboItem tile = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(tile.getBaseItem()).thenReturn(base);
        when(tile.canWalkOn(any(), any(), any())).thenReturn(true);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 4, (short) 3)).thenReturn(to);
        when(room.getItemManager()).thenReturn(items);
        when(items.getWalkableItemAt(anyInt(), anyInt())).thenReturn(tile);
        when(room.tileWalkable(to)).thenReturn(true);
        when(room.getItemsAt(to)).thenReturn(new HashSet<>());

        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(from);
        Field goal = RoomUnit.class.getDeclaredField("goalLocation");
        goal.setAccessible(true);
        goal.set(unit, to);
        unit.setPath(new ArrayDeque<>(List.of(to)));

        AtomicReference<RoomTile> seen = new AtomicReference<>();
        doAnswer(invocation -> {
                    seen.set(unit.getWiredLocation());
                    return null;
                })
                .when(tile)
                .onWalkOn(any(), any(), any());

        unit.cycle(room);

        assertSame(to, seen.get());
        assertSame(to, unit.getWiredLocation());
    }
}
