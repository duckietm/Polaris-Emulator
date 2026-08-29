package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The chest writes its configuration as key-value furni data, and it has to write <em>only</em> that.
 *
 * <p>A room's furniture arrives as one list, read straight through. An item that writes one field too
 * many leaves the reader standing in the wrong place, and every item after it is parsed as nonsense —
 * the room draws empty. That is not a subtle bug and it is not a visible one either: the server logs
 * nothing and the client reports nothing, the furniture is simply not there.
 *
 * <p>It has happened once already, by calling {@code super.serializeExtradata}, which appends a second
 * format header and the legacy string after the map. These tests read the bytes back and insist the
 * buffer ends exactly where the map ends.
 */
class ChestExtradataWireTest {

    /** Nitro's key-value furni data format. */
    private static final int MAP_FORMAT = 1;

    private static final int LIMITED_FLAG = 256;

    /** A chest with no room, no database and nothing but its contents. */
    private static final class TestChest extends InteractionWiredChest {
        private final boolean limited;

        private TestChest(boolean limited) {
            super(1, 2, mock(Item.class), "", limited ? 5 : 0, limited ? 3 : 0);
            this.limited = limited;
        }

        @Override
        public boolean isLimited() {
            return this.limited;
        }

        @Override
        public int getLimitedSells() {
            return 3;
        }

        @Override
        public int getLimitedStack() {
            return 5;
        }

        @Override
        protected int visualState() {
            return 1;
        }

        @Override
        protected int storedCount() {
            return 42;
        }

        @Override
        public void serializeWiredData(ServerMessage message, Room room) {}

        @Override
        public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
            return false;
        }
    }

    private static ChestFurniStoredItem storedFurni(int baseItemId, int spriteId) {
        ChestFurniStoredItem item = new ChestFurniStoredItem();
        item.baseItemId = baseItemId;
        item.spriteId = spriteId;
        item.extradata = "0";
        return item;
    }

    /** Read a serialized chest back: the format header, then the map, then whatever is left. */
    private record Wire(int format, Map<String, String> data, int trailingBytes) {}

    private static Wire read(InteractionWiredChest chest) {
        ServerMessage message = new ServerMessage(1);
        chest.serializeExtradata(message);

        ByteBuf buffer = message.get();
        try {
            // ServerMessage frames as [int length][short header][body]; step over both.
            buffer.readInt();
            buffer.readShort();

            int format = buffer.readInt();
            int pairs = buffer.readInt();

            Map<String, String> data = new LinkedHashMap<>();
            for (int i = 0; i < pairs; i++) {
                data.put(readString(buffer), readString(buffer));
            }

            return new Wire(format, data, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static String readString(ByteBuf buffer) {
        int length = buffer.readShort();
        return buffer.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    @Test
    void writesTheMapAndStopsThere() {
        Wire wire = read(new TestChest(false));

        assertEquals(MAP_FORMAT, wire.format());
        assertEquals(
                0,
                wire.trailingBytes(),
                "the chest wrote past its map; every furni after it in the room list is now misread");
    }

    @Test
    void aLimitedChestWritesOnlyTheLimitedPairAfterTheMap() {
        Wire wire = read(new TestChest(true));

        assertEquals(MAP_FORMAT + LIMITED_FLAG, wire.format());
        assertEquals(8, wire.trailingBytes(), "expected exactly the two limited ints and nothing else");
    }

    @Test
    void aRestingFurniChestIsClosed() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);

        // Exactly what the database holds for a real chest: the default appearance mode, nobody
        // looking inside. The sprite's frame 0 is the closed chest, so this must be "0".
        assertEquals(0, chest.getContents().getAppearanceState());
        assertEquals("0", read(chest).data().get("state"));
    }

    @Test
    void aFurniChestOpensWhileSomebodyLooksInside() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);
        Habbo viewer = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(viewer.getHabboInfo().getId()).thenReturn(99);

        chest.openFor(viewer, null);
        assertEquals("1", read(chest).data().get("state"));

        chest.closeFor(viewer, null);
        assertEquals("0", read(chest).data().get("state"));
    }

    @Test
    void aViewerWhoLeftTheRoomStopsHoldingTheLidOpen() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);
        Habbo viewer = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(viewer.getHabboInfo().getId()).thenReturn(99);

        chest.openFor(viewer, null);
        assertEquals("1", read(chest).data().get("state"));

        // They are gone, and their client never got to say so -- a reload, a crash, an older build.
        Room room = mock(Room.class);
        when(room.getHabbo(99)).thenReturn(null);
        chest.pruneViewers(room);

        assertEquals(
                "0", read(chest).data().get("state"), "a chest whose only viewer has left must not stay open forever");
    }

    @Test
    void aCoinChestClosesItsLidWhenNobodyIsLooking() {
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(1, 2, mock(Item.class), "", 0, 0);
        chest.getContents().add(ChestStorage.KIND_CURRENCY, -1, 500);

        // The coin chest's sprite states are one axis: 0 is the closed chest, 1 to 4 are the open one
        // with more and more gold. Money inside is not a reason to have the lid up.
        assertEquals("0", read(chest).data().get("state"));
    }

    @Test
    void anEmptyCoinChestStillOpensItsLid() {
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(1, 2, mock(Item.class), "", 0, 0);
        Habbo viewer = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(viewer.getHabboInfo().getId()).thenReturn(99);

        chest.openFor(viewer, null);

        // An open-but-empty chest has its own sprite; without this an empty chest read as shut even
        // while somebody had it open, and only appeared to work once coins went in.
        assertEquals("1", read(chest).data().get("state"));
    }

    @Test
    void aFullCoinChestShowsTheFullestPile() {
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(1, 2, mock(Item.class), "", 0, 0);
        Habbo viewer = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(viewer.getHabboInfo().getId()).thenReturn(99);
        chest.openFor(viewer, null);

        chest.getContents()
                .add(ChestStorage.KIND_CURRENCY, -1, chest.getContents().getCapacity());

        assertEquals("4", read(chest).data().get("state"));
    }

    @Test
    void aChestShowsNothingUntilItsOwnerAsksItTo() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);
        chest.getContents().addFurniItem(storedFurni(1389, 9500));

        // What is in a chest is its owner's business until they put it on show.
        assertEquals("", read(chest).data().get("visuals"));
        assertEquals("0", read(chest).data().get("preview_mode"));
    }

    @Test
    void aChestOnShowNamesDistinctTypesOnly() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);
        chest.getContents().setPreview(1, 4);
        chest.getContents().addFurniItem(storedFurni(1389, 9500));
        chest.getContents().addFurniItem(storedFurni(1389, 9500));
        chest.getContents().addFurniItem(storedFurni(77, 88));

        // Four of the same chair on a lid says less than four different things.
        // isWallItem,typeId entries joined by semicolons -- the shape the client reads.
        assertEquals("false,9500;false,88", read(chest).data().get("visuals"));
    }

    @Test
    void aChestOnShowStopsAtTheCountItsOwnerChose() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(1, 2, mock(Item.class), "", 0, 0);
        chest.getContents().setPreview(1, 1);
        chest.getContents().addFurniItem(storedFurni(1389, 9500));
        chest.getContents().addFurniItem(storedFurni(77, 88));

        assertEquals("false,9500", read(chest).data().get("visuals"));
    }

    @Test
    void carriesTheKeysTheOfficialWindowReads() {
        Map<String, String> data = read(new TestChest(false)).data();

        // `state` is the one the renderer turns into an animation state; the rest is what the official
        // chest window reads off the furni rather than off a packet of its own.
        assertTrue(data.containsKey("state"), "without state the sprite can never move");
        assertEquals("1", data.get("state"));
        assertEquals("42", data.get("contents_count"));

        for (String key : new String[] {
            "chest_name",
            "chest_desc",
            "locked",
            "auto_lock",
            "capacity",
            "capacity_level",
            "everyone_can_open",
            "everyone_can_donate",
            "state_control_mode",
            "notify_mode",
            "is_wired_enabled"
        }) {
            assertTrue(data.containsKey(key), "missing key the official window reads: " + key);
        }
    }
}
