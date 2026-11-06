package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/**
 * :update_items emptied the crackable rewards and soundtracks before querying them again, while rooms and jukeboxes
 * read them, and loadItems never removed a base item whose row had been deleted.
 */
class ItemManagerReloadTest {

    private static void set(ItemManager manager, String name, Object value) throws Exception {
        Field field = ItemManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @Test
    void aBaseItemWhoseRowIsGoneLeavesTheMap() throws Exception {
        ItemManager manager = mock(ItemManager.class, CALLS_REAL_METHODS);
        Item kept = mock(Item.class);
        Item deleted = mock(Item.class);
        Int2ObjectMap<Item> items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        items.put(1, kept);
        items.put(2, deleted);
        set(manager, "items", items);

        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getInt("id")).thenReturn(1);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.loadItems();
        }

        verify(kept).update(rows);
        assertSame(kept, items.get(1));
        assertFalse(items.containsKey(2), "the furni editor deleted this base item");
    }

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsEverything() throws Exception {
        ItemManager manager = mock(ItemManager.class, CALLS_REAL_METHODS);
        Int2ObjectMap<Item> items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        items.put(1, mock(Item.class));
        set(manager, "items", items);
        Int2ObjectMap<CrackableReward> rewards = new Int2ObjectOpenHashMap<>();
        CrackableReward egg = mock(CrackableReward.class);
        rewards.put(7, egg);
        set(manager, "crackableRewards", rewards);
        set(manager, "soundTracks", new ConcurrentHashMap<>());
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.loadItems();
            manager.loadCrackable();
            manager.loadSoundTracks();
        }

        assertEquals(1, items.size());
        assertSame(egg, manager.getCrackableData(7));
        assertNull(manager.getSoundTrack("missing"));
    }
}
