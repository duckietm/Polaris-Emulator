package com.eu.habbo.messages.outgoing.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** A purchase with no catalog item still sends an offer the client reads field for field. */
class PurchaseOKComposerFallbackTest {

    @Test
    void theFallbackOfferHasTheClientsFieldTypes() {
        ServerMessage message = new PurchaseOKComposer().compose();
        ByteBuf packet = message.get();
        packet.skipBytes(6);

        assertEquals(0, packet.readInt()); // offer id
        assertEquals("", readString(packet)); // localization id
        assertFalse(packet.readBoolean()); // rent
        assertEquals(0, packet.readInt()); // credits
        assertEquals(0, packet.readInt()); // activity points
        assertEquals(0, packet.readInt()); // activity point type
        assertTrue(packet.readBoolean()); // giftable
        assertEquals(1, packet.readInt()); // products
        assertEquals("s", readString(packet));
        assertEquals(0, packet.readInt()); // furni class id
        assertEquals("", readString(packet)); // extra param
        assertEquals(1, packet.readInt()); // product count
        assertFalse(packet.readBoolean()); // limited
        assertEquals(0, packet.readInt()); // club level
        assertFalse(packet.readBoolean()); // bundle purchase allowed
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
