package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import io.netty.buffer.ByteBuf;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.outgoing.wired.WiredEffectDataComposer;

/**
 * The "move user tiles" dialog sends four ints back and saveData reads the tile count from the
 * fourth; the box sent it only three, so every reopen showed one tile whatever had been saved.
 */
class WiredEffectMoveUserTilesTest {

    @Test
    void theDialogGetsTheTileCountAsAThirdInt() throws Exception {
        WiredEffectMoveUserTiles box = new WiredEffectMoveUserTiles(77, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"delay\":0,\"movementDirection\":2,\"rotationDirection\":8,\"userSource\":0,\"tileCount\":7}"),
                null);

        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getTriggers(anyInt(), anyInt())).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        ByteBuf packet = new WiredEffectDataComposer(box, room).compose().get();
        try {
            packet.skipBytes(6); // frame length and header
            // official layout: furniLimit, stuffIds, stuffIds2, spriteId, id
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.skipBytes(packet.readShort()); // the string slot
            // official [move, rotation, tiles]; the user source is the official slot
            assertEquals(3, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(9, packet.readInt()); // our clockwise 8 is the official 9
            assertEquals(7, packet.readInt());
        } finally {
            packet.release();
        }
    }
}
