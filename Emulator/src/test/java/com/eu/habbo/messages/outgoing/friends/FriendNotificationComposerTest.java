package com.eu.habbo.messages.outgoing.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class FriendNotificationComposerTest {
    @Test
    void encodesAvatarTypeAndMessageInAirOrder() {
        var packet = new FriendNotificationComposer(42, FriendNotificationComposer.ROOM_EVENT, "hello")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals("42", readString(packet));
        assertEquals(FriendNotificationComposer.ROOM_EVENT, packet.readInt());
        assertEquals("hello", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(io.netty.buffer.ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), java.nio.charset.StandardCharsets.UTF_8)
                .toString();
    }
}
