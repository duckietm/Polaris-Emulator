package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/**
 * :update_pets cleared the pet tables before querying them again, so a purchase in between found its type gone, and
 * a reload whose queries failed left the hotel with no pets at all. The tables are built apart and swapped now.
 */
class PetManagerReloadTest {

    private static void set(PetManager manager, String name, Object value) throws Exception {
        Field field = PetManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static PetManager loadedWith(int type, PetData data) throws Exception {
        PetManager manager = mock(PetManager.class, CALLS_REAL_METHODS);
        Map<Integer, PetData> petData = new ConcurrentHashMap<>();
        petData.put(type, data);
        set(manager, "petData", petData);
        set(manager, "petRaces", new HashMap<>());
        set(manager, "breedingPetType", new Int2IntOpenHashMap());
        set(manager, "breedingReward", new HashMap<>());
        return manager;
    }

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheLoadedTables() throws Exception {
        PetData dog = mock(PetData.class);
        PetManager manager = loadedWith(0, dog);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.reloadPetData();
        }

        assertSame(dog, manager.getPetData(0));
    }
}
