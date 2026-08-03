package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogOfferSnapshot;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CatalogAdminOfferDetailsComposerTest {
    @Test
    void serializesEveryEditableOfferFieldIncludingSongId() {
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL,
                99,
                "100:2;200:5",
                42,
                "sound_offer",
                25,
                10,
                5,
                1,
                100,
                -1,
                77,
                321,
                "extra",
                true,
                false);

        ByteBuf payload =
                new CatalogAdminOfferDetailsComposer(offer, 12).compose().get();
        payload.skipBytes(6);

        assertEquals(99, payload.readInt());
        assertEquals(42, payload.readInt());
        assertEquals("100:2;200:5", readString(payload));
        assertEquals("sound_offer", readString(payload));
        assertEquals(25, payload.readInt());
        assertEquals(10, payload.readInt());
        assertEquals(5, payload.readInt());
        assertEquals(1, payload.readInt());
        assertFalse(payload.readBoolean());
        assertEquals("extra", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals(77, payload.readInt());
        assertEquals(100, payload.readInt());
        assertEquals(12, payload.readInt());
        assertEquals(-1, payload.readInt());
        assertEquals(321, payload.readInt());
        assertEquals("NORMAL", readString(payload));
        assertFalse(payload.isReadable());
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
