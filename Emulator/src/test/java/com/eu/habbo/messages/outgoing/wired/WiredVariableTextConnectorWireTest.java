package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The text-connector table of a wired variable reaches both the official diff and the creator tools packet. */
class WiredVariableTextConnectorWireTest {

    private static Map<Integer, String> swordAndShield() {
        Map<Integer, String> mappings = new LinkedHashMap<>();
        mappings.put(1, "Sword");
        mappings.put(2, "Shield");
        return mappings;
    }

    private static RoomWiredVariableCatalog.Variable variable(Map<Integer, String> textConnector) {
        return new RoomWiredVariableCatalog.Variable(
                "user:42", 0, "Weapon", 0, 1, true, !textConnector.isEmpty(), false, textConnector);
    }

    @Test
    void theDiffWritesTheTableAfterTheFlagsWhenTheVariableIsTextConnected() {
        WireReader reader =
                compose(new WiredAllVariablesDiffComposer(7, true, List.of(), List.of(variable(swordAndShield()))));

        assertEquals(7, reader.readInt()); // allVariablesHash
        assertTrue(reader.readBoolean()); // lastChunk
        assertEquals(0, reader.readInt()); // removed
        assertEquals(1, reader.readInt()); // addedOrUpdated
        reader.readInt(); // hash
        assertEquals("user:42", reader.readString());
        assertEquals(0, reader.readInt()); // variableType: made with a box
        assertEquals("Weapon", reader.readString());
        assertEquals(0, reader.readInt());
        assertEquals(1, reader.readInt());
        for (int flag = 0; flag < 9; flag++) {
            reader.readBoolean();
        }
        assertTrue(reader.readBoolean(), "hasTextConnector");
        assertEquals(2, reader.readInt());
        assertEquals(1, reader.readInt());
        assertEquals("Sword", reader.readString());
        assertEquals(2, reader.readInt());
        assertEquals("Shield", reader.readString());
        assertFalse(reader.readBoolean(), "hasTextConnectorFallback");
        assertEquals(0, reader.remaining());
    }

    @Test
    void theDiffWritesTheFallbackTextAfterTheTable() {
        RoomWiredVariableCatalog.Variable variable = new RoomWiredVariableCatalog.Variable(
                "user:42", 0, "Weapon", 0, 1, true, true, false, swordAndShield(), "Fists", false);
        WireReader reader = compose(new WiredAllVariablesDiffComposer(7, true, List.of(), List.of(variable)));

        skipToFlags(reader);
        for (int flag = 0; flag < 9; flag++) {
            reader.readBoolean();
        }
        assertTrue(reader.readBoolean(), "hasTextConnector");
        assertEquals(2, reader.readInt());
        reader.readInt();
        reader.readString();
        reader.readInt();
        reader.readString();
        assertTrue(reader.readBoolean(), "hasTextConnectorFallback");
        assertEquals("Fists", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void aPointerIsSentAsRequiringAnExecutionContext() {
        RoomWiredVariableCatalog.Variable pointer = new RoomWiredVariableCatalog.Variable(
                "furni:9", 0, "target", 0, 0, true, false, false, Map.of(), null, true);
        WireReader reader = compose(new WiredAllVariablesDiffComposer(7, true, List.of(), List.of(pointer)));

        skipToFlags(reader);
        boolean[] flags = new boolean[9];
        for (int flag = 0; flag < 9; flag++) {
            flags[flag] = reader.readBoolean();
        }
        assertFalse(flags[5], "isInvisible");
        assertTrue(flags[6], "requiresExecutionContext");
        assertFalse(reader.readBoolean(), "hasTextConnector");
        assertEquals(0, reader.remaining());
        assertNotEquals(
                pointer.hash(),
                new RoomWiredVariableCatalog.Variable("furni:9", 0, "target", 0, 0, true, false, false, Map.of())
                        .hash());
    }

    /** Reads the diff header and the variable's id, type, name, availability and target. */
    private static void skipToFlags(WireReader reader) {
        reader.readInt();
        reader.readBoolean();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readString();
        reader.readInt();
        reader.readString();
        reader.readInt();
        reader.readInt();
    }

    @Test
    void theDiffKeepsTheLegacyShapeWhenThereIsNoTable() {
        WireReader reader = compose(new WiredAllVariablesDiffComposer(7, true, List.of(), List.of(variable(Map.of()))));

        reader.readInt();
        reader.readBoolean();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readString();
        reader.readInt();
        reader.readString();
        reader.readInt();
        reader.readInt();
        for (int flag = 0; flag < 9; flag++) {
            reader.readBoolean();
        }
        assertFalse(reader.readBoolean(), "hasTextConnector");
        assertEquals(0, reader.remaining());
    }

    @Test
    void renamingAValueChangesTheVariableHashSoTheClientResyncs() {
        Map<Integer, String> renamed = swordAndShield();
        renamed.put(2, "Buckler");

        assertNotEquals(variable(swordAndShield()).hash(), variable(renamed).hash());
        assertEquals(
                variable(swordAndShield()).hash(), variable(swordAndShield()).hash());
    }

    @Test
    void theCreatorToolsPacketAppendsTheTableAsTrailingJson() {
        WiredUserVariablesDataComposer.TextConnectorMetadata metadata =
                new WiredUserVariablesDataComposer.TextConnectorMetadata(
                        42, WiredUserVariablesDataComposer.METADATA_TYPE_USER, swordAndShield());
        WireReader reader = compose(new WiredUserVariablesDataComposer(null, null, null, List.of(), List.of(metadata)));

        assertEquals(0, reader.readInt()); // roomId
        for (int section = 0; section < 7; section++) {
            assertEquals(0, reader.readInt());
        }
        String json = reader.readString();
        assertEquals(
                "[{\"itemId\":42,\"variableType\":2,\"textConnector\":[{\"key\":1,\"value\":\"Sword\"},{\"key\":2,\"value\":\"Shield\"}]}]",
                json);
        assertEquals(0, reader.remaining());
    }

    @Test
    void theCreatorToolsPacketStaysUnchangedWithoutTables() {
        WireReader reader = compose(new WiredUserVariablesDataComposer(null, null, null, List.of(), List.of()));

        for (int section = 0; section < 8; section++) {
            reader.readInt();
        }
        assertEquals(0, reader.remaining());
    }

    private static WireReader compose(MessageComposer composer) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            return new WireReader(bytes);
        } finally {
            packet.release();
        }
    }

    /** Reads the framed packet back: four-byte length, two-byte header, then the body. */
    private static final class WireReader {
        private final byte[] bytes;
        private int position = 6;

        WireReader(byte[] bytes) {
            this.bytes = bytes;
        }

        int readInt() {
            int value = ((this.bytes[this.position] & 0xff) << 24)
                    | ((this.bytes[this.position + 1] & 0xff) << 16)
                    | ((this.bytes[this.position + 2] & 0xff) << 8)
                    | (this.bytes[this.position + 3] & 0xff);
            this.position += 4;
            return value;
        }

        boolean readBoolean() {
            return this.bytes[this.position++] != 0;
        }

        String readString() {
            int length = ((this.bytes[this.position] & 0xff) << 8) | (this.bytes[this.position + 1] & 0xff);
            this.position += 2;
            String value = new String(this.bytes, this.position, length, StandardCharsets.UTF_8);
            this.position += length;
            return value;
        }

        int remaining() {
            return this.bytes.length - this.position;
        }
    }
}
