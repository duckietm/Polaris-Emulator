package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredInternalVariables;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.users.HabboItem;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The official targets, variable types and flags of the variable list (AIR's {@code WiredVariable}). */
class WiredVariableCatalogFlagsTest {

    private static final int FLAGS = 9;

    @Test
    void theTargetsAreAirsCodes() {
        assertEquals(0, RoomWiredVariableCatalog.TARGET_FURNI);
        assertEquals(1, RoomWiredVariableCatalog.TARGET_USER);
        assertEquals(-10, RoomWiredVariableCatalog.TARGET_GLOBAL);
        assertEquals(-20, RoomWiredVariableCatalog.TARGET_CONTEXT);
        assertEquals(RoomWiredVariableCatalog.TARGET_GLOBAL, RoomWiredVariableCatalog.targetOf("room:5"));
        assertEquals(RoomWiredVariableCatalog.TARGET_CONTEXT, RoomWiredVariableCatalog.targetOf("ctx:5"));
        assertEquals(RoomWiredVariableCatalog.TARGET_FURNI, RoomWiredVariableCatalog.targetOf("furni:5"));
        assertEquals(-1, RoomWiredVariableCatalog.targetOf("-125"));
        assertEquals("room:5", RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_GLOBAL, 5));
    }

    @Test
    void aUserVariableIsGivenRemovedWrittenWatchedAndTimed() {
        Block block = block(box("user:7", RoomWiredVariableCatalog.TYPE_CREATED, RoomWiredVariableCatalog.TARGET_USER));

        assertEquals(0, block.type);
        assertEquals(1, block.target);
        assertArrayEquals(new boolean[] {false, true, true, true, true, false, false, true, true}, block.flags);
    }

    @Test
    void aGlobalVariableAlwaysExistsAndIsNeverGivenOrRemoved() {
        Block block =
                block(box("room:7", RoomWiredVariableCatalog.TYPE_CREATED, RoomWiredVariableCatalog.TARGET_GLOBAL));

        assertEquals(-10, block.target);
        assertArrayEquals(new boolean[] {true, false, true, true, true, false, false, true, true}, block.flags);
    }

    @Test
    void aContextVariableIsNotInterceptableAndHasACreationTime() {
        // AIR: a context box's changes cannot be intercepted; it has a creation time, and a last update when it has
        // a value.
        Block block =
                block(box("ctx:7", RoomWiredVariableCatalog.TYPE_CREATED, RoomWiredVariableCatalog.TARGET_CONTEXT));

        assertEquals(-20, block.target);
        assertArrayEquals(new boolean[] {false, true, true, true, false, false, false, true, true}, block.flags);
    }

    @Test
    void aReadOnlyReferenceIsNeitherWrittenNorGiven() {
        RoomWiredVariableCatalog.Variable reference = new RoomWiredVariableCatalog.Variable(
                "user:8",
                RoomWiredVariableCatalog.TYPE_CREATED,
                "shared",
                11,
                RoomWiredVariableCatalog.TARGET_USER,
                true,
                false,
                true);

        assertArrayEquals(
                new boolean[] {false, false, true, false, true, false, false, true, true}, block(reference).flags);
    }

    @Test
    void aSubVariableIsComputedAndNeverGiven() {
        Block block =
                block(box("user:700000001", RoomWiredVariableCatalog.TYPE_SUB, RoomWiredVariableCatalog.TARGET_USER));

        assertEquals(2, block.type);
        assertArrayEquals(new boolean[] {false, false, true, true, true, false, false, false, false}, block.flags);
    }

    @Test
    void positionIsListedUnderHabbosIdAsAPackedValue() {
        RoomWiredVariableCatalog.Variable position = builtIn("-125");

        assertNotNull(position);
        Block block = block(position);
        assertEquals("-125", block.id);
        assertEquals(1, block.type);
        assertEquals("@position", block.name);
        assertEquals(0, block.target);
        assertArrayEquals(new boolean[] {true, false, true, true, true, false, false, false, false}, block.flags);
        assertTrue(block.hasTextConnector);
        assertEquals(0, block.textConnectorSize);
    }

    @Test
    void readOnlyBuiltInsAreNotWritableAndNotWatched() {
        Block block = block(builtIn("-100"));

        assertEquals("@id", block.name);
        assertArrayEquals(new boolean[] {true, false, true, false, false, false, false, false, false}, block.flags);
        assertFalse(block.hasTextConnector);
    }

    @Test
    void handitemsAreGivenAndRemoved() {
        RoomWiredVariableCatalog.Variable handitem = builtIn("-240");

        assertEquals(RoomWiredVariableCatalog.TARGET_USER, handitem.getVariableTarget());
        assertTrue(handitem.canCreateAndDelete());
        assertTrue(handitem.canWriteValue());
    }

    @Test
    void globalAndContextBuiltInsUseTheOfficialTargets() {
        assertEquals(RoomWiredVariableCatalog.TARGET_GLOBAL, builtIn("-310").getVariableTarget());
        assertEquals("@wired_timer", builtIn("-310").getVariableName());
        assertEquals(RoomWiredVariableCatalog.TARGET_CONTEXT, builtIn("-400").getVariableTarget());
        assertFalse(builtIn("-461").isAlwaysAvailable());
    }

    @Test
    void theClockTransactionAndHabbiconBuiltInsAreListedAsAir() {
        RoomWiredVariableCatalog.Variable state = builtIn("-284");
        assertEquals("@transaction.state", state.getVariableName());
        assertFalse(state.canWriteValue(), "read only");
        assertEquals(2, state.getTextConnector().size(), "Adding Items, Confirmed");

        RoomWiredVariableCatalog.Variable pulses = builtIn("~121");
        assertEquals("~clock.pulse_count", pulses.getVariableName());
        assertTrue(pulses.canWriteValue());
        assertTrue(pulses.isInvisible(), "no clock in the room");

        RoomWiredVariableCatalog.Variable habbicon = builtIn("-245");
        assertTrue(habbicon.canCreateAndDelete());
        assertTrue(habbicon.canWriteValue());
        assertEquals(RoomWiredVariableCatalog.TARGET_CONTEXT, builtIn("-452").getVariableTarget());
        assertEquals(RoomWiredVariableCatalog.TARGET_CONTEXT, builtIn("-470").getVariableTarget());
        assertFalse(builtIn("~601").canWriteValue(), "~plant.shape");
    }

    @Test
    void smartVariablesAreInvisibleWhileNothingInTheRoomIsOfTheirKind() {
        RoomWiredVariableCatalog.Variable capacity = builtIn("~133");
        RoomWiredVariableCatalog.Variable petLevel = builtIn("~400");

        assertEquals(RoomWiredVariableCatalog.TYPE_SMART, capacity.getVariableType());
        assertTrue(capacity.isInvisible());
        assertTrue(petLevel.isInvisible());
        assertFalse(builtIn("-100").isInvisible());
    }

    @Test
    void theOfficialTableHasEveryRowOnce() {
        List<RoomWiredInternalVariables.Row> rows = RoomWiredInternalVariables.officialRows();

        assertEquals(162, rows.size());
        assertEquals(
                rows.size(),
                rows.stream().map(RoomWiredInternalVariables.Row::id).distinct().count());
    }

    @Test
    void theBlockKeepsItsGoldenBytes() {
        RoomWiredVariableCatalog.Variable round = new RoomWiredVariableCatalog.Variable(
                "room:9", RoomWiredVariableCatalog.TYPE_CREATED, "round", 10, -10, true, false, false);
        byte[] body = body(new WiredAllVariablesDiffComposer(7, true, List.of(), List.of(round)));

        assertEquals(
                "00000007" + "01" + "00000000" + "00000001" + hex(round.hash())
                        + "0006" + hex("room:9") + "00000000" + "0005" + hex("round") + "0000000a" + "fffffff6"
                        + "01" + "00" + "01" + "01" + "01" + "00" + "00" + "01" + "01" + "00",
                hex(body));
    }

    private static RoomWiredVariableCatalog.Variable box(String id, int type, int target) {
        return new RoomWiredVariableCatalog.Variable(
                id, type, "points", 0, target, true, false, false, Map.of(), null, false);
    }

    private static RoomWiredVariableCatalog.Variable builtIn(String id) {
        Room room = mock(Room.class);
        when(room.getFloorItems()).thenReturn(new HashSet<HabboItem>());
        when(room.getCurrentPets()).thenReturn(new Int2ObjectOpenHashMap<Pet>());
        for (RoomWiredVariableCatalog.Variable variable : RoomWiredInternalVariables.catalogEntries(room)) {
            if (variable.getVariableId().equals(id)) {
                return variable;
            }
        }
        return null;
    }

    private static Block block(RoomWiredVariableCatalog.Variable variable) {
        byte[] body = body(new WiredAllVariablesDiffComposer(1, true, List.of(), List.of(variable)));
        Reader reader = new Reader(body);
        reader.readInt();
        reader.readBoolean();
        reader.readInt();
        reader.readInt();
        reader.readInt(); // hash
        Block block = new Block();
        block.id = reader.readString();
        block.type = reader.readInt();
        block.name = reader.readString();
        reader.readInt(); // availability
        block.target = reader.readInt();
        block.flags = new boolean[FLAGS];
        for (int flag = 0; flag < FLAGS; flag++) {
            block.flags[flag] = reader.readBoolean();
        }
        block.hasTextConnector = reader.readBoolean();
        if (block.hasTextConnector) {
            block.textConnectorSize = reader.readInt();
            for (int entry = 0; entry < block.textConnectorSize; entry++) {
                reader.readInt();
                reader.readString();
            }
            if (reader.readBoolean()) reader.readString();
        }
        assertEquals(0, reader.remaining());
        return block;
    }

    private static byte[] body(WiredAllVariablesDiffComposer composer) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes() - 6];
            packet.getBytes(packet.readerIndex() + 6, bytes);
            return bytes;
        } finally {
            packet.release();
        }
    }

    private static String hex(int value) {
        return String.format("%08x", value);
    }

    private static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    private static final class Block {
        String id;
        int type;
        String name;
        int target;
        boolean[] flags;
        boolean hasTextConnector;
        int textConnectorSize;
    }

    private static final class Reader {
        private final byte[] bytes;
        private int position;

        Reader(byte[] bytes) {
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
