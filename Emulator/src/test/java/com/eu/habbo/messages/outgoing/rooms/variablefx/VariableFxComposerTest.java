package com.eu.habbo.messages.outgoing.rooms.variablefx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxSettings;
import com.eu.habbo.habbohotel.wired.variablefx.VariableFxStatus;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableFxComposerTest {

    @Test
    void statusUpdateWritesOneEntryInReadOrder() {
        VariableFxStatus status =
                new VariableFxStatus("19199|room:42", true, false, 5001, 70L, true, 0L, 100L, Map.of());

        ServerMessage message = new VariableFxStatusUpdateComposer(true, List.of(status)).compose();

        assertEquals(Outgoing.VariableFxStatusUpdateComposer, message.getHeader());
    }

    @Test
    void configRemoveWritesTheCountThenTheIds() {
        ServerMessage message = new VariableFxConfigRemoveComposer(List.of(19199, 19200)).compose();

        assertEquals(Outgoing.VariableFxConfigRemoveComposer, message.getHeader());
    }

    /**
     * The client reads this packet positionally: no field name travels over the wire, only its
     * slot in the sequence. This pins every byte of a two-entry batch - one entry without
     * overrides, one with - against a real {@link ServerMessage} so a reordered, added or dropped
     * field (including the two-int long encoding) fails here instead of at a client months later.
     */
    @Test
    void statusUpdateSerializesEveryFieldInOrder() throws Exception {
        VariableFxStatus withoutOverrides =
                new VariableFxStatus("101|room:7", false, true, 77, 4294967296L, false, 0L, 0L, Map.of("unit", "cm"));
        VariableFxStatus withOverrides =
                new VariableFxStatus("102|room:8", true, false, 88, -5L, true, 10L, 200L, Map.of());

        ServerMessage message =
                new VariableFxStatusUpdateComposer(true, List.of(withoutOverrides, withOverrides)).compose();

        ByteBuf body = message.get();
        try {
            body.skipBytes(6); // 4-byte frame length + 2-byte packet header.

            assertTrue(body.readBoolean(), "initializeAll");
            assertEquals(2, body.readInt(), "status count");

            assertEquals("101|room:7", readString(body), "entry 1 statusKey");
            assertFalse(body.readBoolean(), "entry 1 isInitialize");
            assertTrue(body.readBoolean(), "entry 1 isUserEntity");
            assertEquals(77, body.readInt(), "entry 1 entityId");
            assertEquals(1, body.readInt(), "entry 1 value high word");
            assertEquals(0, body.readInt(), "entry 1 value low word");
            assertFalse(body.readBoolean(), "entry 1 hasOverrides");
            assertEquals(1, body.readInt(), "entry 1 extras count");
            assertEquals("unit", readString(body), "entry 1 extra key");
            assertEquals("cm", readString(body), "entry 1 extra value");

            assertEquals("102|room:8", readString(body), "entry 2 statusKey");
            assertTrue(body.readBoolean(), "entry 2 isInitialize");
            assertFalse(body.readBoolean(), "entry 2 isUserEntity");
            assertEquals(88, body.readInt(), "entry 2 entityId");
            assertEquals(-1, body.readInt(), "entry 2 value high word");
            assertEquals(-5, body.readInt(), "entry 2 value low word");
            assertTrue(body.readBoolean(), "entry 2 hasOverrides");
            assertEquals(0, body.readInt(), "entry 2 overrideMinValue high word");
            assertEquals(10, body.readInt(), "entry 2 overrideMinValue low word");
            assertEquals(0, body.readInt(), "entry 2 overrideMaxValue high word");
            assertEquals(200, body.readInt(), "entry 2 overrideMaxValue low word");
            assertEquals(0, body.readInt(), "entry 2 extras count");

            assertEquals(0, body.readableBytes(), "no bytes beyond the pinned fields");
        } finally {
            body.release();
        }
    }

    @Test
    void configUpdateSerializesEveryFieldInOrder() throws Exception {
        VariableFxSettings settings = new VariableFxSettings(
                VariableFxSettings.SOURCE_USER,
                1,
                2,
                6,
                true,
                30,
                3,
                4,
                5,
                6,
                0L,
                100L,
                false,
                false,
                0,
                0,
                0L,
                0,
                "",
                "",
                "",
                "");
        VariableFxConfig config = new VariableFxConfig(555, 1, settings, Map.of("scale", "2"));

        ServerMessage message = new VariableFxConfigUpdateComposer(List.of(config)).compose();

        ByteBuf body = message.get();
        try {
            body.skipBytes(6); // 4-byte frame length + 2-byte packet header.

            assertEquals(1, body.readInt(), "config count");
            assertEquals(555, body.readInt(), "configId");
            assertTrue(body.readBoolean(), "isUserFx");
            assertEquals(2, body.readInt(), "showMode");
            assertEquals(6, body.readInt(), "showTriggerMask");
            assertTrue(body.readBoolean(), "showOnMouseHover");
            assertEquals(30, body.readInt(), "showDuration");
            assertEquals(1, body.readInt(), "categoryId");
            assertEquals(3, body.readInt(), "styleId");
            assertEquals(4, body.readInt(), "colorId");
            assertEquals(5, body.readInt(), "widthId");
            assertEquals(6, body.readInt(), "rendererId");
            assertEquals(0, body.readInt(), "defaultMinValue high word");
            assertEquals(0, body.readInt(), "defaultMinValue low word");
            assertEquals(0, body.readInt(), "defaultMaxValue high word");
            assertEquals(100, body.readInt(), "defaultMaxValue low word");
            assertEquals(1, body.readInt(), "extras count");
            assertEquals("scale", readString(body), "extra key");
            assertEquals("2", readString(body), "extra value");

            assertEquals(0, body.readableBytes(), "no bytes beyond the pinned fields");
        } finally {
            body.release();
        }
    }

    @Test
    void configRemoveSerializesCountThenIds() throws Exception {
        ServerMessage message = new VariableFxConfigRemoveComposer(List.of(19199, 19200)).compose();

        ByteBuf body = message.get();
        try {
            body.skipBytes(6);

            assertEquals(2, body.readInt(), "config id count");
            assertEquals(19199, body.readInt(), "config id 1");
            assertEquals(19200, body.readInt(), "config id 2");
            assertEquals(0, body.readableBytes(), "no bytes beyond the pinned fields");
        } finally {
            body.release();
        }
    }

    @Test
    void statusRemoveSerializesCountThenKeys() throws Exception {
        ServerMessage message = new VariableFxStatusRemoveComposer(List.of("101|room:7", "102|room:8")).compose();

        ByteBuf body = message.get();
        try {
            body.skipBytes(6);

            assertEquals(2, body.readInt(), "status key count");
            assertEquals("101|room:7", readString(body), "status key 1");
            assertEquals("102|room:8", readString(body), "status key 2");
            assertEquals(0, body.readableBytes(), "no bytes beyond the pinned fields");
        } finally {
            body.release();
        }
    }

    private static String readString(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
