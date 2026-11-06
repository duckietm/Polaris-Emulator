package polaris.hotel.games.snowwar.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.games.snowwar.SnowWarPoint;

class SnowWarMapsManagerTest {

    @Test
    void recognizesBothEditorAndAirMachineClassnames() {
        assertTrue(SnowWarMapsManager.isMachineName("snowball_machine"));
        assertTrue(SnowWarMapsManager.isMachineName("s_snowball_machine"));
        assertFalse(SnowWarMapsManager.isMachineName("snst_block1"));
    }

    @Test
    void preservesOfficialMachineAndPileOnlyLayouts() {
        List<SnowWarPoint> arcticMachines = new ArrayList<>();
        assertTrue(SnowWarMapsManager.loadBundledMachines(8, new ArrayList<>(), arcticMachines));
        // Arctic Island has a single machine, in the middle of the island.
        assertEquals(List.of(new SnowWarPoint(25, 24)), arcticMachines);

        for (int mapId : List.of(9, 11)) {
            List<SnowWarPoint> machines = new ArrayList<>();
            assertTrue(SnowWarMapsManager.loadBundledMachines(mapId, new ArrayList<>(), machines));
            assertTrue(machines.isEmpty());
        }
    }
}
