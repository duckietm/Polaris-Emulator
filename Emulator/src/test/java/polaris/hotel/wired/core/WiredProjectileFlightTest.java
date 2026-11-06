package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredProjectileFlightTest {

    /** Four tiles east from (2,5) at altitude 1.00 down to 0.00, over 400 ms from t=1000. */
    private static WiredProjectileFlight eastward(int mask) {
        return WiredProjectileFlight.launch(
                2,
                5,
                100,
                6,
                5,
                0,
                1_000,
                400,
                mask,
                (x, y) -> (x == 3) ? 2 : (x == 2 ? 9 : 0),
                (x, y) -> (x == 5) ? 1 : 0);
    }

    @Test
    void thePathStepsTheLongerAxisOneTileAtATime() {
        int[][] path = WiredProjectileFlight.buildPath(0, 0, 4, 2);

        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, path[0]);
        assertArrayEquals(new int[] {0, 1, 1, 2, 2}, path[1]);
        assertEquals(1, WiredProjectileFlight.buildPath(3, 3, 3, 3)[0].length);
    }

    @Test
    void theAnimationIsFollowedFromLaunchToLanding() {
        WiredProjectileFlight flight = eastward(WiredProjectileFlight.ALL_VARIABLES);

        assertTrue(flight.isTraveling(1_000));
        assertEquals(2, flight.x(1_000));
        assertEquals(100, flight.altitude(1_000));
        assertEquals(0, flight.tilesTraveled(1_000));

        assertEquals(4, flight.x(1_200));
        assertEquals(5, flight.y(1_200));
        assertEquals(50, flight.altitude(1_200));
        assertEquals(2, flight.tilesTraveled(1_200));

        assertFalse(flight.isTraveling(1_400));
        assertEquals(6, flight.x(9_999));
        assertEquals(4, flight.tilesTraveled(9_999));
        assertEquals(2, flight.x(0));
    }

    @Test
    void collisionsCountWhatStoodOnTheTilesReachedButNotTheStartTile() {
        WiredProjectileFlight flight = eastward(WiredProjectileFlight.ALL_VARIABLES);

        assertEquals(0, flight.userCollisions(1_050));
        assertEquals(2, flight.userCollisions(1_100));
        assertEquals(0, flight.furniCollisions(1_200));
        assertEquals(1, flight.furniCollisions(1_300));
        assertEquals(2, flight.userCollisions(5_000));
    }

    @Test
    void onlyTheVariablesTheMaskEnablesAreHeldAndTheFlagOnlyMidFlight() {
        int mask = (1 << WiredProjectileFlight.KEYS.indexOf(WiredProjectileFlight.POSITION_X))
                | (1 << WiredProjectileFlight.KEYS.indexOf(WiredProjectileFlight.IS_TRAVELING));
        WiredProjectileFlight flight = eastward(mask);

        assertEquals(4, flight.read(WiredProjectileFlight.POSITION_X, 1_200));
        assertNull(flight.read(WiredProjectileFlight.POSITION_Y, 1_200));
        assertFalse(flight.holds(WiredProjectileFlight.USER_COLLISIONS, 1_200));

        assertTrue(flight.holds(WiredProjectileFlight.IS_TRAVELING, 1_200));
        assertNull(flight.read(WiredProjectileFlight.IS_TRAVELING, 1_200));
        assertFalse(flight.holds(WiredProjectileFlight.IS_TRAVELING, 1_400));
        assertFalse(flight.holds("@position_x", 1_200));
    }

    @Test
    void aFlightWithoutTimeHasLandedAndAHugeOneKeepsItsPathBounded() {
        WiredProjectileFlight instant = WiredProjectileFlight.launch(0, 0, 0, 3, 0, 0, 10, 0, 127, null, null);
        assertFalse(instant.isTraveling(10));
        assertEquals(3, instant.x(10));

        WiredProjectileFlight far = WiredProjectileFlight.launch(0, 0, 0, 5_000, 0, 0, 0, 100, 127, null, null);
        assertEquals(WiredProjectileFlight.MAX_PATH_TILES, far.pathLength());
    }
}
