package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class WiredFurniRuntimeStatePolicyTest {

    @Test
    void readReturnsAuthoritativeRoomOwnedState() {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem item = floorItem();
        when(runtime.isGravityEnabled(item)).thenReturn(true);

        WiredFurniRuntimeStatePolicy.Result result = WiredFurniRuntimeStatePolicy.read(room, item, "  @gravity  ");

        assertEquals(1, result.value());
        assertTrue(result.supported());
        assertTrue(result.success());
    }

    @Test
    void writeAcceptsOnlyBooleanValuesAndReturnsCommittedState() {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem item = floorItem();
        when(runtime.setGravityEnabled(item, true)).thenReturn(true);
        when(runtime.isGravityEnabled(item)).thenReturn(true);

        WiredFurniRuntimeStatePolicy.Result result = WiredFurniRuntimeStatePolicy.write(room, item, "@gravity", 1);

        assertEquals(1, result.value());
        assertTrue(result.supported());
        assertTrue(result.success());
        verify(runtime).setGravityEnabled(item, true);

        WiredFurniRuntimeStatePolicy.Result invalid = WiredFurniRuntimeStatePolicy.write(room, item, "@gravity", 2);
        assertFalse(invalid.supported());
        assertFalse(invalid.success());
    }

    @Test
    void unknownOversizedAndWallRequestsFailClosed() {
        Room room = mock(Room.class);

        assertFalse(WiredFurniRuntimeStatePolicy.read(room, floorItem(), "@owner_id")
                .supported());
        assertEquals("", WiredFurniRuntimeStatePolicy.normalizeAllowedKey("x".repeat(65)));

        HabboItem wall = mock(HabboItem.class);
        Item wallBase = mock(Item.class);
        when(wall.getBaseItem()).thenReturn(wallBase);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);
        assertFalse(WiredFurniRuntimeStatePolicy.read(room, wall, "@gravity").supported());
    }

    private static HabboItem floorItem() {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }
}
