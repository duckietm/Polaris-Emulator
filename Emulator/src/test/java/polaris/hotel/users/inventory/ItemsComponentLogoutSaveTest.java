package polaris.hotel.users.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.users.UserItem;

/**
 * The logout batch wrote fewer columns than a single save (no rent expiry, no underpass, the shown extra data) and
 * cleared every flag before it ran, so a rent renewed just before leaving, or a failed batch, was lost.
 */
class ItemsComponentLogoutSaveTest {

    private final PreparedStatement updates = mock(PreparedStatement.class);
    private final PreparedStatement deletes = mock(PreparedStatement.class);

    private Database database() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(this.deletes);
        when(connection.prepareStatement(UserItem.UPDATE_ROW_SQL)).thenReturn(this.updates);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);
        return database;
    }

    private static UserItem changed(long generation) throws SQLException {
        UserItem item = mock(UserItem.class);
        when(item.needsUpdate()).thenReturn(true);
        when(item.bindRow(any(PreparedStatement.class))).thenReturn(generation);
        return item;
    }

    @Test
    void theBatchWritesTheFullRowAndMarksItSavedAfterwards() throws Exception {
        UserItem rented = changed(4);
        Database database = this.database();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            ItemsComponent.saveOnDispose(List.of(rented));
        }

        // bindRow is the column list UserItem.run() writes, expiry and underpass included.
        verify(rented).bindRow(this.updates);
        verify(this.updates).executeBatch();
        verify(rented).markSaved(4);
        verify(rented, never()).needsUpdate(false);
    }

    @Test
    void aFailedBatchRetriesTheUpdatesOneByOne() throws Exception {
        UserItem rented = changed(4);
        when(this.updates.executeBatch()).thenThrow(new SQLException("deadlock"));
        Database database = this.database();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            ItemsComponent.saveOnDispose(List.of(rented));
        }

        verify(rented, never()).markSaved(4);
        verify(rented).run();
    }
}
