package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for RoomLayout's pure static geometry helpers. These
 * are used across furniture placement, rights areas and adjacency checks but had
 * no coverage. They touch neither the database nor Emulator config, so they are
 * safe to pin down now and guard later refactors of the room/furni logic.
 */
class RoomLayoutGeometryCharacterizationTest {

    @Test
    void getRectangleSwapsWidthAndLengthForRotation2And6() {
        assertEquals(new Rectangle(1, 2, 3, 4), RoomLayout.getRectangle(1, 2, 3, 4, 0));
        assertEquals(new Rectangle(1, 2, 4, 3), RoomLayout.getRectangle(1, 2, 3, 4, 2));
        assertEquals(new Rectangle(1, 2, 4, 3), RoomLayout.getRectangle(1, 2, 3, 4, 6));
        assertEquals(new Rectangle(1, 2, 3, 4), RoomLayout.getRectangle(1, 2, 3, 4, 8)); // 8 % 8 == 0
    }

    @Test
    void squareInSquareRequiresFullContainment() {
        Rectangle outer = new Rectangle(0, 0, 10, 10);
        assertTrue(RoomLayout.squareInSquare(outer, new Rectangle(2, 2, 3, 3)));
        assertFalse(RoomLayout.squareInSquare(outer, new Rectangle(-1, 2, 3, 3))); // starts left of outer
        assertFalse(RoomLayout.squareInSquare(outer, new Rectangle(8, 8, 5, 5)));  // overflows right/bottom
    }

    @Test
    void pointInSquareIsInclusiveOfBounds() {
        assertTrue(RoomLayout.pointInSquare(0, 0, 5, 5, 0, 0));
        assertTrue(RoomLayout.pointInSquare(0, 0, 5, 5, 5, 5));
        assertFalse(RoomLayout.pointInSquare(0, 0, 5, 5, 6, 5));
    }

    @Test
    void tilesAdjacentWithinOneTileInclusiveOfDiagonals() {
        RoomTile a = new RoomTile((short) 5, (short) 5, (short) 0, RoomTileState.OPEN, true);
        RoomTile diagonal = new RoomTile((short) 6, (short) 6, (short) 0, RoomTileState.OPEN, true);
        RoomTile twoAway = new RoomTile((short) 7, (short) 5, (short) 0, RoomTileState.OPEN, true);

        assertTrue(RoomLayout.tilesAdjecent(a, diagonal));
        assertFalse(RoomLayout.tilesAdjecent(a, twoAway));
        assertFalse(RoomLayout.tilesAdjecent(a, null));
    }
}
