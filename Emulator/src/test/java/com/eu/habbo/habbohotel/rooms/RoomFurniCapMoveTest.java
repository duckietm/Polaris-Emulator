package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class RoomFurniCapMoveTest {
    @Test
    void aRoomAtTheCapRefusesANewFurniButStillMovesTheOnesItHolds() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        int previousCap = Room.MAXIMUM_FURNI;

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            Room.MAXIMUM_FURNI = 2;
            Room room = new Room(41, 7);
            room.replaceSpecialTypes(new RoomSpecialTypes()); // a loaded room: its items can be looked up by id
            room.getFurniOwnerNames().put(7, "owner");
            HabboItem first = new TestItem(1001, 7);
            HabboItem second = new TestItem(1002, 7);
            room.addHabboItem(first);
            room.addHabboItem(second);
            Habbo habbo = mock(Habbo.class);

            // A furni that would enter a full room is refused, before the tile is even looked at.
            assertEquals(
                    FurnitureMovementError.MAX_ITEMS,
                    room.getItemManager().canPlaceFurnitureAt(new TestItem(1003, 7), habbo, null, 0));

            // One already in the room is not asking for room: the cap does not answer, the tile check does.
            assertEquals(
                    FurnitureMovementError.INVALID_MOVE,
                    room.getItemManager().canPlaceFurnitureAt(first, habbo, null, 0));
        } finally {
            Room.MAXIMUM_FURNI = previousCap;
        }
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
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}
    }
}
