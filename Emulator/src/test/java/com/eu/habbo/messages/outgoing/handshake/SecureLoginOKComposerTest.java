package com.eu.habbo.messages.outgoing.handshake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureLoginOKComposerTest {
    @Test
    void appendsSessionResumeMetadataForCompatibleRenderers() {
        var packet = new SecureLoginOKComposer(true, 42).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(42, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void reportsNormalLoginWithoutARoom() {
        var packet = new SecureLoginOKComposer(false, 0).compose().get();
        packet.skipBytes(6);

        assertFalse(packet.readBoolean());
        assertEquals(0, packet.readInt());
        assertFalse(packet.isReadable());
    }
}
