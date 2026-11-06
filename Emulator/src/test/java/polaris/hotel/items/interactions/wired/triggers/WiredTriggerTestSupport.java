package polaris.hotel.items.interactions.wired.triggers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.ServerMessage;

/** What a trigger test needs: a base item, a room to serialize against, a database row, and the bytes read back. */
final class WiredTriggerTestSupport {

    private WiredTriggerTestSupport() {}

    static Item boxBase() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    /** A room whose wired index is empty, which is all serializeWiredData asks of it. */
    static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getRoomSpecialTypes()).thenReturn(mock(RoomSpecialTypes.class));
        return room;
    }

    /** A database row holding only this wired_data. */
    static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    /**
     * The wired body every trigger writes: the furni-selection flag and limit, the selected ids, the
     * sprite and item ids, the string slot, the int params, the stuff-type selection code and, last,
     * the trigger code the client picks a dialog by.
     */
    record Body(
            boolean furniSelection,
            int selectionLimit,
            int[] selectedIds,
            int spriteId,
            int itemId,
            String text,
            int[] params,
            int stuffTypeSelectionCode,
            int typeCode) {}

    /** Serializes the trigger and reads the body back in the order it was written. */
    static Body body(InteractionWiredTrigger trigger, Room room) {
        ServerMessage message = new ServerMessage(1);
        trigger.serializeWiredData(message, room);

        ByteBuf buffer = message.get();
        try {
            // ServerMessage frames as [int length][short header][body]; step over both.
            buffer.readInt();
            buffer.readShort();

            boolean furniSelection = buffer.readByte() != 0;
            int selectionLimit = buffer.readInt();
            int[] selectedIds = ints(buffer, buffer.readInt());
            int spriteId = buffer.readInt();
            int itemId = buffer.readInt();
            String text = string(buffer);
            int[] params = ints(buffer, buffer.readInt());
            int stuffTypeSelectionCode = buffer.readInt();
            int typeCode = buffer.readInt();

            return new Body(
                    furniSelection,
                    selectionLimit,
                    selectedIds,
                    spriteId,
                    itemId,
                    text,
                    params,
                    stuffTypeSelectionCode,
                    typeCode);
        } finally {
            buffer.release();
        }
    }

    private static int[] ints(ByteBuf buffer, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = buffer.readInt();
        }
        return values;
    }

    private static String string(ByteBuf buffer) {
        int length = buffer.readShort();
        return buffer.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
