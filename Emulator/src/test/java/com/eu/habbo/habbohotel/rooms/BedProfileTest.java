package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedProfileTest {

    private static HabboItem doubleBed(int rotation) {
        return bed(rotation, 2);
    }

    private static HabboItem bed(int rotation, int width) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(width);
        when(base.getLength()).thenReturn(3);
        when(base.getHeight()).thenReturn(1.0);

        HabboItem bed = mock(HabboItem.class);
        when(bed.getId()).thenReturn(77);
        when(bed.getX()).thenReturn((short) 4);
        when(bed.getY()).thenReturn((short) 6);
        when(bed.getRotation()).thenReturn(rotation);
        when(bed.getBaseItem()).thenReturn(base);
        return bed;
    }

    private static Room roomWith(HabboItem bed) {
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort()))
                .thenAnswer(call ->
                        new RoomTile(call.getArgument(0), call.getArgument(1), (short) 0, RoomTileState.OPEN, true));

        Room room = mock(Room.class);
        when(room.getLayout()).thenReturn(layout);
        when(room.getTopItemAt(anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            int y = call.getArgument(1);
            int lanes = bed.getBaseItem().getWidth();
            boolean onBed = bed.getRotation() == 0 || bed.getRotation() == 4
                    ? x >= 4 && x < 4 + lanes && y >= 6 && y <= 8
                    : y >= 6 && y < 6 + lanes && x >= 4 && x <= 6;
            return onBed ? bed : null;
        });
        when(room.canLayAt(anyInt(), anyInt())).thenReturn(true);
        when(room.getRoomUnitsAt(any(RoomTile.class))).thenReturn(List.of());
        return room;
    }

    @Test
    void thePillowIsInTheClickedLaneForBothRotations() {
        HabboItem bed = doubleBed(0);
        Room room = roomWith(bed);
        BedProfile profile = new BedProfile(bed);
        RoomTile far = profile.getPillow(room, (short) 5, (short) 8, bed);
        assertEquals(5, far.x);
        assertEquals(6, far.y);

        HabboItem turned = doubleBed(2);
        Room turnedRoom = roomWith(turned);
        RoomTile side = new BedProfile(turned).getPillow(turnedRoom, (short) 6, (short) 7, turned);
        assertEquals(4, side.x);
        assertEquals(7, side.y);
    }

    @Test
    void theOtherSideWorksFromEitherLane() {
        HabboItem bed = doubleBed(0);
        Room room = roomWith(bed);
        BedProfile profile = new BedProfile(bed);

        RoomTile fromFirst = profile.getOtherSide(
                room, bed, new RoomTile((short) 4, (short) 6, (short) 0, RoomTileState.OPEN, true));
        RoomTile fromSecond = profile.getOtherSide(
                room, bed, new RoomTile((short) 5, (short) 6, (short) 0, RoomTileState.OPEN, true));

        assertEquals(5, fromFirst.x);
        assertEquals(4, fromSecond.x);

        HabboItem turned = doubleBed(2);
        Room turnedRoom = roomWith(turned);
        RoomTile turnedOther = new BedProfile(turned)
                .getOtherSide(
                        turnedRoom, turned, new RoomTile((short) 4, (short) 7, (short) 0, RoomTileState.OPEN, true));
        assertEquals(6, turnedOther.y);
    }

    @Test
    void aSingleBedHasNoOtherSide() {
        HabboItem bed = doubleBed(0);
        when(bed.getBaseItem().getWidth()).thenReturn(1);
        assertNull(new BedProfile(bed)
                .getOtherSide(
                        roomWith(bed), bed, new RoomTile((short) 4, (short) 6, (short) 0, RoomTileState.OPEN, true)));
    }

    @Test
    void aPillowSomeoneElseLiesOnIsTakenButTheWalkersOwnIsNot() {
        HabboItem bed = doubleBed(0);
        Room room = roomWith(bed);
        RoomTile pillow = new RoomTile((short) 4, (short) 6, (short) 0, RoomTileState.OPEN, true);
        RoomUnit walker = mock(RoomUnit.class);
        RoomUnit sleeper = mock(RoomUnit.class);

        assertFalse(BedProfile.isPillowTaken(room, pillow, walker));

        when(room.getRoomUnitsAt(pillow)).thenReturn(List.of(walker));
        assertFalse(BedProfile.isPillowTaken(room, pillow, walker));

        when(room.getRoomUnitsAt(pillow)).thenReturn(List.of(sleeper));
        assertTrue(BedProfile.isPillowTaken(room, pillow, walker));

        when(room.getRoomUnitsAt(pillow)).thenReturn(List.of());
        when(room.canLayAt(4, 6)).thenReturn(false);
        assertTrue(BedProfile.isPillowTaken(room, pillow, walker));
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }

    @Test
    void theLayStatusIsTheBedHeightWithNoScreenOffsets() {
        HabboItem bed = doubleBed(0);
        when(bed.getBaseItem().getHeight()).thenReturn(1.73);
        BedProfile profile = new BedProfile(bed);

        assertEquals("1.73", BedProfile.layStatus(bed));
        assertEquals(0, profile.getLayXOffset());
        assertEquals(0, profile.getLayYOffset());
        assertEquals(0, profile.getLayZOffset());
    }

    @Test
    void thePillowsAreTheLeadingRowOneLanePerTileOfWidth() {
        HabboItem along = bed(0, 3);
        List<RoomTile> pillows = new BedProfile(along).getPillows(roomWith(along), along);
        assertEquals(List.of(tile(4, 6), tile(5, 6), tile(6, 6)), pillows);

        HabboItem across = bed(2, 2);
        assertEquals(List.of(tile(4, 6), tile(4, 7)), new BedProfile(across).getPillows(roomWith(across), across));

        HabboItem turnedBack = bed(4, 2);
        assertEquals(
                List.of(tile(4, 6), tile(5, 6)),
                new BedProfile(turnedBack).getPillows(roomWith(turnedBack), turnedBack));
    }

    @Test
    void aTakenPillowSendsYouToTheNearestFreeLane() {
        HabboItem bed = bed(0, 3);
        Room room = roomWith(bed);
        BedProfile profile = new BedProfile(bed);
        RoomUnit walker = mock(RoomUnit.class);
        RoomUnit sleeper = mock(RoomUnit.class);

        assertEquals(tile(5, 6), profile.findFreePillow(room, bed, tile(5, 6), walker));

        when(room.getRoomUnitsAt(tile(5, 6))).thenReturn(List.of(sleeper));
        assertEquals(tile(4, 6), profile.findFreePillow(room, bed, tile(5, 6), walker));

        when(room.getRoomUnitsAt(tile(4, 6))).thenReturn(List.of(sleeper));
        assertEquals(tile(6, 6), profile.findFreePillow(room, bed, tile(5, 6), walker));

        when(room.getRoomUnitsAt(tile(6, 6))).thenReturn(List.of(sleeper));
        assertNull(profile.findFreePillow(room, bed, tile(5, 6), walker));
    }

    @Test
    void layingPutsYouOnYourLanesPillowAtTheBedsZFacingAlongIt() {
        HabboItem bed = doubleBed(2);
        when(bed.getZ()).thenReturn(1.5);
        when(bed.getBaseItem().getHeight()).thenReturn(1.9);
        Room room = roomWith(bed);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) 6);
        when(unit.getY()).thenReturn((short) 7);

        BedProfile.lay(room, bed, unit);

        verify(unit).setLocation(tile(4, 7));
        verify(unit).setZ(1.5);
        verify(unit).setRotation(RoomUserRotation.EAST);
        verify(unit).setStatus(RoomUnitStatus.LAY, "1.9");
    }

    @Test
    void layingWhenSomeoneHasYourPillowTakesTheOtherLane() {
        HabboItem bed = doubleBed(0);
        Room room = roomWith(bed);
        RoomUnit sleeper = mock(RoomUnit.class);
        when(room.getRoomUnitsAt(tile(4, 6))).thenReturn(List.of(sleeper));
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) 4);
        when(unit.getY()).thenReturn((short) 8);

        BedProfile.lay(room, bed, unit);

        verify(unit).setLocation(tile(5, 6));
        verify(unit).setRotation(RoomUserRotation.NORTH);
    }

    @Test
    void aBedTurnedToFourOrSixStillFacesAlongItsAxis() {
        HabboItem bed = doubleBed(6);
        RoomUnit unit = mock(RoomUnit.class);
        BedProfile.layInPlace(bed, unit);
        verify(unit).setRotation(RoomUserRotation.EAST);
    }
}
