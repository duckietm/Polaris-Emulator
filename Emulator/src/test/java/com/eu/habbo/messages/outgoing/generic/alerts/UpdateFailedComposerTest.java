package com.eu.habbo.messages.outgoing.generic.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateFailedComposerTest {
    @Test
    void sendsTheKeyThenItsPlaceholderPairsInOrder() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("max_usernames", "20");
        parameters.put("limit", "1000");
        ByteBuf packet = new UpdateFailedComposer("wiredfurni.error.usernames_count", parameters)
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals("wiredfurni.error.usernames_count", readString(packet));
        assertEquals(2, packet.readInt());
        assertEquals("max_usernames", readString(packet));
        assertEquals("20", readString(packet));
        assertEquals("limit", readString(packet));
        assertEquals("1000", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void sendsAnEmptyPairListForAPlainMessage() {
        ByteBuf packet = new UpdateFailedComposer("Invalid wired effect settings")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals("Invalid wired effect settings", readString(packet));
        assertEquals(0, packet.readInt());
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        byte[] bytes = new byte[packet.readUnsignedShort()];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
