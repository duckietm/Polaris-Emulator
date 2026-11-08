package polaris.messages.incoming.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Switching a forum off deletes its data in one transaction, and only a committed delete turns it off. */
class GuildForumDisableTransactionTest {

    private static Connection connection(PreparedStatement threads) throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        when(connection.prepareStatement(contains("DELETE FROM `guilds_forums_threads`")))
                .thenReturn(threads);
        return connection;
    }

    @Test
    void aFailedDeleteRollsEverythingBack() throws Exception {
        PreparedStatement threads = mock(PreparedStatement.class);
        when(threads.executeUpdate()).thenThrow(new SQLException("down"));
        Connection connection = connection(threads);

        assertFalse(GuildChangeSettingsEvent.deleteForumData(connection, 8));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void aCleanDeleteIsCommitted() throws Exception {
        Connection connection = connection(mock(PreparedStatement.class));

        assertTrue(GuildChangeSettingsEvent.deleteForumData(connection, 8));

        verify(connection).setAutoCommit(false);
        verify(connection).commit();
    }

    @Test
    void theForumIsSwitchedOffOnlyAfterTheDelete() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/guilds/GuildChangeSettingsEvent.java"));

        int delete = source.indexOf("} else if (this.deleteForumData(guildId)) {");
        int off = source.indexOf("guild.setForum(false);", delete);
        int cache = source.indexOf("ForumThread.clearCacheForGuild(guildId);", delete);

        assertTrue(delete > 0 && off > delete && cache > delete);
    }
}
