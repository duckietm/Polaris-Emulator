package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.messages.outgoing.habbicons.HabbiconInfoComposer;
import polaris.messages.outgoing.habbicons.HabbiconShopDataComposer;
import polaris.messages.outgoing.habbicons.UserHabbiconStatusChangedComposer;
import polaris.messages.outgoing.habbicons.UserHabbiconsComposer;

class HabbiconPacketContractTest {
    private final HabbiconService.Item first = new HabbiconService.Item(61, "toast", 6, 2, 10, 4, 5);
    private final HabbiconService.Item second = new HabbiconService.Item(62, "happy", 6, 3, 11, 5, 6);
    private final HabbiconService.Item reward = new HabbiconService.Item(71, "fine", 6, 1, 0, 0, 0);

    @Test
    void ownedSnapshotContainsClaimableRewardsFavoritesAndOrderedRecents() {
        ByteBuf packet = new UserHabbiconsComposer(snapshot()).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(9465, packet.readShort());
            assertEquals(3, packet.readInt());
            for (HabbiconService.Item item : List.of(first, second, reward)) {
                assertEquals(item.id(), packet.readInt());
                assertEquals(item.state(), packet.readInt());
            }
            assertEquals(2, packet.readInt());
            assertEquals(62, packet.readInt());
            assertEquals(61, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void shopContainsTwoCompleteCollectionRecordsAndNestedItemsWithoutMisalignment() {
        ByteBuf packet = new HabbiconShopDataComposer(snapshot()).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(9467, packet.readShort());
            assertEquals(2, packet.readInt());
            for (HabbiconService.Collection collection : snapshot().collections()) {
                assertEquals(collection.id(), packet.readInt());
                assertEquals(collection.name(), readString(packet));
                assertEquals(collection.completed(), packet.readBoolean());
                assertEquals(collection.rewardId(), packet.readInt());
                assertEquals(collection.rewardState(), packet.readInt());
                assertEquals(collection.credits(), packet.readInt());
                assertEquals(collection.points(), packet.readInt());
                assertEquals(collection.pointsType(), packet.readInt());
                assertEquals(collection.items().size(), packet.readInt());
                for (HabbiconService.Item item : collection.items()) {
                    assertItem(packet, item);
                }
            }
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void infoAndStatusChangeUseTheirFullRequiredWireShapes() {
        ByteBuf info = new HabbiconInfoComposer(second).compose().get();
        ByteBuf status = new UserHabbiconStatusChangedComposer(62, 3).compose().get();
        try {
            info.skipBytes(4);
            assertEquals(9463, info.readShort());
            assertItem(info, second);
            assertFalse(info.isReadable());
            status.skipBytes(4);
            assertEquals(9466, status.readShort());
            assertEquals(62, status.readInt());
            assertEquals(3, status.readInt());
            assertFalse(status.isReadable());
        } finally {
            info.release();
            status.release();
        }
    }

    @Test
    void catalogHabbiconProductUsesItsIdAndCannotBeGiftedOrMultiplied() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("habbicon_id");
        when(rows.getInt("habbicon_id")).thenReturn(61);
        when(rows.getInt("id")).thenReturn(10);
        when(rows.getString("item_Ids")).thenReturn("5");
        when(rows.getString("catalog_name")).thenReturn("toast");
        when(rows.getString("extradata")).thenReturn("");
        CatalogItem item;
        try (var emulator = mockStatic(Emulator.class)) {
            GameEnvironment environment = mock(GameEnvironment.class);
            ItemManager items = mock(ItemManager.class);
            Item giftableFurniture = mock(Item.class);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            when(environment.getItemManager()).thenReturn(items);
            when(items.getItem(5)).thenReturn(giftableFurniture);
            when(giftableFurniture.allowGift()).thenReturn(true);
            item = new CatalogItem(rows);
        }
        ServerMessage message = new ServerMessage();
        message.init(34);
        item.serialize(message);
        ByteBuf packet = message.get();
        try {
            packet.skipBytes(6);
            assertEquals(10, packet.readInt());
            assertEquals("toast", readString(packet));
            assertFalse(packet.readBoolean());
            packet.skipBytes(12);
            assertFalse(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals("habbicon", readString(packet));
            assertEquals(61, packet.readInt());
            assertEquals("61", readString(packet));
            assertEquals(1, packet.readInt());
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readInt());
            assertFalse(packet.readBoolean());
        } finally {
            packet.release();
        }
    }

    private HabbiconService.Snapshot snapshot() {
        Map<Integer, HabbiconService.Item> items = new LinkedHashMap<>();
        for (HabbiconService.Item item : List.of(first, second, reward)) {
            items.put(item.id(), item);
        }
        return new HabbiconService.Snapshot(
                List.of(
                        new HabbiconService.Collection(6, "toast", true, 71, 1, 25, 3, 5, List.of(first, second)),
                        new HabbiconService.Collection(7, "duck", false, 0, 4, 0, 0, 0, List.of())),
                items,
                List.of(62, 61),
                List.of());
    }

    private static void assertItem(ByteBuf packet, HabbiconService.Item item) {
        assertEquals(item.id(), packet.readInt());
        assertEquals(item.name(), readString(packet));
        assertEquals(item.collectionId(), packet.readInt());
        assertEquals(item.state(), packet.readInt());
        assertEquals(item.credits(), packet.readInt());
        assertEquals(item.points(), packet.readInt());
        assertEquals(item.pointsType(), packet.readInt());
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
