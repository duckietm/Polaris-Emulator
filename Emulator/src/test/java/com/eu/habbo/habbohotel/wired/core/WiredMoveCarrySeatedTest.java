package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A seat that wired moves on its tile (an altitude write on a repeater) takes the users sitting on it along in its own
 * animation, without a carry-users extra; a user standing next to it and a furni nobody can sit on carry nobody.
 */
class WiredMoveCarrySeatedTest {

    @Test
    void theUserSittingOnTheSeatRidesAlong() {
        Fixture fixture = new Fixture(true);
        RoomUnit sitter = fixture.unit(1, 4, 8, RoomUnitStatus.SIT);
        fixture.unit(2, 5, 8, null);

        List<RoomUnit> riders = WiredMoveCarryHelper.seatedRiders(fixture.room, fixture.seat);

        assertEquals(List.of(sitter), riders);
    }

    @Test
    void aStandingUserOnTheTileIsNotCarried() {
        Fixture fixture = new Fixture(true);
        fixture.unit(1, 4, 8, null);

        assertTrue(WiredMoveCarryHelper.seatedRiders(fixture.room, fixture.seat).isEmpty());
    }

    @Test
    void aFurniWithoutSeatCarriesNobody() {
        Fixture fixture = new Fixture(false);
        fixture.unit(1, 4, 8, RoomUnitStatus.SIT);

        assertTrue(WiredMoveCarryHelper.seatedRiders(fixture.room, fixture.seat).isEmpty());
    }

    @Test
    void aBotSittingOnTheSeatRidesAlongAsAnAvatarDoes() {
        Fixture fixture = new Fixture(true);
        RoomUnit bot = fixture.unit(1, 4, 8, RoomUnitStatus.SIT, RoomUnitType.BOT);

        assertEquals(List.of(bot), WiredMoveCarryHelper.seatedRiders(fixture.room, fixture.seat));
    }

    @Test
    void aPetOnTheSeatIsNotCarried() {
        Fixture fixture = new Fixture(true);
        fixture.unit(1, 4, 8, RoomUnitStatus.SIT, RoomUnitType.PET);

        assertTrue(WiredMoveCarryHelper.seatedRiders(fixture.room, fixture.seat).isEmpty());
    }

    @Test
    void aRiderIsAnnouncedOnlyWhenItsRideStarts() {
        Fixture fixture = new Fixture(true);
        RoomUnit sitter = fixture.unit(1, 4, 8, RoomUnitStatus.SIT);

        try {
            assertEquals(List.of(sitter), WiredMoveCarryHelper.ridersToAnnounce(List.of(sitter)));

            WiredMoveCarryHelper.suppressStatusComposer(sitter, 2000);

            assertTrue(WiredMoveCarryHelper.ridersToAnnounce(List.of(sitter)).isEmpty());
        } finally {
            WiredMoveCarryHelper.clearStatusComposerSuppression(sitter);
        }
    }

    @Test
    void aRiderThatWalksOffIsNoLongerHeldBack() {
        Fixture fixture = new Fixture(true);
        RoomUnit rider = fixture.unit(1, 4, 8, null);

        try {
            WiredMoveCarryHelper.suppressStatusComposer(rider, 2000);
            assertTrue(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));

            when(rider.isWalking()).thenReturn(true);
            assertFalse(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));

            // It stays released when it stops on the seat, so the seat's ride announces the sit.
            when(rider.isWalking()).thenReturn(false);
            assertFalse(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));
            assertEquals(List.of(rider), WiredMoveCarryHelper.ridersToAnnounce(List.of(rider)));
        } finally {
            WiredMoveCarryHelper.clearStatusComposerSuppression(rider);
        }
    }

    @Test
    void aRiderThatSitsDownDuringItsRideIsNoLongerHeldBack() {
        Fixture fixture = new Fixture(true);
        // The ride starts on the last walk step: the user stands on the seat, the sit follows one status cycle later.
        RoomUnit rider = fixture.unit(1, 4, 8, null);

        try {
            WiredMoveCarryHelper.suppressStatusComposer(rider, 2000);
            assertTrue(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));

            when(rider.hasStatus(RoomUnitStatus.SIT)).thenReturn(true);
            // A renewed hold keeps the standing posture the clients were last told, so the sit still goes out.
            WiredMoveCarryHelper.suppressStatusComposer(rider, 2000);

            assertFalse(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));
            assertEquals(List.of(rider), WiredMoveCarryHelper.ridersToAnnounce(List.of(rider)));

            // The next ride tick holds the sitter again.
            WiredMoveCarryHelper.suppressStatusComposer(rider, 2000);
            assertTrue(WiredMoveCarryHelper.shouldSuppressStatusComposer(rider));
        } finally {
            WiredMoveCarryHelper.clearStatusComposerSuppression(rider);
        }
    }

    private static final class Fixture {
        private final Room room = mock(Room.class);
        private final RoomLayout layout = mock(RoomLayout.class);
        private final HabboItem seat = mock(HabboItem.class);
        private final Set<RoomUnit> units = new LinkedHashSet<>();
        private final RoomTile seatTile = new RoomTile((short) 4, (short) 8, (short) 0, RoomTileState.OPEN, true);

        private Fixture(boolean allowSit) {
            Item base = mock(Item.class);
            when(base.allowSit()).thenReturn(allowSit);
            when(base.getWidth()).thenReturn(1);
            when(base.getLength()).thenReturn(1);
            when(seat.getBaseItem()).thenReturn(base);
            when(seat.getX()).thenReturn((short) 4);
            when(seat.getY()).thenReturn((short) 8);
            when(seat.getZ()).thenReturn(1.5);
            when(room.getLayout()).thenReturn(layout);
            when(room.getRoomUnits()).thenReturn(units);
            when(room.getTopItemAt(anyInt(), anyInt())).thenReturn(seat);
            when(layout.getTile((short) 4, (short) 8)).thenReturn(seatTile);
            when(layout.getTilesAt(seatTile, 1, 1, 0)).thenReturn(Set.of(seatTile));
        }

        private RoomUnit unit(int id, int x, int y, RoomUnitStatus status) {
            return this.unit(id, x, y, status, RoomUnitType.USER);
        }

        private RoomUnit unit(int id, int x, int y, RoomUnitStatus status, RoomUnitType type) {
            RoomUnit unit = mock(RoomUnit.class);
            RoomTile tile = new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
            when(unit.getId()).thenReturn(id);
            when(unit.getRoomUnitType()).thenReturn(type);
            when(unit.getCurrentLocation()).thenReturn(tile);
            when(unit.isInRoom()).thenReturn(true);
            when(unit.getX()).thenReturn((short) x);
            when(unit.getY()).thenReturn((short) y);
            when(unit.getZ()).thenReturn(1.5);
            if (status != null) when(unit.hasStatus(status)).thenReturn(true);
            units.add(unit);
            return unit;
        }
    }
}
