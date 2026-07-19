package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomItemMovementBehaviorTest {

    @Test
    void explicitHeightMovementRejectsNullItemsAcrossPhysicsModes() {
        RoomItemManager manager = new RoomItemManager(mock(Room.class));
        RoomTile tile = new RoomTile();

        assertEquals(
                FurnitureMovementError.INVALID_MOVE,
                manager.moveFurniTo(
                        null,
                        tile,
                        0,
                        0.0,
                        null,
                        false,
                        false));
        assertEquals(
                FurnitureMovementError.INVALID_MOVE,
                manager.moveFurniToWithPhysics(
                        null,
                        tile,
                        0,
                        0.0,
                        null,
                        false,
                        false,
                        mock(WiredMovementPhysics.class)));
    }

    @Test
    void inactivePhysicsUsesTheNormalFitContract() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(layout.fitsOnMap(tile, 1, 1, 0)).thenReturn(false);
        WiredMovementPhysics physics = mock(WiredMovementPhysics.class);
        when(physics.isActive()).thenReturn(false);
        RoomItemManager manager = new RoomItemManager(room);

        assertEquals(
                manager.furnitureFitsAt(tile, item, 0, true),
                manager.furnitureFitsAtWithPhysics(
                        tile,
                        item,
                        0,
                        true,
                        physics));
    }

    @Test
    void slideRejectsFurnitureThatCannotStackAtTheTarget() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(layout.getTilesAt(tile, 1, 1, 0))
                .thenReturn(Set.of(tile));
        when(item.canStackAt(any(), anyList())).thenReturn(false);

        assertEquals(
                FurnitureMovementError.CANT_STACK,
                new RoomItemManager(room).slideFurniTo(item, tile, 0));
    }
}
