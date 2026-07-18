package com.eu.habbo.messages.outgoing.friends;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FriendNotificationComposerTest {
    @Test
    void encodesTypeAvatarAndMessageInRendererOrder() {
        var packet = new FriendNotificationComposer(42, FriendNotificationComposer.ROOM_EVENT, "hello")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(FriendNotificationComposer.ROOM_EVENT, packet.readInt());
        assertEquals(42, packet.readInt());
        assertEquals("hello", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(io.netty.buffer.ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), java.nio.charset.StandardCharsets.UTF_8).toString();
    }
}
