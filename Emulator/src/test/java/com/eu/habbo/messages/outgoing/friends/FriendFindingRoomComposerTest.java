package com.eu.habbo.messages.outgoing.friends;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendFindingRoomComposerTest {
    @Test
    void encodesRoomFoundAsTheSingleBooleanExpectedByTheRenderer() {
        var packet = new FriendFindingRoomComposer(FriendFindingRoomComposer.ROOM_FOUND).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertFalse(packet.isReadable());
    }
}
