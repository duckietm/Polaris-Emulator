package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;
import polaris.plugin.PluginManager;
import polaris.plugin.events.inventory.InventoryItemRemovedEvent;

/** A plugin that cancels a removal keeps the item, and callers that consume items are told so. */
class ItemsComponentCancelledRemovalTest {

    private static UserItem item(int id, Item base) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    private static void run(boolean cancel, ThrowingCheck check) throws Exception {
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (cancel && event instanceof InventoryItemRemovedEvent removed) removed.setCancelled(true);
            return event;
        });
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("no database in this test"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            ItemsComponent items = new ItemsComponent(
                    mock(UserInventory.class, RETURNS_DEEP_STUBS), mock(User.class, RETURNS_DEEP_STUBS));
            check.run(items);
        }
    }

    @FunctionalInterface
    private interface ThrowingCheck {
        void run(ItemsComponent items) throws Exception;
    }

    @Test
    void aCancelledRemovalKeepsTheItemAndReportsIt() throws Exception {
        Item base = mock(Item.class);
        UserItem item = item(4, base);

        run(true, items -> {
            items.addItem(item);

            assertFalse(items.tryRemoveUserItem(item));
            assertNull(items.getAndRemoveUserItem(base), "the wired take-furni effect must not delete it");
            assertNull(items.getAndRemoveUnrentedUserItem(base), "crafting must not spend it");
            assertSame(item, items.getUserItem(4));
        });
    }

    @Test
    void anAllowedRemovalTakesTheItem() throws Exception {
        Item base = mock(Item.class);
        UserItem item = item(4, base);

        run(false, items -> {
            items.addItem(item);

            assertSame(item, items.getAndRemoveUserItem(base));
            assertNull(items.getUserItem(4));
            assertFalse(items.tryRemoveUserItem(item), "already gone");
            items.addItem(item);
            assertTrue(items.tryRemoveUserItem(item));
        });
    }
}
