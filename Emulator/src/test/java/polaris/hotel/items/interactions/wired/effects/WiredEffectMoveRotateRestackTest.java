package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.users.UserItem;

/** Move/Rotate with no move and no turn puts a furni back on top of its own stack, as AIR does. */
class WiredEffectMoveRotateRestackTest {

    @Test
    void theBottomFurniGoesOnTopAndTheTopOneStays() {
        Room room = mock(Room.class);
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        UserItem bottom = furni(0);
        UserItem middle = furni(1);
        UserItem top = furni(2);
        when(room.getItemsAt(tile)).thenReturn(Set.of(bottom, middle, top));

        assertEquals(3.0, WiredEffectMoveRotateFurni.restackHeight(room, bottom, tile));
        assertNull(WiredEffectMoveRotateFurni.restackHeight(room, top, tile));
    }

    private static UserItem furni(double z) {
        Item base = mock(Item.class);
        when(base.getHeight()).thenReturn(1.0);
        UserItem item = mock(UserItem.class);
        when(item.getZ()).thenReturn(z);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }
}
