package polaris.hotel.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.gameclients.GameClient;

/** Accepting a request counts a new member only when the request row was really promoted. */
class GuildAcceptJoinResultTest {

    private static boolean accept(int promotedRows) throws Exception {
        GuildManager manager = mock(GuildManager.class, CALLS_REAL_METHODS);
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(8);

        ResultSet counts = mock(ResultSet.class);
        when(counts.next()).thenReturn(true);
        when(counts.getInt(1)).thenReturn(0);
        PreparedStatement count = mock(PreparedStatement.class);
        when(count.executeQuery()).thenReturn(counts);
        PreparedStatement promote = mock(PreparedStatement.class);
        when(promote.executeUpdate()).thenReturn(promotedRows);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(count);
        when(connection.prepareStatement(contains("UPDATE guilds_members"))).thenReturn(promote);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            return manager.tryJoinGuild(guild, mock(GameClient.class), 42, true);
        }
    }

    @Test
    void aPromotedRequestIsAJoin() throws Exception {
        assertTrue(accept(1));
    }

    @Test
    void aRequestThatIsAlreadyGoneIsNotAJoin() throws Exception {
        assertFalse(accept(0));
    }
}
