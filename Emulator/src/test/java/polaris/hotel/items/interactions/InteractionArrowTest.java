package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;

/** The public-room arrow: it only fires where a walk ends, and puts the user on top of its partner. */
class InteractionArrowTest {

    private static InteractionArrow arrow(int x, int y, double z, int rotation) {
        Item base = mock(Item.class);
        when(base.getHeight()).thenReturn(0.1);
        when(base.getMultiHeights()).thenReturn(new double[0]);
        when(base.allowWalk()).thenReturn(true);
        InteractionArrow arrow = new InteractionArrow(1, 1, base, "0", 0, 0);
        arrow.setX((short) x);
        arrow.setY((short) y);
        arrow.setZ(z);
        arrow.setRotation(rotation);
        return arrow;
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }

    @Test
    void itFiresOnlyWhereTheWalkEnds() {
        InteractionArrow arrow = arrow(3, 4, 0, 0);
        RoomUnit unit = new RoomUnit();

        unit.setLocation(tile(3, 4));
        assertTrue(arrow.endsWalkHere(unit), "the walk's goal is the arrow");

        unit.setLocation(tile(9, 9));
        assertFalse(arrow.endsWalkHere(unit), "walking over it on the way to 9,9");

        unit.setLocation(tile(3, 4));
        unit.isTeleporting = true;
        assertFalse(arrow.endsWalkHere(unit), "a teleport already runs for the user");
    }

    // Entering a room on the partner arrow gives a walk-on with no step in it: taking the arrow then sent the user
    // back and forth between the two rooms.
    @Test
    void onlyAStepOfAWalkTakesTheArrow() {
        assertTrue(InteractionArrow.isWalkStep(new Object[] {tile(3, 5), tile(3, 4)}));
        assertFalse(InteractionArrow.isWalkStep(new Object[] {}), "placed there when entering the room");
        assertFalse(InteractionArrow.isWalkStep(null), "a height update of the tile");
    }

    @Test
    void theUserLandsOnTopOfThePartnerFacingWhereItPoints() {
        InteractionArrow partner = arrow(7, 2, 1.0, 4);
        RoomTile target = tile(7, 2);
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 7, (short) 2)).thenReturn(target);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getPath()).thenReturn(new ArrayDeque<>());
        when(unit.getZ()).thenReturn(1.1);

        InteractionArrow.placeOn(room, unit, partner);

        verify(unit).setLocation(target);
        verify(unit).setZ(1.1);
        verify(unit).setRotation(RoomUserRotation.values()[0]);
        verify(unit).statusUpdate(true);
    }

    // A furniture teleport into another room arrives marked as teleporting until its door walks the user out; an
    // arrow has no door, so the user could not walk in the new room.
    @Test
    void aUserEnteringARoomOnAnArrowMayWalkAtOnce() {
        RoomUnit unit = new RoomUnit();
        unit.isTeleporting = true;
        unit.setCanLeaveRoomByDoor(false);

        InteractionArrow.arrive(unit, arrow(2, 2, 0, 0));

        assertFalse(unit.isTeleporting);
        assertTrue(unit.canLeaveRoomByDoor());
        assertEquals(0.1, unit.getZ(), 1e-9);
        assertEquals(RoomUserRotation.values()[4], unit.getBodyRotation(), "an arrow at rotation 0 points to 4");
    }

    @Test
    void theUserFacesHalfATurnFromTheArrowsRotation() {
        assertEquals(RoomUserRotation.values()[6], InteractionArrow.rotationOf(arrow(0, 0, 0, 2)));
        assertEquals(RoomUserRotation.values()[0], InteractionArrow.rotationOf(arrow(0, 0, 0, 4)));
        assertEquals(RoomUserRotation.values()[2], InteractionArrow.rotationOf(arrow(0, 0, 0, 6)));
    }

    // The teleblock graphic plays its sparkle in state 2; both arrows show it while a user goes through.
    @Test
    void anArrowSparklesWhileAUserGoesThrough() {
        InteractionArrow arrow = arrow(1, 1, 0, 0);
        Room room = mock(Room.class);

        InteractionArrow.startSparkle(room, arrow);

        assertEquals("2", arrow.getExtradata());
        verify(room).updateItemState(arrow);
    }

    @Test
    void itsStandingHeightIsItsOwnTopNotTheTileStackHeight() {
        assertEquals(0.6, InteractionArrow.standingHeight(arrow(0, 0, 0.5, 0)), 1e-9);
    }
}
