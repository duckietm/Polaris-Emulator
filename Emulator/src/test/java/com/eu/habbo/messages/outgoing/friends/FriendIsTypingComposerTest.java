package com.eu.habbo.messages.outgoing.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FriendIsTypingComposerTest {
    @Test
    void encodesSenderAndTypingFlagInRendererOrder() {
        var packet = new FriendIsTypingComposer(42, true).compose().get();
        packet.skipBytes(6);

        assertEquals(42, packet.readInt());
        assertTrue(packet.readBoolean());
        assertFalse(packet.isReadable());
    }

    @Test
    void encodesTheStopAsAFalseFlag() {
        var packet = new FriendIsTypingComposer(42, false).compose().get();
        packet.skipBytes(6);

        assertEquals(42, packet.readInt());
        assertFalse(packet.readBoolean());
    }
}
