package com.eu.habbo.messages.outgoing.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ConsoleReadReceiptComposerTest {
    @Test
    void carriesOnlyTheReaderId() {
        var packet = new ConsoleReadReceiptComposer(1337).compose().get();
        packet.skipBytes(6);

        assertEquals(1337, packet.readInt());
        assertFalse(packet.isReadable());
    }
}
