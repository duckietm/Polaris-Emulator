package com.eu.habbo.habbohotel.messenger.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Listing the conversations used to bring the user's pre-messenger private chatlogs
 * across every single time - an INSERT ... SELECT ... GROUP BY over all their rows in
 * chatlogs_private, which is MyISAM and takes a table lock, plus an INSERT IGNORE whose
 * SUBSTRING_INDEX conditions scan messenger_conversations twice. The console open and
 * every "mark as read" paid for it.
 */
class MessengerLegacyBackfillTest {

    /** A distinct id per case: the repository remembers who has been migrated. */
    private static int nextUserId = 900_000;

    private final List<String> prepared = new ArrayList<>();

    private DataSource dataSource() throws SQLException {
        ResultSet empty = mock(ResultSet.class);
        when(empty.next()).thenReturn(false);

        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(empty);

        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            this.prepared.add(invocation.getArgument(0));
            return statement;
        });

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private long backfills() {
        return this.prepared.stream()
                .filter(sql -> sql.contains("chatlogs_private"))
                .count();
    }

    @Test
    void theLegacyBackfillRunsOncePerUser() throws Exception {
        JdbcMessengerHistoryRepository repository = new JdbcMessengerHistoryRepository(this.dataSource());
        int userId = nextUserId++;

        repository.listConversations(userId);
        assertEquals(1, this.backfills(), "the first listing migrates");

        repository.listConversations(userId);
        repository.listConversations(userId);
        assertEquals(1, this.backfills(), "every later listing skips it");
    }

    @Test
    void everyUserIsMigratedOnTheirOwn() throws Exception {
        JdbcMessengerHistoryRepository repository = new JdbcMessengerHistoryRepository(this.dataSource());

        repository.listConversations(nextUserId++);
        repository.listConversations(nextUserId++);

        assertEquals(2, this.backfills());
    }
}
