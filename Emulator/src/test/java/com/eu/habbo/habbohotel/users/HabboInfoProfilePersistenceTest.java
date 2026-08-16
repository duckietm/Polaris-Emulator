package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class HabboInfoProfilePersistenceTest {
    @Test
    void profileSnapshotNeverPersistsWalletBalances() throws Exception {
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        HabboInfo info = new HabboInfo(42, 100);
        info.applyPersistedCurrencyAmount(5, 200);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_700_000_000);
            info.run();
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1)).prepareStatement(sql.capture());
        List<String> statements = sql.getAllValues();

        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().startsWith("UPDATE users SET motto = ?"));
        assertFalse(statements.getFirst().contains("credits"));
        assertFalse(statements.stream().anyMatch(query -> query.contains("users_currency")));
    }
}
