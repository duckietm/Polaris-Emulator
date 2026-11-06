package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomWiredRuntime;
import polaris.hotel.users.UserItem;

class WiredGravityInternalVariableTest {

    @Test
    void gravityIsAvailableForTypedReferenceAndDestinationCapabilities() {
        assertTrue(WiredInternalVariableSupport.canUseFurniReference("@gravity"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("@gravity"));
    }

    @Test
    void readAndWriteDelegateToRoomOwnedSessionState() {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        UserItem item = floorItem();
        when(runtime.setGravityEnabled(item, true)).thenReturn(true);
        when(runtime.isGravityEnabled(item)).thenReturn(true);

        assertTrue(WiredInternalVariableSupport.hasFurniValue(item, "@gravity"));
        assertTrue(WiredInternalVariableSupport.writeFurniValue(room, item, "@gravity", 1));
        assertEquals(1, WiredInternalVariableSupport.readFurniValue(room, item, "@gravity"));
    }

    @Test
    void wallFurnitureFailsClosed() {
        Room room = mock(Room.class);
        UserItem wall = mock(UserItem.class);
        Item wallBase = mock(Item.class);
        when(wall.getBaseItem()).thenReturn(wallBase);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);

        assertFalse(WiredInternalVariableSupport.hasFurniValue(wall, "@gravity"));
        assertFalse(WiredInternalVariableSupport.writeFurniValue(room, wall, "@gravity", 1));
    }

    private static UserItem floorItem() {
        UserItem item = mock(UserItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }
}
