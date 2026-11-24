package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

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

    private static List<RoomUnit> lifted(Fixture fixture) {
        return WiredMoveCarryHelper.liftedUnits(fixture.room, fixture.seat).stream()
                .map(WiredMoveCarryHelper.LiftedUnit::unit)
                .toList();
    }

    @Test
    void aStandingUserOnTheFurniIsLiftedWithIt() {
        Fixture fixture = new Fixture(false);
        RoomUnit standing = fixture.unit(1, 4, 8, null);
        fixture.unit(2, 5, 8, null);

        assertEquals(List.of(standing), lifted(fixture));
    }

    @Test
    void aSitterAndAPetNobodyRidesAreNotLifted() {
        Fixture fixture = new Fixture(true);
        fixture.unit(1, 4, 8, RoomUnitStatus.SIT);
        fixture.unit(3, 4, 8, null, RoomUnitType.PET);

        assertTrue(lifted(fixture).isEmpty());
    }

    @Test
    void aUserWhoAskedToWalkAwayButHasNotSteppedIsStillLifted() {
        Fixture fixture = new Fixture(false);
        RoomUnit leaving = fixture.unit(1, 4, 8, null);
        when(leaving.isWalking()).thenReturn(true);

        List<WiredMoveCarryHelper.LiftedUnit> lifted = WiredMoveCarryHelper.liftedUnits(fixture.room, fixture.seat);

        assertEquals(List.of(leaving), lifted(fixture));
        assertFalse(lifted.get(0).stepping());
    }

    @Test
    void aWalkerWhoseStepEndsOnTheFurniIsLiftedAsStepping() {
        Fixture fixture = new Fixture(false);
        // The current location of a walker is the tile his announced step ends on.
        RoomUnit walker = fixture.unit(1, 4, 8, RoomUnitStatus.MOVE);
        when(walker.isWalking()).thenReturn(true);

        List<WiredMoveCarryHelper.LiftedUnit> lifted = WiredMoveCarryHelper.liftedUnits(fixture.room, fixture.seat);

        assertEquals(1, lifted.size());
        assertTrue(lifted.get(0).stepping());
    }

    @Test
    void aRiderIsListedBeforeHisHorse() {
        Fixture fixture = new Fixture(false);
        RoomUnit horse = fixture.unit(7, 4, 8, null, RoomUnitType.PET);
        RoomUnit rider = fixture.unit(1, 4, 8, null);
        fixture.rides(rider, horse);

        List<WiredMoveCarryHelper.LiftedUnit> lifted = WiredMoveCarryHelper.liftedUnits(fixture.room, fixture.seat);

        assertEquals(List.of(rider, horse), lifted(fixture));
        assertTrue(lifted.get(0).riding());
        // The horse ends on its rider's tile.
        assertEquals(rider, lifted.get(1).anchor());
        assertEquals(rider, lifted.get(0).anchor());
    }

    @Test
    void aUserOnARugStackedOnTheFurniKeepsHisHeightSource() {
        Fixture fixture = new Fixture(false);
        RoomUnit onRug = fixture.unit(1, 4, 8, null);
        when(fixture.room.getTopItemAt(anyInt(), anyInt())).thenReturn(mock(UserItem.class));
        // The furni's top is at 1.5; the rug on it puts the user higher.
        when(onRug.getZ()).thenReturn(1.9);

        assertTrue(lifted(fixture).isEmpty());
    }

    @Test
    void aTurnThatKeepsTheFurnisTilesStaysInPlace() {
        RoomTile a = new RoomTile((short) 4, (short) 8, (short) 0, RoomTileState.OPEN, true);
        RoomTile b = new RoomTile((short) 5, (short) 8, (short) 0, RoomTileState.OPEN, true);
        RoomTile c = new RoomTile((short) 4, (short) 9, (short) 0, RoomTileState.OPEN, true);

        assertTrue(WiredMoveCarryHelper.sameTiles(Set.of(a), Set.of(a)));
        assertTrue(WiredMoveCarryHelper.sameTiles(List.of(a, b), List.of(b, a)));
        // A two-tile furni that turns a quarter reaches a new tile: users there still stop it.
        assertFalse(WiredMoveCarryHelper.sameTiles(List.of(a, b), List.of(a, c)));
        assertFalse(WiredMoveCarryHelper.sameTiles(List.of(a), List.of(a, b)));
        assertFalse(WiredMoveCarryHelper.sameTiles(null, List.of(a)));

        Fixture fixture = new Fixture(false);
        // Same rotation (an altitude write) and a square furni's turn both keep the tiles.
        assertTrue(WiredMoveCarryHelper.keepsFootprint(fixture.room, fixture.seat, fixture.seatTile, 0));
        when(fixture.layout.getTilesAt(fixture.seatTile, 1, 1, 2)).thenReturn(Set.of(fixture.seatTile));
        assertTrue(WiredMoveCarryHelper.keepsFootprint(fixture.room, fixture.seat, fixture.seatTile, 2));
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
        private final UserItem seat = mock(UserItem.class);
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

        private void rides(RoomUnit rider, RoomUnit horse) {
            User user = mock(User.class);
            UserInfo info = mock(UserInfo.class);
            RideablePet pet = mock(RideablePet.class);
            when(pet.getRoomUnit()).thenReturn(horse);
            when(info.getRiding()).thenReturn(pet);
            when(user.getUserInfo()).thenReturn(info);
            when(room.getUser(rider)).thenReturn(user);
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
