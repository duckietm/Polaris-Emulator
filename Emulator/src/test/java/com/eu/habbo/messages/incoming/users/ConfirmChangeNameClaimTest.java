package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Two users confirming "Bob" and "bob" together both passed the name lookup; the in-progress set held the original
 * case while the check looked for lowercase; and the loser kept a duplicate name in memory, with its rooms, groups and
 * the change log rewritten, while the database had refused it.
 */
class ConfirmChangeNameClaimTest {

    private static MockedStatic<Emulator> database(PreparedStatement statement) throws Exception {
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        Connection connection = mock(Connection.class);
        when(database.getDataSource().getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getDatabase).thenReturn(database);
        return emulator;
    }

    @Test
    void theNameIsTheUsersOnceTheDatabaseTakesIt() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeUpdate()).thenReturn(1);

        try (MockedStatic<Emulator> ignored = database(statement)) {
            assertTrue(ConfirmChangeNameEvent.claimUsername(7, "Bob"));
        }
    }

    @Test
    void aNameAnotherUserHoldsIsRefused() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException("Duplicate entry"));

        try (MockedStatic<Emulator> ignored = database(statement)) {
            assertFalse(ConfirmChangeNameEvent.claimUsername(7, "bob"));
        }
    }

    @Test
    void aDuplicateReportedByStateIsRefusedToo() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeUpdate()).thenThrow(new SQLException("Duplicate entry", "23000"));

        try (MockedStatic<Emulator> ignored = database(statement)) {
            assertFalse(ConfirmChangeNameEvent.claimUsername(7, "bob"));
        }
    }

    @Test
    void nothingChangesBeforeTheClaimAndTheSetIsLowercase() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/users/ConfirmChangeNameEvent.java"));

        int claim = source.indexOf("claimUsername(this.client");
        assertTrue(claim > 0);
        assertTrue(claim < source.indexOf("getHabboInfo().setUsername(name)"));
        assertTrue(claim < source.indexOf("room.setOwnerName(name)"));
        assertTrue(claim < source.indexOf("INSERT INTO namechange_log"));
        assertTrue(source.contains("String key = name.toLowerCase(Locale.ROOT);"));
        assertTrue(source.contains("changingUsernames.add(key);"));
    }
}
