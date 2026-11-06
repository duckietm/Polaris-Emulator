package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

/**
 * Renting a square in someone else's room used to be judged by the destination tile
 * alone: a tenant could drag any furni in the room - the owner's rare included - into
 * their own square, and move the kinds the placement handler refuses to let them place.
 */
class RoomTenantFurniMoveTest {

    private static final int TENANT = 9;
    private static final int SOMEONE_ELSE = 7;

    private static User tenant() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(TENANT);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private static Item base(FurnitureType type) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getType()).thenReturn(type);
        return base;
    }

    private static UserItem furni(int ownerId, int x, int y) {
        return new TestItem(1000 + x * 100 + y, ownerId, base(FurnitureType.FLOOR), x, y);
    }

    private static RoomTile tileAt(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }

    /** A 1x1 rented square at (5,5). */
    private static UserItem rentedSquare() {
        return furni(SOMEONE_ELSE, 5, 5);
    }

    @Test
    void aTenantMovesTheirOwnFurniInsideTheirSquare() {
        UserItem own = furni(TENANT, 5, 5);

        assertEquals(
                FurnitureMovementError.NONE,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantPlacesTheirOwnFurniFromTheirInventory() {
        // Not in the room yet, so there is no origin to be inside the rental.
        UserItem own = furni(TENANT, 0, 0);

        assertEquals(
                FurnitureMovementError.NONE,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, false));
    }

    @Test
    void aTenantDoesNotMoveSomebodyElsesFurni() {
        UserItem theirs = furni(SOMEONE_ELSE, 5, 5);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), theirs, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantDoesNotDragTheirOwnFurniInFromOutsideTheSquare() {
        UserItem own = furni(TENANT, 1, 1);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantDoesNotMoveTheirOwnFurniOutOfTheSquare() {
        UserItem own = furni(TENANT, 5, 5);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(1, 1), 0, true));
    }

    @Test
    void theKindsTheRentalRefusesAreRefusedWhenMovedToo() {
        User user = tenant();

        assertTrue(RoomItemPlacementService.mayTenantMove(furni(TENANT, 5, 5), user));
        assertFalse(
                RoomItemPlacementService.mayTenantMove(new TestRoller(1, TENANT, base(FurnitureType.FLOOR)), user),
                "a roller is not a tenant's to move");
        assertFalse(
                RoomItemPlacementService.mayTenantMove(new TestItem(2, TENANT, base(FurnitureType.WALL), 5, 5), user),
                "a wall item is not a tenant's to move");
    }

    @Test
    void ownershipIsCheckedBeforeTheTypeAndTheGeometry() {
        // Someone else's roller, inside the square: still refused, and for the right reason.
        assertFalse(RoomItemPlacementService.mayTenantMove(
                new TestRoller(3, SOMEONE_ELSE, base(FurnitureType.FLOOR)), tenant()));
    }

    private static class TestItem extends UserItem {
        private TestItem(int id, int ownerId, Item base, int x, int y) {
            super(id, ownerId, base, "0", 0, 0);
            this.setX((short) x);
            this.setY((short) y);
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

    /** A roller is one of the kinds a rental keeps out, whoever owns it. */
    private static final class TestRoller extends InteractionRoller {
        private TestRoller(int id, int ownerId, Item base) {
            super(id, ownerId, base, "0", 0, 0);
        }
    }
}
