package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItemProperties;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnowWarArcticIslandFixtureTest {

    @Test
    void bundlesTheGameCenterArcticIslandHeightmapAndFurniture() throws IOException {
        String heightmap = readResource("/snowwar/arena_1_heightmap.txt");
        String items = readResource("/snowwar/arena_1.dat");

        assertEquals(50, heightmap.lines().count());
        assertEquals(50, heightmap.lines().findFirst().orElseThrow().length());
        assertEquals(101, items.lines().count());
        assertTrue(items.contains("snst_tree1 19 41 0"));
        assertTrue(items.contains("snst_fence 17 14 2"));
        assertTrue(items.contains("ads_igorraygun 28 12 4"));
        assertTrue(SnowWarItemProperties.isKnownItem("snst_block1"));
        assertTrue(SnowWarItemProperties.isKnownItem("snst_tree1"));
        assertTrue(SnowWarItemProperties.isKnownItem("snst_fence"));
    }

    @Test
    void bundlesEveryOriginalSelectableArena() throws IOException {
        assertEquals(List.of(8, 9, 11), SnowWarManager.ARENA_IDS);

        for (Integer arenaId : SnowWarManager.ARENA_IDS) {
            String heightmap = readResource("/snowwar/arena_" + arenaId + "_heightmap.txt");
            String items = readResource("/snowwar/arena_" + arenaId + ".dat");
            assertEquals(50, heightmap.lines().count());
            assertTrue(items.lines().count() > 30);
        }
    }

    @Test
    void blocksTheEntireArcticIslandPondButKeepsFourPlankCrossingsWalkable() throws IOException {
        List<String> rows =
                readResource("/snowwar/arena_8_heightmap.txt").lines().toList();

        for (int y = 16; y <= 27; y++) {
            for (int x = 19; x <= 30; x++) {
                boolean northPlank = x >= 22 && x <= 23 && y >= 16 && y <= 18;
                boolean westPlank = x >= 19 && x <= 21 && y >= 22 && y <= 23;
                boolean eastPlank = x >= 28 && x <= 30 && y >= 20 && y <= 21;
                boolean southPlank = x >= 25 && x <= 26 && y >= 25 && y <= 27;
                char expected = northPlank || westPlank || eastPlank || southPlank ? '0' : 'x';
                assertEquals(expected, rows.get(y).charAt(x), "tile " + x + "," + y);
            }
        }

        SnowWarMap map = new SnowWarMap(8, rows, List.of(), List.of(), List.of());
        assertFalse(map.getTile(19, 16).isWalkable());
        assertFalse(map.getTile(24, 21).isWalkable());
        assertFalse(map.getTile(24, 25).isWalkable());
        assertTrue(map.getTile(22, 16).isWalkable());
        assertTrue(map.getTile(19, 22).isWalkable());
        assertTrue(map.getTile(30, 20).isWalkable());
        assertTrue(map.getTile(25, 25).isWalkable());
    }

    private static String readResource(String name) throws IOException {
        try (var stream = SnowWarArcticIslandFixtureTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
