package com.eu.habbo.messages.outgoing.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.messages.outgoing.unknown.RoomUnknown3Composer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoomAccessFlatIdComposersTest {
    @Test
    void flatAccessDeniedStartsWithTheRoomId() {
        ByteBuf packet = new RoomAccessDeniedComposer(321, "alice").compose().get();
        packet.skipBytes(6);

        assertEquals(321, packet.readInt());
        assertEquals("alice", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void flatAccessibleStartsWithTheRoomId() {
        ByteBuf packet = new HideDoorbellComposer(77, "").compose().get();
        packet.skipBytes(6);

        assertEquals(77, packet.readInt());
        assertEquals("", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void youAreSpectatorCarriesTheRoomId() {
        ByteBuf packet = new RoomUnknown3Composer(9).compose().get();
        packet.skipBytes(6);

        assertEquals(9, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theLegacyConstructorsSendRoomZero() {
        ByteBuf packet = new RoomAccessDeniedComposer("bob").compose().get();
        packet.skipBytes(6);

        assertEquals(0, packet.readInt());
        assertEquals("bob", readString(packet));
    }

    private static String readString(ByteBuf packet) {
        byte[] bytes = new byte[packet.readUnsignedShort()];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
