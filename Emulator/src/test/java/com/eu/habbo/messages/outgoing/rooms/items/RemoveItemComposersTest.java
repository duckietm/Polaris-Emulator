package com.eu.habbo.messages.outgoing.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AIR's remove packets end with {@code bool byInvisibleFurniManager}; ours only ever remove for good. */
class RemoveItemComposersTest {
    @Test
    void objectRemoveEndsWithTheFlag() {
        ByteBuf packet = new RemoveFloorItemComposer(item(41, 7)).compose().get();
        packet.skipBytes(6);

        assertEquals("41", readString(packet));
        assertFalse(packet.readBoolean());
        assertEquals(7, packet.readInt());
        assertEquals(0, packet.readInt());
        assertFalse(packet.readBoolean());
        assertFalse(packet.isReadable());
        packet.release();
    }

    @Test
    void objectRemoveCanSayTheInvisibleFurniManagerHidIt() {
        ByteBuf packet = new RemoveFloorItemComposer(item(41, 7), true, true).compose().get();
        packet.skipBytes(6);

        readString(packet);
        packet.readBoolean();
        assertEquals(0, packet.readInt());
        packet.readInt();
        assertTrue(packet.readBoolean());
        assertFalse(packet.isReadable());
        packet.release();
    }

    @Test
    void itemRemoveEndsWithTheFlag() {
        ByteBuf packet = new RemoveWallItemComposer(item(42, 8)).compose().get();
        packet.skipBytes(6);

        assertEquals("42", readString(packet));
        assertEquals(8, packet.readInt());
        assertFalse(packet.readBoolean());
        assertFalse(packet.isReadable());
        packet.release();
    }

    @Test
    void bothMultipleVariantsEndWithTheFlag() {
        for (ByteBuf packet : List.of(
                new RemoveFloorItemsComposer(List.of(1, 2), 9).compose().get(),
                new RemoveWallItemsComposer(List.of(1, 2), 9).compose().get())) {
            packet.skipBytes(6);

            assertEquals(2, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(9, packet.readInt());
            assertFalse(packet.readBoolean());
            assertFalse(packet.isReadable());
            packet.release();
        }
    }

    private static HabboItem item(int id, int ownerId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getUserId()).thenReturn(ownerId);
        return item;
    }

    private static String readString(ByteBuf in) {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
