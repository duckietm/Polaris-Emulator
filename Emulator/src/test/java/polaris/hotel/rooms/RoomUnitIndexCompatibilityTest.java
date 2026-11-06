package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class RoomUnitIndexCompatibilityTest {

    @Test
    void liveCollectionsAndUnitCounterSurviveIndexExtraction() {
        RoomUnitManager manager = new Room(41, 7).getUnitManager();

        assertSame(manager.getCurrentUsers(), manager.getCurrentUsers());
        assertSame(manager.getUserQueue(), manager.getUserQueue());
        assertSame(manager.getCurrentBots(), manager.getCurrentBots());
        assertSame(manager.getCurrentPets(), manager.getCurrentPets());

        assertEquals(0, manager.getNextUnitId());
        assertEquals(1, manager.getNextUnitId());
        manager.clear();
        assertEquals(0, manager.getUnitCounter());
    }
}
