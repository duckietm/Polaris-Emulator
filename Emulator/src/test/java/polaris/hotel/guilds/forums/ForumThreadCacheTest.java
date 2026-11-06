package polaris.hotel.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/**
 * A guild's thread set used to be cached empty before its query ran: a second reader after a restart saw no threads
 * or only some, and a query that failed left the forum empty until the next restart.
 */
class ForumThreadCacheTest {

    private static final int GUILD = 9_901;

    @AfterEach
    void forget() {
        ForumThread.clearCacheForGuild(GUILD);
    }

    private static Connection connectionWithOneThread() throws SQLException {
        ResultSet threadRows = mock(ResultSet.class);
        when(threadRows.next()).thenReturn(true, false);
        when(threadRows.getInt("id")).thenReturn(77);
        when(threadRows.getInt("guild_id")).thenReturn(GUILD);
        when(threadRows.getString(anyString())).thenReturn("");
        PreparedStatement threads = mock(PreparedStatement.class);
        when(threads.executeQuery()).thenReturn(threadRows);

        ResultSet noRows = mock(ResultSet.class);
        PreparedStatement comments = mock(PreparedStatement.class);
        when(comments.executeQuery()).thenReturn(noRows);

        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(comments);
        when(connection.prepareStatement(argThat((String sql) -> sql.contains("FROM guilds_forums_threads"))))
                .thenReturn(threads);
        return connection;
    }

    @Test
    void aFailedLoadIsNotCachedAndTheNextOneFindsTheThreads() throws Exception {
        Connection connection = connectionWithOneThread();
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection())
                .thenThrow(new SQLException("down"))
                .thenReturn(connection);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            assertTrue(ForumThread.getByGuildId(GUILD).isEmpty());

            Set<ForumThread> loaded = ForumThread.getByGuildId(GUILD);
            assertEquals(1, loaded.size());
            assertEquals(77, loaded.iterator().next().getThreadId());
            // Cached once complete: the next reader gets the same set without a query.
            assertSame(loaded, ForumThread.getByGuildId(GUILD));
        }
    }
}
