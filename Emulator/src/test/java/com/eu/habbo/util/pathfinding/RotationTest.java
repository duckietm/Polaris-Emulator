package com.eu.habbo.util.pathfinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationTest {

    // (X1,Y1) is the origin, (X2,Y2) the target. The emulator uses Habbo's
    // 8-direction rotation where 0 = north and values increase clockwise.

    @Test
    void samePointReturnsNorth() {
        assertEquals(0, Rotation.Calculate(5, 5, 5, 5));
    }

    @Test
    void cardinalDirections() {
        assertEquals(0, Rotation.Calculate(5, 5, 5, 4)); // target north (Y2 < Y1)
        assertEquals(2, Rotation.Calculate(5, 5, 6, 5)); // target east  (X2 > X1)
        assertEquals(4, Rotation.Calculate(5, 5, 5, 6)); // target south (Y2 > Y1)
        assertEquals(6, Rotation.Calculate(5, 5, 4, 5)); // target west  (X2 < X1)
    }

    @Test
    void diagonalDirections() {
        assertEquals(1, Rotation.Calculate(5, 5, 6, 4)); // north-east
        assertEquals(3, Rotation.Calculate(5, 5, 6, 6)); // south-east
        assertEquals(5, Rotation.Calculate(5, 5, 4, 6)); // south-west
        assertEquals(7, Rotation.Calculate(5, 5, 4, 4)); // north-west
    }
}
