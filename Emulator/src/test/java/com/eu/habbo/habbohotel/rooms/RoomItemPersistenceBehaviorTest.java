package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveDuckets;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RoomItemPersistenceBehaviorTest {

    @Test
    void pendingItemsShareOneConnectionAndReleaseTheRegistryBeforeJdbc() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomItemManager manager = new RoomItemManager(new Room(41, 7));
        Int2ObjectMap<HabboItem> items = items(manager);
        TestItem first = item(1001, "first");
        TestItem second = item(1002, "second");
        first.needsUpdate(true);
        second.needsUpdate(true);
        items.put(first.getId(), first);
        items.put(second.getId(), second);
        AtomicBoolean registryHeldDuringJdbc = new AtomicBoolean();
        dataSource.beforeExecution(ignored -> registryHeldDuringJdbc.compareAndSet(false, Thread.holdsLock(items)));

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            manager.saveAllPendingItems();
        }

        assertEquals(1, dataSource.connectionCount());
        assertFalse(registryHeldDuringJdbc.get());
    }

    @Test
    void pendingItemSaveRetainsUpdateDeleteAndDirtyStateContracts() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomItemManager manager = new RoomItemManager(new Room(41, 7));
        TestItem updated = item(1001, "updated");
        TestItem deleted = item(1002, "deleted");
        TestItem clean = item(1003, "clean");
        updated.needsUpdate(true);
        deleted.needsUpdate(true);
        deleted.needsDelete(true);
        items(manager).put(updated.getId(), updated);
        items(manager).put(deleted.getId(), deleted);
        items(manager).put(clean.getId(), clean);

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            manager.saveAllPendingItems();
            manager.saveAllPendingItems();
        }

        assertEquals(2, dataSource.calls().size());
        RoomJdbcTestSupport.SqlCall update = dataSource.calls().stream()
                .filter(call -> call.sql().startsWith("UPDATE items"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, "
                        + "x = ?, y = ?, z = ?, rot = ?, extra_data = ?, "
                        + "limited_data = ?, wired_data = CASE WHEN ? = 1 THEN ? ELSE wired_data END WHERE id = ?",
                update.sql());
        assertEquals(
                Map.ofEntries(
                        Map.entry(1, 7),
                        Map.entry(2, 41),
                        Map.entry(3, ":w=1,2 l=3,4"),
                        Map.entry(4, 5),
                        Map.entry(5, 6),
                        Map.entry(6, 7.125D),
                        Map.entry(7, 3),
                        Map.entry(8, "updated"),
                        Map.entry(9, "11:2"),
                        Map.entry(10, 0),
                        Map.entry(11, ""),
                        Map.entry(12, 1001)),
                update.parameters());
        RoomJdbcTestSupport.SqlCall delete = dataSource.calls().stream()
                .filter(call -> call.sql().startsWith("DELETE FROM items"))
                .findFirst()
                .orElseThrow();
        assertEquals("DELETE FROM items WHERE id = ?", delete.sql());
        assertEquals(Map.of(1, 1002), delete.parameters());
        assertFalse(updated.needsUpdate());
        assertFalse(updated.needsDelete());
        assertFalse(deleted.needsUpdate());
        assertFalse(deleted.needsDelete());
        assertFalse(clean.needsUpdate());
        assertFalse(clean.needsDelete());
    }

    @Test
    void pendingWiredItemSaveIncludesItsWiredData() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomItemManager manager = new RoomItemManager(new Room(41, 7));
        WiredEffectGiveDuckets wired = new WiredEffectGiveDuckets(1001, 7, mock(Item.class), "0", 0, 0);
        wired.setRoomId(41);
        assertTrue(wired.saveData(
                new WiredSettings(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, "25", new int[0], 0), null));
        wired.needsUpdate(true);
        items(manager).put(wired.getId(), wired);

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            manager.saveAllPendingItems();
        }

        RoomJdbcTestSupport.SqlCall update = dataSource.calls().stream()
                .filter(call -> call.sql().startsWith("UPDATE items"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, update.parameters().get(10));
        assertEquals(wired.getWiredData(), update.parameters().get(11));
        assertEquals(wired.getId(), update.parameters().get(12));
        assertFalse(wired.needsUpdate());
    }

    private static TestItem item(int id, String extraData) {
        TestItem item = new TestItem(id, extraData);
        item.setRoomId(41);
        item.setWallPosition(":w=1,2 l=3,4");
        item.setX((short) 5);
        item.setY((short) 6);
        item.setZ(7.125D);
        item.setRotation(3);
        return item;
    }

    private static Int2ObjectMap<HabboItem> items(RoomItemManager manager) {
        return manager.getRoomItems();
    }

    private static final class TestItem extends HabboItem {

        private TestItem(int id, String extraData) {
            super(id, 7, (Item) null, extraData, 11, 2);
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
