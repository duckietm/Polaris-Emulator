package polaris.hotel.rooms.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.outgoing.wired.WiredUserPermanentVariablesListComposer;

/**
 * The holder detail window of the creator tools reads and changes the permanent variables of a holder who is not in
 * the room: only permanent variables are listed, and set / create / delete follow the official actions.
 */
class PermanentUserVariablesTest {
    private static final int ROOM = 44;
    private static final int POINTS = 10;
    private static final int TEMPORARY = 11;
    private static final int LEVEL = 12;
    private static final int DAVE = 7;

    private final MemoryUserVariableRepository database = new MemoryUserVariableRepository();
    private final AtomicInteger clock = new AtomicInteger(1_000);
    private Room room;

    @BeforeEach
    void setUp() throws Exception {
        WiredExtraUserVariable points = definition(POINTS, "points", WiredExtraUserVariable.AVAILABILITY_PERMANENT);
        WiredExtraUserVariable temporary = definition(TEMPORARY, "temp", WiredExtraUserVariable.AVAILABILITY_ROOM);
        WiredExtraUserVariable level = definition(LEVEL, "level", WiredExtraUserVariable.AVAILABILITY_PERMANENT);
        this.room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(points, temporary, level));
        when(this.room.getId()).thenReturn(ROOM);
        when(this.room.getWiredTimezone()).thenReturn("UTC");
        when(this.room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        when(special.getExtra(POINTS)).thenReturn(points);
        when(special.getExtra(TEMPORARY)).thenReturn(temporary);
        when(special.getExtra(LEVEL)).thenReturn(level);
        when(this.room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));
        RoomUserVariableManager manager = new RoomUserVariableManager(this.room, this.database, this.clock::get);
        when(this.room.getUserVariableManager()).thenReturn(manager);
        this.database.usernames.put(DAVE, "dave");
    }

    @Test
    void anAbsentHoldersPermanentVariablesAreListed() {
        this.database.save(ROOM, DAVE, POINTS, 5, 100, 200);
        this.database.save(ROOM, DAVE, TEMPORARY, 1, 100, 200);

        PermanentUserVariables.Listing listing = PermanentUserVariables.of(this.room, DAVE);

        assertEquals(DAVE, listing.holderKey());
        assertEquals("dave", listing.name());
        assertEquals(
                List.of(new PermanentUserVariables.Entry("user:" + POINTS, 5, 100_000L, 200_000L)), listing.entries());
    }

    @Test
    void aHolderWithNothingSavedHasAnEmptyList() {
        assertTrue(PermanentUserVariables.of(this.room, 99).entries().isEmpty());
        assertEquals("", PermanentUserVariables.of(this.room, 99).name());
    }

    @Test
    void theOfficialActionsSetCreateAndDelete() {
        this.database.save(ROOM, DAVE, POINTS, 5, 100, 200);

        assertTrue(
                PermanentUserVariables.apply(this.room, DAVE, "user:" + POINTS, 8, PermanentUserVariables.ACTION_SET));
        assertEquals(8, this.database.row(ROOM, DAVE, POINTS).value());

        // Create gives a variable the holder does not have, and never replaces one it has.
        assertFalse(PermanentUserVariables.apply(
                this.room, DAVE, "user:" + POINTS, 1, PermanentUserVariables.ACTION_CREATE));
        assertEquals(8, this.database.row(ROOM, DAVE, POINTS).value());
        assertTrue(PermanentUserVariables.apply(
                this.room, DAVE, "user:" + LEVEL, 3, PermanentUserVariables.ACTION_CREATE));
        assertEquals(3, this.database.row(ROOM, DAVE, LEVEL).value());

        // Setting a variable the holder does not have fails instead of creating it.
        assertTrue(PermanentUserVariables.apply(
                this.room, DAVE, "user:" + LEVEL, 0, PermanentUserVariables.ACTION_DELETE));
        assertNull(this.database.row(ROOM, DAVE, LEVEL));
        assertFalse(
                PermanentUserVariables.apply(this.room, DAVE, "user:" + LEVEL, 4, PermanentUserVariables.ACTION_SET));
        assertFalse(PermanentUserVariables.apply(
                this.room, DAVE, "user:" + LEVEL, 0, PermanentUserVariables.ACTION_DELETE));
    }

    @Test
    void onlyPermanentUserVariablesCanBeChanged() {
        assertFalse(PermanentUserVariables.apply(
                this.room, DAVE, "user:" + TEMPORARY, 1, PermanentUserVariables.ACTION_CREATE));
        assertFalse(PermanentUserVariables.apply(
                this.room, DAVE, "furni:" + POINTS, 1, PermanentUserVariables.ACTION_CREATE));
        assertFalse(PermanentUserVariables.apply(this.room, DAVE, "user:" + POINTS, 1, 9));
        assertFalse(PermanentUserVariables.apply(this.room, DAVE, "nonsense", 1, PermanentUserVariables.ACTION_SET));
        assertNull(this.database.row(ROOM, DAVE, POINTS));
    }

    @Test
    void theListIsSentInTheOfficialLayout() {
        this.database.save(ROOM, DAVE, POINTS, 5, 100, 0);

        ByteBuf packet = new WiredUserPermanentVariablesListComposer(PermanentUserVariables.of(this.room, DAVE))
                .compose()
                .get();
        packet.skipBytes(6);

        // Entity type 1 (a Habbo) carries no owner block.
        assertEquals(1, packet.readInt());
        assertEquals(DAVE, packet.readInt());
        assertEquals("dave", string(packet));
        assertEquals("", string(packet));
        assertEquals(1, packet.readInt());
        assertEquals("user:" + POINTS, string(packet));
        assertEquals(5, packet.readInt());
        assertEquals(100_000L, packet.readLong());
        assertFalse(string(packet).isEmpty());
        assertEquals(0L, packet.readLong());
        assertEquals("", string(packet));
        assertEquals(0, packet.readableBytes());
    }

    private static String string(ByteBuf packet) {
        byte[] bytes = new byte[packet.readUnsignedShort()];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static WiredExtraUserVariable definition(int id, String name, int availability) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn(
                        "{\"variableName\":\"" + name + "\",\"hasValue\":true,\"availability\":" + availability + "}");
        definition.loadWiredData(set, null);
        return definition;
    }
}
