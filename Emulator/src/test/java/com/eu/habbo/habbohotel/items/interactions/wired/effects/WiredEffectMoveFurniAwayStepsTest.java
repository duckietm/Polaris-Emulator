package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AIR's flee: straight away from a user in line, else east, then one y step away, then the other. */
class WiredEffectMoveFurniAwayStepsTest {

    @Test
    void aUserInLineIsFledStraightAwayThenEastThenAlongY() {
        List<int[]> steps = WiredEffectMoveFurniAway.fleeSteps(5, 5, new int[] {2, 5}, 2, 5);

        assertEquals(4, steps.size());
        assertArrayEquals(new int[] {1, 0}, steps.get(0));
        assertArrayEquals(new int[] {1, 0}, steps.get(1));
        assertArrayEquals(new int[] {0, 1}, steps.get(2));
        assertArrayEquals(new int[] {0, -1}, steps.get(3));
    }

    @Test
    void withNoUserInLineTheFurniGoesEastEvenTowardsTheUser() {
        List<int[]> steps = WiredEffectMoveFurniAway.fleeSteps(5, 5, null, 8, 3);

        assertEquals(3, steps.size());
        assertArrayEquals(new int[] {1, 0}, steps.get(0));
        // The furni is south of the user (higher y), so it keeps going south first.
        assertArrayEquals(new int[] {0, 1}, steps.get(1));
        assertArrayEquals(new int[] {0, -1}, steps.get(2));

        List<int[]> north = WiredEffectMoveFurniAway.fleeSteps(5, 5, null, 8, 7);
        assertArrayEquals(new int[] {0, -1}, north.get(1));
    }

    @Test
    void onlyAvatarsAreFledFromAndInLineMeansARowOrColumnWithinThree() {
        Room room = mock(Room.class);
        RoomUnit bot = unit(5, 6, RoomUnitType.BOT);
        RoomUnit user = unit(1, 5, RoomUnitType.USER);
        RoomUnit far = unit(9, 9, RoomUnitType.USER);
        when(room.getRoomUnits()).thenReturn(new LinkedHashSet<>(List.of(bot, user, far)));

        List<RoomUnit> users = WiredEffectMoveFurniAway.fleeCandidates(room);

        assertEquals(List.of(user, far), users);
        assertSame(user, WiredEffectMoveFurniAway.nearest(users, 5, 5, false));
        // Four tiles away in the row is too far to be "in line".
        assertNull(WiredEffectMoveFurniAway.nearest(users, 5, 5, true));
        assertSame(user, WiredEffectMoveFurniAway.nearest(users, 4, 5, true));
    }

    @Test
    void aTieAtTheSameDistanceIsBrokenAtRandom() {
        RoomUnit west = unit(3, 5, RoomUnitType.USER);
        RoomUnit east = unit(7, 5, RoomUnitType.USER);
        Set<RoomUnit> picked = new HashSet<>();

        for (int i = 0; i < 64; i++) {
            picked.add(WiredEffectMoveFurniAway.nearest(List.of(west, east), 5, 5, false));
        }

        assertEquals(Set.of(west, east), picked);
    }

    private static RoomUnit unit(int x, int y, RoomUnitType type) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) x);
        when(unit.getY()).thenReturn((short) y);
        when(unit.getRoomUnitType()).thenReturn(type);
        when(unit.getCurrentLocation()).thenReturn(mock(com.eu.habbo.habbohotel.rooms.RoomTile.class));
        return unit;
    }
}
