package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * :unban lifted every active ban of a user whoever had placed it, so a lower rank could undo an
 * administrator's ban, and it concatenated the localized "unbanned" text into the statement.
 */
class UnbanRankTest {

    private static Habbo moderator(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    /** Runs an unban against mocks and answers the statement it bound. */
    private static PreparedStatement unban(Habbo moderator) throws Exception {
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
        String command = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/UnbanCommand.java"));
        String housekeeping = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingUnbanUserEvent.java"));

        assertTrue(command.contains("unban(params[1], gameClient.getHabbo())"));
        assertTrue(housekeeping.contains("unban(info.getUsername(), this.client.getHabbo())"));
    }
}
