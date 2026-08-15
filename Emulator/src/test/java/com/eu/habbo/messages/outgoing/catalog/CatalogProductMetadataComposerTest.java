package com.eu.habbo.messages.outgoing.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogProductMetadataComposerTest {

    @Test
    void payloadKeepsVersionCorrelationAndItemCapabilitiesInOrder() {
        ByteBuf payload = new CatalogProductMetadataComposer(
                        "metadata-1",
                        33,
                        List.of(
                                new CatalogProductMetadataEntry(51, 701, 9001, true, true),
                                new CatalogProductMetadataEntry(52, 702, 9002, true, false)))
                .compose()
                .get();

        payload.skipBytes(4);
        assertEquals(Outgoing.CatalogProductMetadataComposer, payload.readUnsignedShort());
        assertEquals(1, payload.readInt());
        assertEquals("metadata-1", readString(payload));
        assertEquals(33, payload.readInt());
        assertEquals(2, payload.readInt());
        assertEntry(payload, 51, 701, 9001, 3);
        assertEntry(payload, 52, 702, 9002, 1);
        assertFalse(payload.isReadable());
    }

    private static void assertEntry(ByteBuf payload, int offerId, int itemBaseId, int productClassId, int flags) {
        assertEquals(offerId, payload.readInt());
        assertEquals(itemBaseId, payload.readInt());
        assertEquals(productClassId, payload.readInt());
        assertEquals(flags, payload.readUnsignedByte());
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
