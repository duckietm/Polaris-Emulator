package polaris.hotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.games.snowwar.mapping.SnowWarItemProperties;
import polaris.hotel.games.snowwar.mapping.SnowWarMap;

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

    /**
     * The AIR Arctic Island pond (room_models 'snowstorm_arena_1', from
     * Domexx/GameCenter) is a frozen moat, three tiles wide, around a walkable
     * snow island, crossed by a one-tile plank on every side. The painted
     * snst_bg_1_a_big backdrop shows exactly this ring and these planks.
     */
    @Test
    void arcticIslandPondIsAMoatAroundAWalkableIslandWithFourPlanks() throws IOException {
        List<String> rows =
                readResource("/snowwar/arena_8_heightmap.txt").lines().toList();

        assertEquals(
                readResource("/snowwar/arena_1_heightmap.txt").lines().toList(),
                rows,
                "arena 8 uses the original Arctic Island floor");

        for (int y = 19; y <= 30; y++) {
            for (int x = 20; x <= 32; x++) {
                boolean island = x >= 23 && x <= 29 && y >= 22 && y <= 27;
                boolean northPlank = x == 25 && y >= 19 && y <= 21;
                boolean eastPlank = y == 24 && x >= 30 && x <= 32;
                boolean westPlank = y == 26 && x >= 20 && x <= 22;
                boolean southPlank = x == 27 && y >= 28 && y <= 30;
                char expected = island || northPlank || eastPlank || westPlank || southPlank ? '0' : 'x';
                assertEquals(expected, rows.get(y).charAt(x), "tile " + x + "," + y);
            }
        }

        SnowWarMap map = new SnowWarMap(8, rows, List.of(), List.of(), List.of());
        assertFalse(map.getTile(20, 19).isWalkable());
        assertFalse(map.getTile(24, 20).isWalkable());
        assertFalse(map.getTile(31, 29).isWalkable());
        assertTrue(map.getTile(26, 24).isWalkable(), "island");
        assertTrue(map.getTile(25, 20).isWalkable(), "north plank");
        assertTrue(map.getTile(31, 24).isWalkable(), "east plank");
        assertTrue(map.getTile(21, 26).isWalkable(), "west plank");
        assertTrue(map.getTile(27, 29).isWalkable(), "south plank");
    }

    /** Like the original, a snowball machine stands in the middle of the island. */
    @Test
    void arcticIslandHasASnowballMachineInTheMiddleOfTheIsland() throws IOException {
        List<String> rows =
                readResource("/snowwar/arena_8_heightmap.txt").lines().toList();
        List<String> machines =
                readResource("/snowwar/arena_8_snowmachines.dat").lines().toList();

        assertEquals(List.of("25 24"), machines, "island machine only");
        for (String machine : machines) {
            String[] parts = machine.trim().split("\\s+");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            // A machine covers x..x+2 on its row and is used from (x, y + 1).
            for (int dx = 0; dx < 3; dx++) {
                assertEquals('0', rows.get(y).charAt(x + dx), "machine tile " + (x + dx) + "," + y);
            }
            assertEquals('0', rows.get(y + 1).charAt(x), "pickup tile " + x + "," + (y + 1));
        }
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
