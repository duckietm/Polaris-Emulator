package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomItemOwnershipBehaviorTest {

    @Test
    void addingAndRemovingItemsMaintainsTheLiveOwnerIndexes() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(dataSource)) {
            Room room = new Room(41, 7);
            HabboItem first = new TestItem(1001, 7);
            HabboItem second = new TestItem(1002, 7);
            room.getFurniOwnerNames().put(7, "owner");

            room.addHabboItem(first);
            room.addHabboItem(second);

            assertEquals(2, room.getItemManager().itemCount());
            assertEquals(2, room.getFurniOwnerCount().get(7));
            assertEquals("owner", room.getFurniOwnerName(7));
            assertSame(room.getFurniOwnerNames(), room.getItemManager().getFurniOwnerNames());
            assertSame(room.getFurniOwnerCount(), room.getItemManager().getFurniOwnerCount());

            room.removeHabboItem(first);

            assertEquals(1, room.getItemManager().itemCount());
            assertEquals(1, room.getFurniOwnerCount().get(7));
            assertEquals("owner", room.getFurniOwnerName(7));

            room.removeHabboItem(second);

            assertEquals(0, room.getItemManager().itemCount());
            assertFalse(room.getFurniOwnerCount().containsKey(7));
            assertFalse(room.getFurniOwnerNames().containsKey(7));
        }
    }

    @Test
    void nullItemMutationsLeaveOwnershipStateUnchanged() {
        Room room = new Room(41, 7);

        room.addHabboItem(null);
        room.removeHabboItem((HabboItem) null);

        assertEquals(0, room.getItemManager().itemCount());
        assertEquals(0, room.getFurniOwnerCount().size());
        assertEquals(0, room.getFurniOwnerNames().size());
    }

    @Test
    void itemIndexesKeepTheirLiveIdentityWhenCleared() {
        Room room = new Room(41, 7);
        RoomItemManager manager = room.getItemManager();
        var items = manager.getRoomItems();
        var ownerNames = manager.getFurniOwnerNames();
        var ownerCounts = manager.getFurniOwnerCount();
        var tileCache = manager.tileCache;

        items.put(1001, new TestItem(1001, 7));
        ownerNames.put(7, "owner");
        ownerCounts.put(7, 1);
        tileCache.put(new RoomTile(), Set.of());

        manager.clear();

        assertSame(items, manager.getRoomItems());
        assertSame(ownerNames, manager.getFurniOwnerNames());
        assertSame(ownerCounts, manager.getFurniOwnerCount());
        assertSame(tileCache, manager.tileCache);
        assertTrue(items.isEmpty());
        assertTrue(ownerNames.isEmpty());
        assertTrue(ownerCounts.isEmpty());
        assertTrue(tileCache.isEmpty());
    }

    private static final class TestItem extends HabboItem {
        private TestItem(int id, int ownerId) {
            super(id, ownerId, (Item) null, "0", 0, 0);
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
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
        }
    }
}
