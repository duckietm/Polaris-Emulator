package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.EmulatorTestSupport;
import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Characterizes RoomLayout's movement/direction math (getTileInFront) — the
 * rotation->neighbour mapping used by walking, door logic and furniture
 * placement. Built on the fromHeightmap + config seams. Rotations:
 * 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW.
 */
class RoomLayoutDirectionCharacterizationTest {

    private ConfigurationManager previous;

    @BeforeEach
    void setup() throws Exception {
        previous = Emulator.getConfig();
        EmulatorTestSupport.installConfig(Map.of());
    }

    @AfterEach
    void teardown() throws Exception {
        EmulatorTestSupport.setConfig(previous);
    }

    private RoomLayout grid3x3() {
        return RoomLayout.fromHeightmap("dirs", "000\r000\r000", 0, 0, 2, null);
    }

    @Test
    void mapsAllEightRotationsFromCentre() {
        RoomLayout layout = grid3x3();
        RoomTile c = layout.getTile((short) 1, (short) 1);

        assertSame(layout.getTile((short) 1, (short) 0), layout.getTileInFront(c, 0)); // N
        assertSame(layout.getTile((short) 2, (short) 0), layout.getTileInFront(c, 1)); // NE
        assertSame(layout.getTile((short) 2, (short) 1), layout.getTileInFront(c, 2)); // E
        assertSame(layout.getTile((short) 2, (short) 2), layout.getTileInFront(c, 3)); // SE
        assertSame(layout.getTile((short) 1, (short) 2), layout.getTileInFront(c, 4)); // S
        assertSame(layout.getTile((short) 0, (short) 2), layout.getTileInFront(c, 5)); // SW
        assertSame(layout.getTile((short) 0, (short) 1), layout.getTileInFront(c, 6)); // W
        assertSame(layout.getTile((short) 0, (short) 0), layout.getTileInFront(c, 7)); // NW
    }

    @Test
    void rotationWrapsModuloEight() {
        RoomLayout layout = grid3x3();
        RoomTile c = layout.getTile((short) 1, (short) 1);
        assertSame(layout.getTileInFront(c, 0), layout.getTileInFront(c, 8)); // 8 % 8 == 0
    }

    @Test
    void offsetMovesExtraStepsAndOutOfBoundsReturnsNull() {
        RoomLayout layout = grid3x3();
        RoomTile corner = layout.getTile((short) 0, (short) 0);

        // east, offset 1 -> 2 steps -> (2,0)
        assertSame(layout.getTile((short) 2, (short) 0), layout.getTileInFront(corner, 2, 1));
        // east, offset 2 -> 3 steps -> (3,0), out of a 3-wide grid -> null
        assertNull(layout.getTileInFront(corner, 2, 2));
    }
}
