package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.wired.core.WiredProjectileDirections.EAST;
import static polaris.hotel.wired.core.WiredProjectileDirections.EIGHT_DIFFUSE;
import static polaris.hotel.wired.core.WiredProjectileDirections.EIGHT_STRAIGHT;
import static polaris.hotel.wired.core.WiredProjectileDirections.FOUR_PREFER_HORIZONTAL;
import static polaris.hotel.wired.core.WiredProjectileDirections.FOUR_PREFER_VERTICAL;
import static polaris.hotel.wired.core.WiredProjectileDirections.NORTH;
import static polaris.hotel.wired.core.WiredProjectileDirections.NORTH_EAST;
import static polaris.hotel.wired.core.WiredProjectileDirections.NORTH_WEST;
import static polaris.hotel.wired.core.WiredProjectileDirections.SOUTH;
import static polaris.hotel.wired.core.WiredProjectileDirections.SOUTH_EAST;
import static polaris.hotel.wired.core.WiredProjectileDirections.SOUTH_WEST;
import static polaris.hotel.wired.core.WiredProjectileDirections.WEST;

import org.junit.jupiter.api.Test;
import polaris.util.pathfinding.Direction8;

/** The four directional systems of the projectile add-on, as turbo-cloud resolves them. */
class WiredProjectileDirectionsTest {

    @Test
    void theCompassIsTheOneTheRoomAlreadyUses() {
        // One step each way must land on the rotation Direction8 gives the same step, or a
        // projectile would face somewhere other than where the room thinks it went.
        int[][] steps = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

        for (int[] step : steps) {
            assertEquals(
                    Direction8.fromDelta(step[0], step[1]).getRot(),
                    WiredProjectileDirections.resolve(EIGHT_STRAIGHT, step[0], step[1]),
                    () -> "step " + step[0] + "," + step[1]);
        }
    }

    @Test
    void eightStraightIsOnlyAnAxisWhenExactlyOnIt() {
        assertEquals(EAST, WiredProjectileDirections.resolve(EIGHT_STRAIGHT, 5, 0));
        assertEquals(NORTH, WiredProjectileDirections.resolve(EIGHT_STRAIGHT, 0, -3));
        // Five across and one down is still off the axis, so it is a diagonal.
        assertEquals(SOUTH_EAST, WiredProjectileDirections.resolve(EIGHT_STRAIGHT, 5, 1));
        assertEquals(NORTH_WEST, WiredProjectileDirections.resolve(EIGHT_STRAIGHT, -2, -7));
    }

    @Test
    void eightDiffuseGivesEachDirectionAnEvenWedge() {
        // Within 22.5 degrees of an axis counts as along it.
        assertEquals(EAST, WiredProjectileDirections.resolve(EIGHT_DIFFUSE, 5, 1));
        assertEquals(SOUTH, WiredProjectileDirections.resolve(EIGHT_DIFFUSE, 1, 5));
        // Further off it is the diagonal.
        assertEquals(SOUTH_EAST, WiredProjectileDirections.resolve(EIGHT_DIFFUSE, 5, 3));
        assertEquals(SOUTH_WEST, WiredProjectileDirections.resolve(EIGHT_DIFFUSE, -4, 4));
    }

    @Test
    void theFourWaySystemsDifferOnlyOnAnExactDiagonal() {
        assertEquals(EAST, WiredProjectileDirections.resolve(FOUR_PREFER_VERTICAL, 5, 2));
        assertEquals(EAST, WiredProjectileDirections.resolve(FOUR_PREFER_HORIZONTAL, 5, 2));
        assertEquals(SOUTH, WiredProjectileDirections.resolve(FOUR_PREFER_VERTICAL, 2, 5));
        assertEquals(SOUTH, WiredProjectileDirections.resolve(FOUR_PREFER_HORIZONTAL, 2, 5));

        // The tie: three and three. That is the whole difference between the two.
        assertEquals(SOUTH, WiredProjectileDirections.resolve(FOUR_PREFER_VERTICAL, 3, 3));
        assertEquals(EAST, WiredProjectileDirections.resolve(FOUR_PREFER_HORIZONTAL, 3, 3));
        assertEquals(NORTH, WiredProjectileDirections.resolve(FOUR_PREFER_VERTICAL, -2, -2));
        assertEquals(WEST, WiredProjectileDirections.resolve(FOUR_PREFER_HORIZONTAL, -2, -2));
    }

    @Test
    void aMoveThatGoesNowhereHasNoDirection() {
        assertEquals(-1, WiredProjectileDirections.resolve(EIGHT_STRAIGHT, 0, 0));
        assertEquals(-1, WiredProjectileDirections.resolve(FOUR_PREFER_VERTICAL, 0, 0));
    }

    @Test
    void anUnknownSystemFallsBackToEightStraight() {
        assertEquals(SOUTH_EAST, WiredProjectileDirections.resolve(99, 5, 1));
        assertEquals(EIGHT_STRAIGHT, WiredProjectileDirections.normalizeSystem(-4));
    }

    @Test
    void theOffsetTurnsClockwiseAndWraps() {
        assertEquals(NORTH_EAST, WiredProjectileDirections.rotate(NORTH, 1));
        assertEquals(NORTH, WiredProjectileDirections.rotate(NORTH_WEST, 1));
        assertEquals(SOUTH, WiredProjectileDirections.rotate(NORTH, 4));
        assertEquals(WEST, WiredProjectileDirections.rotate(NORTH, -2));
    }
}
