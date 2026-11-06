package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomWiredRuntime;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/** A firing's collected furni moves can be listed in a chosen order, as a group move lists its picks. */
class WiredMoveCarryHelperOrderTest {

    @Test
    void collectedFurniMovesAreListedInTheGivenOrder() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(room.getWiredRuntime()).thenReturn(mock(RoomWiredRuntime.class));
        when(room.moveFurniTo(any(UserItem.class), any(RoomTile.class), anyInt(), any(), anyBoolean()))
                .thenReturn(FurnitureMovementError.NONE);
        UserItem first = item(1, 6, layout);
        UserItem second = item(2, 7, layout);
        RoomTile eight = tile(8, layout);

        WiredMoveCarryHelper.beginMovementCollection();
        int from = WiredMoveCarryHelper.collectedMovementCount();
        // The leading member moves first, then the one picked first.
        WiredMoveCarryHelper.moveFurni(room, null, second, eight, 0, null, false, null);
        WiredMoveCarryHelper.moveFurni(room, null, first, tile(7, layout), 0, null, false, null);
        WiredMoveCarryHelper.orderCollectedFurni(from, List.of(1, 2));
        ServerMessage message = WiredMoveCarryHelper.finishMovementCollection();

        ByteBuf packet = message.get();
        packet.skipBytes(6);
        assertEquals(2, packet.readInt());
        packet.readInt(); // movement type
        assertEquals(6, packet.readInt()); // from x of the first entry: the furni picked first
    }

    private static UserItem item(int id, int x, RoomLayout layout) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) 12);
        tile(x, layout);
        return item;
    }

    private static RoomTile tile(int x, RoomLayout layout) {
        RoomTile tile = new RoomTile((short) x, (short) 12, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) x, (short) 12)).thenReturn(tile);
        return tile;
    }
}
