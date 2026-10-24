package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

/**
 * Renting a square in someone else's room used to be judged by the destination tile
 * alone: a tenant could drag any furni in the room - the owner's rare included - into
 * their own square, and move the kinds the placement handler refuses to let them place.
 */
class RoomTenantFurniMoveTest {

    private static final int TENANT = 9;
    private static final int SOMEONE_ELSE = 7;

    private static Habbo tenant() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(TENANT);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static Item base(FurnitureType type) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getType()).thenReturn(type);
        return base;
    }

    private static HabboItem furni(int ownerId, int x, int y) {
        return new TestItem(1000 + x * 100 + y, ownerId, base(FurnitureType.FLOOR), x, y);
    }

    private static RoomTile tileAt(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }

    /** A 1x1 rented square at (5,5). */
    private static HabboItem rentedSquare() {
        return furni(SOMEONE_ELSE, 5, 5);
    }

    @Test
    void aTenantMovesTheirOwnFurniInsideTheirSquare() {
        HabboItem own = furni(TENANT, 5, 5);

        assertEquals(
                FurnitureMovementError.NONE,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantPlacesTheirOwnFurniFromTheirInventory() {
        // Not in the room yet, so there is no origin to be inside the rental.
        HabboItem own = furni(TENANT, 0, 0);

        assertEquals(
                FurnitureMovementError.NONE,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, false));
    }

    @Test
    void aTenantDoesNotMoveSomebodyElsesFurni() {
        HabboItem theirs = furni(SOMEONE_ELSE, 5, 5);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), theirs, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantDoesNotDragTheirOwnFurniInFromOutsideTheSquare() {
        HabboItem own = furni(TENANT, 1, 1);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(5, 5), 0, true));
    }

    @Test
    void aTenantDoesNotMoveTheirOwnFurniOutOfTheSquare() {
        HabboItem own = furni(TENANT, 5, 5);

        assertEquals(
                FurnitureMovementError.NO_RIGHTS,
                RoomItemPlacementService.tenantMoveInRental(rentedSquare(), own, tenant(), tileAt(1, 1), 0, true));
    }

    @Test
    void theKindsTheRentalRefusesAreRefusedWhenMovedToo() {
        Habbo habbo = tenant();

        assertTrue(RoomItemPlacementService.mayTenantMove(furni(TENANT, 5, 5), habbo));
        assertFalse(
                RoomItemPlacementService.mayTenantMove(new TestRoller(1, TENANT, base(FurnitureType.FLOOR)), habbo),
                "a roller is not a tenant's to move");
        assertFalse(
                RoomItemPlacementService.mayTenantMove(new TestItem(2, TENANT, base(FurnitureType.WALL), 5, 5), habbo),
                "a wall item is not a tenant's to move");
    }

    @Test
    void ownershipIsCheckedBeforeTheTypeAndTheGeometry() {
        // Someone else's roller, inside the square: still refused, and for the right reason.
        assertFalse(RoomItemPlacementService.mayTenantMove(
                new TestRoller(3, SOMEONE_ELSE, base(FurnitureType.FLOOR)), tenant()));
    }

    private static class TestItem extends HabboItem {
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
