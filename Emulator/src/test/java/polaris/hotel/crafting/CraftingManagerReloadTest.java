package polaris.hotel.crafting;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.items.Item;

/** :update_catalogue cleared the altars before querying them again; a failed query left crafting empty. */
class CraftingManagerReloadTest {

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheAltars() throws Exception {
        CraftingManager manager = mock(CraftingManager.class, CALLS_REAL_METHODS);
        Item altarItem = mock(Item.class);
        CraftingAltar altar = mock(CraftingAltar.class);
        Map<Item, CraftingAltar> altars = new HashMap<>();
        altars.put(altarItem, altar);
        Field field = CraftingManager.class.getDeclaredField("altars");
        field.setAccessible(true);
        field.set(manager, altars);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.reload();
        }

        assertSame(altar, manager.getAltar(altarItem));
    }
}
