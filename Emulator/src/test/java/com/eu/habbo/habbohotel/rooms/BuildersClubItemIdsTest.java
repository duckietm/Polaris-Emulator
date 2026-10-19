package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniPicks;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.protocol.WiredDefinitionWire;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Builders Club furni travel with ids from the official band; the database ids stay inside the emulator. */
class BuildersClubItemIdsTest {
    private static final int FIRST = BuildersClubItemIds.FIRST;

    private final Room first = room(9_101);
    private final Room second = room(9_102);

    private static Room room(int id) {
        Room room = new Room(id, 7);
        room.replaceSpecialTypes(new RoomSpecialTypes());
        return room;
    }

    @AfterEach
    void unload() {
        this.first.getItemManager().clear();
        this.second.getItemManager().clear();
    }

    @Test
    void theBandIsTheOfficialOne() {
        assertEquals(2_147_418_112, BuildersClubItemIds.FIRST);
        assertEquals(Integer.MAX_VALUE, BuildersClubItemIds.LAST);
        assertEquals(65_536, BuildersClubItemIds.SIZE);
        assertTrue(BuildersClubItemIds.isBandId(FIRST));
        assertTrue(BuildersClubItemIds.isBandId(Integer.MAX_VALUE));
        assertEquals(false, BuildersClubItemIds.isBandId(FIRST - 1));
        // The temporary (Wired) band sits right below and never overlaps.
        assertEquals(FIRST - 1, ItemManager.LAST_TEMPORARY_ITEM_ID);
    }

    @Test
    void buildersClubFurniGetBandIdsUniquePerRoomAndTranslateBothWays() throws Exception {
        HabboItem lamp = place(this.first, 9_100_001, true);
        HabboItem chair = place(this.first, 9_100_002, true);
        HabboItem table = place(this.second, 9_100_003, true);

        assertEquals(FIRST, BuildersClubItemIds.toWire(lamp));
        assertEquals(FIRST + 1, BuildersClubItemIds.toWire(chair));
        // Another room hands out its own ids; the database id tells them apart on the way out.
        assertEquals(FIRST, BuildersClubItemIds.toWire(table));

        assertEquals(9_100_001, BuildersClubItemIds.fromWire(this.first, FIRST));
        assertEquals(9_100_002, BuildersClubItemIds.fromWire(this.first, FIRST + 1));
        assertEquals(9_100_003, BuildersClubItemIds.fromWire(this.second, FIRST));
        assertSame(lamp, this.first.getHabboItem(FIRST));
        assertSame(lamp, this.first.getHabboItem(9_100_001));
        assertSame(table, this.second.getHabboItem(FIRST));
        assertArrayEquals(
                new int[] {9_100_001, 9_100_002, 42},
                BuildersClubItemIds.fromWire(this.first, new int[] {FIRST, FIRST + 1, 42}));
    }

    @Test
    void normalAndTemporaryFurniKeepTheirIds() throws Exception {
        HabboItem normal = place(this.first, 9_100_010, false);

        assertEquals(9_100_010, BuildersClubItemIds.toWire(normal));
        assertEquals(9_100_010, BuildersClubItemIds.fromWire(this.first, 9_100_010));
        assertEquals(
                ItemManager.FIRST_TEMPORARY_ITEM_ID,
                BuildersClubItemIds.fromWire(this.first, ItemManager.FIRST_TEMPORARY_ITEM_ID));
        assertEquals(
                ItemManager.LAST_TEMPORARY_ITEM_ID, BuildersClubItemIds.toWire(ItemManager.LAST_TEMPORARY_ITEM_ID));
        assertEquals(9_100_010, floorId(normal));
        assertEquals(0, BuildersClubItemIds.toWire((HabboItem) null));
        assertEquals(-5, BuildersClubItemIds.toWire(-5), "gift-adjusted inventory ids pass through");
    }

    @Test
    void aBandIdNoFurniHoldsResolvesToNothing() throws Exception {
        place(this.first, 9_100_020, true);

        assertEquals(0, BuildersClubItemIds.fromWire(this.first, FIRST + 500));
        assertNull(this.first.getHabboItem(FIRST + 500));
        assertEquals(0, BuildersClubItemIds.fromWire(null, FIRST));
        assertEquals(77, BuildersClubItemIds.fromWire(null, 77));
    }

    @Test
    void fieldsThatOnlySometimesHoldFurniTranslateOnlyThen() throws Exception {
        place(this.first, 9_100_025, true);

        assertEquals(FIRST, BuildersClubItemIds.toWire(9_100_025, true));
        assertEquals(9_100_025, BuildersClubItemIds.toWire(9_100_025, false), "a user id that equals a furni id");
        assertEquals(9_100_025, BuildersClubItemIds.fromWire(this.first, FIRST, true));
        assertEquals(FIRST, BuildersClubItemIds.fromWire(this.first, FIRST, false));
    }

    @Test
    void roomItemPacketsCarryTheBandId() throws Exception {
        HabboItem lamp = place(this.first, 9_100_030, true);
        HabboItem poster = place(this.first, 9_100_031, true);

        assertEquals(FIRST, floorId(lamp));
        ServerMessage wall = new ServerMessage(1);
        poster.serializeWallData(wall);
        ByteBuf in = wall.get();
        in.skipBytes(6);
        assertEquals(Integer.toString(FIRST + 1), readString(in));
        in.release();
    }

    @Test
    void aRemovedFurniKeepsItsIdForTheRemovalPacketAndTheIdIsNotReusedBeforeTheBandWraps() throws Exception {
        HabboItem lamp = place(this.first, 9_100_040, true);
        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                RoomJdbcTestSupport.install(new RoomJdbcTestSupport.RecordingDataSource())) {
            this.first.removeHabboItem(lamp);
        }

        assertNull(this.first.getHabboItem(FIRST));
        assertEquals(0, BuildersClubItemIds.fromWire(this.first, FIRST));
        ByteBuf removal = new RemoveFloorItemComposer(lamp).compose().get();
        removal.skipBytes(6);
        assertEquals(Integer.toString(FIRST), readString(removal));
        removal.release();

        HabboItem chair = place(this.first, 9_100_041, true);
        assertEquals(FIRST + 1, BuildersClubItemIds.toWire(chair));
    }

    @Test
    void unloadingTheRoomForgetsItsIds() throws Exception {
        place(this.first, 9_100_050, true);
        assertEquals(FIRST, BuildersClubItemIds.toWire(9_100_050));

        this.first.getItemManager().clear();

        assertEquals(9_100_050, BuildersClubItemIds.toWire(9_100_050));
        assertEquals(0, BuildersClubItemIds.fromWire(this.first, FIRST));
    }

    @Test
    void theBandRefusesToOverflowAndReusesAFreedIdOnlyAfterWrapping() {
        RoomBuildersClubIdBand band = new RoomBuildersClubIdBand();
        try {
            for (int i = 0; i < BuildersClubItemIds.SIZE; i++) {
                assertEquals(FIRST + i, band.assign(20_000_000 + i));
            }
            assertThrows(IllegalStateException.class, () -> band.assign(30_000_000));

            band.release(20_000_005);
            assertEquals(FIRST + 5, band.assign(30_000_001));
            assertEquals(30_000_001, band.itemOf(FIRST + 5));
            assertEquals(FIRST + 5, BuildersClubItemIds.toWire(30_000_001));
            assertEquals(20_000_005, BuildersClubItemIds.toWire(20_000_005), "the old holder lost the reused id");
        } finally {
            band.clear();
        }
        assertEquals(20_000_000, BuildersClubItemIds.toWire(20_000_000));
    }

    @Test
    void wiredPicksAreSavedWithDatabaseIdsAndSentWithBandIds() throws Exception {
        HabboItem lamp = place(this.first, 9_100_060, true);
        HabboItem normal = place(this.first, 9_100_061, false);
        WiredEffectFurniPicks picks = new WiredEffectFurniPicks(9_100_062, 7, mock(Item.class), "", 0, 0);
        place(this.first, picks, true);

        // The client picked the lamp by its band id and the normal furni by its database id.
        WiredSettings settings = new WiredSettings(new int[0], "", new int[] {FIRST, 9_100_061}, -1);
        BuildersClubItemIds.fromWire(settings, this.first);
        assertTrue(picks.saveData(settings, null));

        String stored = picks.getWiredData();
        assertTrue(stored.contains("9100060") && stored.contains("9100061"), stored);
        assertEquals(false, stored.contains(Integer.toString(FIRST)), stored);

        // Reloaded from the database, the room hands out ids again; the editor shows the current ones.
        this.first.getItemManager().clear();
        place(this.first, normal, false);
        place(this.first, picks, true);
        place(this.first, lamp, true);
        ResultSet set = Mockito.mock(ResultSet.class);
        Mockito.when(set.getString("wired_data")).thenReturn(stored);
        picks.loadWiredData(set, this.first);

        ServerMessage legacy = new ServerMessage(1);
        picks.serializeWiredData(legacy, this.first);
        ServerMessage official = new ServerMessage(2);
        WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.SELECTOR, official, picks);
        ByteBuf in = official.get();
        in.skipBytes(6);
        in.readInt(); // furni limit
        int[] stuffIds = new int[in.readInt()];
        for (int i = 0; i < stuffIds.length; i++) stuffIds[i] = in.readInt();
        in.skipBytes(in.readInt() * 4); // second picks
        in.readInt(); // sprite
        int boxId = in.readInt();
        in.release();

        assertArrayEquals(new int[] {FIRST + 1, 9_100_061}, stuffIds);
        assertEquals(FIRST, boxId);
    }

    private static HabboItem place(Room room, int id, boolean buildersClub) throws Exception {
        TestItem item = new TestItem(id);
        place(room, item, buildersClub);
        return item;
    }

    private static void place(Room room, HabboItem item, boolean buildersClub) throws Exception {
        Field field = RoomItemManager.class.getDeclaredField("ownership");
        field.setAccessible(true);
        RoomItemOwnershipService ownership = (RoomItemOwnershipService) field.get(room.getItemManager());
        ownership.add(item, buildersClub, false);
    }

    private static int floorId(HabboItem item) {
        ServerMessage message = new ServerMessage(1);
        item.serializeFloorData(message);
        ByteBuf in = message.get();
        in.skipBytes(6);
        int id = in.readInt();
        in.release();
        return id;
    }

    private static String readString(ByteBuf in) {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class TestItem extends HabboItem {
        private TestItem(int id) {
            super(id, BuildersClubRoomSupport.VIRTUAL_OWNER_ID, baseItem(), "0", 0, 0);
        }

        private static Item baseItem() {
            ItemInteraction interaction = mock(ItemInteraction.class);
            Mockito.doReturn(TestItem.class).when(interaction).getType();
            Item item = mock(Item.class);
            Mockito.when(item.getInteractionType()).thenReturn(interaction);
            return item;
        }

        @Override
        public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
            return false;
        }

        @Override
        public boolean isWalkable() {
            return false;
        }

        @Override
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}
    }
}
