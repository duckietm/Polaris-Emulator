package com.eu.habbo.messages.outgoing.rooms.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IsUserPartOfCompetitionComposerTest {
    @Test
    void encodesTheFlagBeforeTheRoomId() {
        var packet = new IsUserPartOfCompetitionComposer(true, 4242).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(4242, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void saysNoWithoutARoom() {
        var packet = new IsUserPartOfCompetitionComposer(false, 0).compose().get();
        packet.skipBytes(6);

        assertFalse(packet.readBoolean());
        assertEquals(0, packet.readInt());
    }
}
