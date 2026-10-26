package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Move/Rotate with no move and no turn puts a furni back on top of its own stack, as AIR does. */
class WiredEffectMoveRotateRestackTest {

    @Test
    void theBottomFurniGoesOnTopAndTheTopOneStays() {
        Room room = mock(Room.class);
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        HabboItem bottom = furni(0);
        HabboItem middle = furni(1);
        HabboItem top = furni(2);
        when(room.getItemsAt(tile)).thenReturn(Set.of(bottom, middle, top));

        assertEquals(3.0, WiredEffectMoveRotateFurni.restackHeight(room, bottom, tile));
        assertNull(WiredEffectMoveRotateFurni.restackHeight(room, top, tile));
    }

    private static HabboItem furni(double z) {
        Item base = mock(Item.class);
        when(base.getHeight()).thenReturn(1.0);
        HabboItem item = mock(HabboItem.class);
        when(item.getZ()).thenReturn(z);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }
}
