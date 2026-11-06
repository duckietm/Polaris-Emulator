package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.database.Database;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * :unban lifted every active ban of a user whoever had placed it, so a lower rank could undo an
 * administrator's ban, and it concatenated the localized "unbanned" text into the statement.
 */
class UnbanRankTest {

    private static User moderator(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    /** Runs an unban against mocks and answers the statement it bound. */
    private static PreparedStatement unban(User moderator) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(ModToolManager.UNBAN_SQL)).thenReturn(statement);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenReturn("Unbanned');--");
        Rank top = mock(Rank.class);
        when(top.getId()).thenReturn(7);
        PermissionsManager permissions = mock(PermissionsManager.class);
        when(permissions.getAllRanks()).thenReturn(List.of(top));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(permissions);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getTexts).thenReturn(texts);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_800_000_000);

            ModToolManager manager = mock(ModToolManager.class, CALLS_REAL_METHODS);
            manager.unban("target", moderator);
        }

        return statement;
    }

    @Test
    void aModeratorLiftsOnlyBansOfLowerRanksAndTheirOwn() throws Exception {
        PreparedStatement statement = unban(moderator(9, 4));

        verify(statement).setInt(5, 9);
        verify(statement).setInt(6, 3);
    }

    @Test
    void theTopRankAlsoLiftsItsPeers() throws Exception {
        verify(unban(moderator(9, 7))).setInt(6, 7);
    }

    @Test
    void noModeratorMeansNoRankLimit() throws Exception {
        PreparedStatement statement = unban(null);

        verify(statement).setInt(5, 0);
        verify(statement).setInt(6, Integer.MAX_VALUE);
    }

    @Test
    void theNoteIsABoundParameter() throws Exception {
        verify(unban(moderator(9, 4))).setString(2, "Unbanned');--");
        assertFalse(ModToolManager.UNBAN_SQL.contains("'Unbanned"), ModToolManager.UNBAN_SQL);
        assertTrue(ModToolManager.UNBAN_SQL.contains("staff.`rank` <= ?"));
        assertEquals(6, ModToolManager.UNBAN_SQL.chars().filter(c -> c == '?').count());
    }

    @Test
    void bothCallersNameTheirModerator() throws Exception {
        String command = Files.readString(Path.of("src/main/java/polaris/hotel/commands/UnbanCommand.java"));
        String housekeeping = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/housekeeping/HousekeepingUnbanUserEvent.java"));

        assertTrue(command.contains("unban(params[1], gameClient.getUser())"));
        assertTrue(housekeeping.contains("unban(info.getUsername(), this.client.getUser())"));
    }
}
