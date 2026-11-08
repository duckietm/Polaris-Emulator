package polaris.hotel.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.gameclients.GameClient;

/** Joining an open guild never adds a second row, and only real memberships count towards the group limit. */
class GuildJoinExistingRowTest {

    private static PreparedStatement counting(int total) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.next()).thenReturn(true);
        when(set.getInt(1)).thenReturn(total);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(set);
        return statement;
    }

    private static final class Join {
        final PreparedStatement userCount;
        final PreparedStatement insert = mock(PreparedStatement.class);
        final boolean joined;

        Join(int existingRows) throws Exception {
            GuildManager manager = mock(GuildManager.class, CALLS_REAL_METHODS);
            doReturn(false).when(manager).isBlocked(any(), anyInt());
            Guild guild = mock(Guild.class);
            when(guild.getId()).thenReturn(8);
            when(guild.getState()).thenReturn(GuildState.OPEN);
            GameClient client = mock(GameClient.class, RETURNS_DEEP_STUBS);
            when(client.getUser().getUserInfo().getId()).thenReturn(42);

            this.userCount = counting(0);
            Connection connection = mock(Connection.class);
            PreparedStatement guildCount = counting(0);
            PreparedStatement existing = counting(existingRows);
            when(connection.prepareStatement(anyString())).thenReturn(guildCount);
            when(connection.prepareStatement(contains("WHERE user_id = ? AND level_id < ?")))
                    .thenReturn(this.userCount);
            when(connection.prepareStatement(contains("WHERE guild_id = ? AND user_id = ?")))
                    .thenReturn(existing);
            when(connection.prepareStatement(contains("INSERT INTO guilds_members")))
                    .thenReturn(this.insert);
            Database database = mock(Database.class, RETURNS_DEEP_STUBS);
            when(database.getDataSource().getConnection()).thenReturn(connection);

            try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
                emulator.when(Emulator::getDatabase).thenReturn(database);
                this.joined = manager.tryJoinGuild(guild, client, 0, false);
            }
        }
    }

    @Test
    void anExistingRowInAnOpenGuildInsertsNothing() throws Exception {
        Join join = new Join(1);

        assertFalse(join.joined);
        verify(join.insert, never()).execute();
    }

    @Test
    void aNewMemberOfAnOpenGuildIsInsertedAndTheLimitCountsMembershipsOnly() throws Exception {
        Join join = new Join(0);

        assertTrue(join.joined);
        verify(join.insert).execute();
        verify(join.userCount).setInt(2, GuildRank.REQUESTED.type);
    }
}
