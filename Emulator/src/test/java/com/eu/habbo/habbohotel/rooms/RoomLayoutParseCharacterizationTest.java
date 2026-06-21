package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.EmulatorTestSupport;
import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes RoomLayout's heightmap parser — central room logic that had no
 * coverage. Uses the new {@link RoomLayout#fromHeightmap} seam plus the config
 * test seam (PathfinderImpl, constructed by the layout, reads config in a static
 * initializer). Pins the encoding: rows split on '\r', 'x' = INVALID tile,
 * digits = height, letters = 10 + letter index, and ragged rows become INVALID
 * tiles (never nulls).
 */
class RoomLayoutParseCharacterizationTest {

    private ConfigurationManager previous;

    @BeforeEach
    void setup() throws Exception {
        previous = Emulator.getConfig();
        EmulatorTestSupport.installConfig(Map.of()); // satisfies PathfinderImpl static init
    }

    @AfterEach
    void teardown() throws Exception {
        EmulatorTestSupport.setConfig(previous);
    }

    @Test
    void parsesDimensionsStatesAndHeights() {
        // 3x3: row0 "x10", row1 "234", row2 "a0x"
        RoomLayout layout = RoomLayout.fromHeightmap("test", "x10\r234\ra0x", 1, 1, 2, null);

        assertEquals(3, layout.getMapSizeX());
        assertEquals(3, layout.getMapSizeY());

        assertEquals(RoomTileState.INVALID, layout.getTile((short) 0, (short) 0).getState()); // 'x'
        assertEquals(1.0, layout.getTile((short) 1, (short) 0).getStackHeight());             // '1'
        assertEquals(4.0, layout.getTile((short) 2, (short) 1).getStackHeight());             // '4'
        assertEquals(10.0, layout.getTile((short) 0, (short) 2).getStackHeight());            // 'a' -> 10
        assertEquals(RoomTileState.INVALID, layout.getTile((short) 2, (short) 2).getState()); // 'x'
    }

    @Test
    void resolvesDoorTileAndBounds() {
        RoomLayout layout = RoomLayout.fromHeightmap("test", "x10\r234\ra0x", 1, 1, 2, null);

        assertNotNull(layout.getDoorTile());
        assertTrue(layout.tileExists((short) 0, (short) 0));
        assertFalse(layout.tileExists((short) 9, (short) 9));
        assertFalse(layout.tileExists((short) -1, (short) 0));
    }

    @Test
    void raggedRowBecomesInvalidTilesNeverNull() {
        // middle row shorter than the model width -> whole row filled INVALID
        RoomLayout layout = RoomLayout.fromHeightmap("ragged", "000\r0\r000", 0, 0, 2, null);

        assertEquals(3, layout.getMapSizeX());
        assertEquals(3, layout.getMapSizeY());
        for (short x = 0; x < 3; x++) {
            assertNotNull(layout.getTile(x, (short) 1));
            assertEquals(RoomTileState.INVALID, layout.getTile(x, (short) 1).getState());
        }
    }
}
