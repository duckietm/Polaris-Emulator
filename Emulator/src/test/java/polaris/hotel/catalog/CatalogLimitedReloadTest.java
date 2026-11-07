package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.database.Database;

/**
 * :update_catalogue cleared the limited numbers before querying them, without the lock the readers take: a limited
 * purchase in between found no configuration, and one an in-flight purchase held was dropped.
 */
class CatalogLimitedReloadTest {

    @Test
    void aReloadKeepsTheConfigurationAndGivesItTheNewNumbers() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        CatalogLimitedConfiguration held = new CatalogLimitedConfiguration(5, new LinkedList<>(List.of(1)), 3, false);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);

            held.replaceNumbers(new LinkedList<>(List.of(2, 3)), 3);
        }

        assertEquals(2, held.available());
        // A number the purchase that holds this object gives back still lands in the live pool.
        held.restoreNumber(1);
        assertEquals(3, held.available());
    }

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheNumbers() throws Exception {
        CatalogManager manager = mock(CatalogManager.class, CALLS_REAL_METHODS);
        CatalogLimitedConfiguration held =
                new CatalogLimitedConfiguration(5, new LinkedList<>(List.of(1, 2)), 2, false);
        Map<Integer, CatalogLimitedConfiguration> limited = new HashMap<>();
        limited.put(5, held);
        Field field = CatalogManager.class.getDeclaredField("limitedNumbers");
        field.setAccessible(true);
        field.set(manager, limited);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));
        Method load = CatalogManager.class.getDeclaredMethod("loadLimitedNumbers");
        load.setAccessible(true);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            load.invoke(manager);
        }

        assertSame(held, limited.get(5));
        assertEquals(2, held.available());
    }
}
