package polaris.hotel.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/**
 * Deleting a guild ran nine statements in autocommit with errors only logged, and forgot the guild in memory anyway:
 * a failure half way left a guild without members or forum that came back after a restart.
 */
class GuildDeleteTransactionTest {

    @Test
    void aStatementThatFailsRollsEverythingBackAndKeepsTheGuild() throws Exception {
        GuildManager manager = mock(GuildManager.class, CALLS_REAL_METHODS);
        Int2ObjectMap<Guild> guilds = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(8);
        guilds.put(8, guild);
        Field field = GuildManager.class.getDeclaredField("guilds");
        field.setAccessible(true);
        field.set(manager, guilds);
        doReturn(new HashSet<GuildMember>()).when(manager).getGuildMembers(guild);

        PreparedStatement statement = mock(PreparedStatement.class);
        // The members go, then the guild row refuses.
        when(statement.execute()).thenReturn(true, true, true, true, true).thenThrow(new SQLException("locked"));
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            assertFalse(manager.tryDeleteGuild(guild));
        }

        verify(connection).rollback();
        verify(connection, never()).commit();
        assertSame(guild, guilds.get(8));
    }
}
