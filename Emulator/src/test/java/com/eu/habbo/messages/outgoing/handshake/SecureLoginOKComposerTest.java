package com.eu.habbo.messages.outgoing.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecureLoginOKComposerTest {
    @Test
    void appendsSessionResumeMetadataForCompatibleRenderers() {
        var packet = new SecureLoginOKComposer(true, 42).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(42, packet.readInt());
        assertEquals("", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void reportsNormalLoginWithoutARoom() {
        var packet = new SecureLoginOKComposer(false, 0).compose().get();
        packet.skipBytes(6);

        assertFalse(packet.readBoolean());
        assertEquals(0, packet.readInt());
        assertEquals("", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void appendsAnOpaqueRecoveryToken() {
        var packet =
                new SecureLoginOKComposer(false, 0, "token-value").compose().get();
        packet.skipBytes(6);
        packet.readBoolean();
        packet.readInt();

        assertEquals("token-value", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(io.netty.buffer.ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8)
                .toString();
    }
}
