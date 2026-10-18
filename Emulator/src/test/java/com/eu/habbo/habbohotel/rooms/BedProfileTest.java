package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedProfileTest {

    private static HabboItem doubleBed(int rotation) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(2);
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
            boolean onBed = bed.getRotation() == 0 || bed.getRotation() == 4
                    ? (x == 4 || x == 5) && y >= 6 && y <= 8
                    : (y == 6 || y == 7) && x >= 4 && x <= 6;
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
}
