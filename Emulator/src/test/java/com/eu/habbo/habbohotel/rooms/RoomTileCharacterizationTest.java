package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Characterizes RoomTile — the core pathfinding data structure (A* cost
 * accumulation, coordinate equality, distance). Pure, no DB/config needed.
 */
class RoomTileCharacterizationTest {

    private static RoomTile tile(int x, int y, RoomTileState state) {
        return new RoomTile((short) x, (short) y, (short) 0, state, true);
    }

    @Test
    void equalityIsByCoordinatesOnly() {
        RoomTile a = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile sameCoordsOtherFields = new RoomTile((short) 1, (short) 1, (short) 5, RoomTileState.BLOCKED, false);
        RoomTile otherCoords = tile(1, 2, RoomTileState.OPEN);

        assertEquals(a, sameCoordsOtherFields); // height/state ignored
        assertNotEquals(a, otherCoords);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a tile");
    }

    @Test
    void copyEqualsButIsDistinctInstance() {
        RoomTile a = tile(3, 7, RoomTileState.OPEN);
        RoomTile copy = a.copy();

        assertEquals(a, copy);
        assertNotSame(a, copy);
    }

    @Test
    void euclideanDistance() {
        assertEquals(5.0, tile(0, 0, RoomTileState.OPEN).distance(tile(3, 4, RoomTileState.OPEN)));
    }

    @Test
    void gCostAccumulatesFromPreviousTile() {
        RoomTile start = tile(0, 0, RoomTileState.OPEN); // gCosts 0
        RoomTile next = tile(1, 0, RoomTileState.OPEN);
        next.setgCosts(start, 10);
        assertEquals(10, next.getgCosts());
        assertEquals(10, next.getfCosts()); // hCosts defaults to 0

        RoomTile after = tile(2, 0, RoomTileState.OPEN);
        after.setgCosts(next, 14);
        assertEquals(24, after.getgCosts());
    }

    @Test
    void invalidTileRelativeHeightIsMaxValue() {
        RoomTile invalid = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.INVALID, false);
        assertEquals(Short.MAX_VALUE, invalid.relativeHeight());
    }
}
