package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

class RoomItemOwnershipBehaviorTest {

    @Test
    void addingAndRemovingItemsMaintainsTheLiveOwnerIndexes() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            Room room = new Room(41, 7);
            UserItem first = new TestItem(1001, 7);
            UserItem second = new TestItem(1002, 7);
            room.getFurniOwnerNames().put(7, "owner");

            room.addUserItem(first);
            room.addUserItem(second);

            assertEquals(2, room.getItemManager().itemCount());
            assertEquals(2, room.getFurniOwnerCount().get(7));
            assertEquals("owner", room.getFurniOwnerName(7));
            assertSame(room.getFurniOwnerNames(), room.getItemManager().getFurniOwnerNames());
            assertSame(room.getFurniOwnerCount(), room.getItemManager().getFurniOwnerCount());

            room.removeUserItem(first);

            assertEquals(1, room.getItemManager().itemCount());
            assertEquals(1, room.getFurniOwnerCount().get(7));
            assertEquals("owner", room.getFurniOwnerName(7));

            room.removeUserItem(second);

            assertEquals(0, room.getItemManager().itemCount());
            assertFalse(room.getFurniOwnerCount().containsKey(7));
            assertFalse(room.getFurniOwnerNames().containsKey(7));
        }
    }

    @Test
    void nullItemMutationsLeaveOwnershipStateUnchanged() {
        Room room = new Room(41, 7);

        room.addUserItem(null);
        room.removeUserItem((UserItem) null);

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

    @Test
    void ejectAllPreservesPostItsAndTheExemptOwnersItems() {
        Room room = new Room(41, 7);
        RoomItemManager manager = room.getItemManager();
        InteractionPostIt postIt = mock(InteractionPostIt.class);
        when(postIt.getId()).thenReturn(1001);
        when(postIt.getUserId()).thenReturn(8);
        UserItem exemptItem = mock(UserItem.class);
        when(exemptItem.getId()).thenReturn(1002);
        when(exemptItem.getUserId()).thenReturn(7);
        manager.getRoomItems().put(1001, postIt);
        manager.getRoomItems().put(1002, exemptItem);
        User exemptOwner = mock(User.class);
        UserInfo exemptInfo = mock(UserInfo.class);
        when(exemptOwner.getUserInfo()).thenReturn(exemptInfo);
        when(exemptInfo.getId()).thenReturn(7);

        manager.ejectAll(exemptOwner);

        assertSame(postIt, manager.getRoomItems().get(1001));
        assertSame(exemptItem, manager.getRoomItems().get(1002));
    }

    private static final class TestItem extends UserItem {
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
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}
    }
}
