package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ModToolSanctionsTest {
    private int requestedUser;

    @Test
    void everyCallGetsItsOwnMap() throws Exception {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            Database database = mock(Database.class);
            HikariDataSource dataSource = mock(HikariDataSource.class);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            when(database.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenAnswer(i -> this.connection());

            ModToolSanctions sanctions = new ModToolSanctions();
            Map<Integer, ArrayList<ModToolSanctionItem>> first = sanctions.getSanctions(7);
            Map<Integer, ArrayList<ModToolSanctionItem>> second = sanctions.getSanctions(8);

            assertNotSame(first, second);
            assertEquals(List.of(7), List.copyOf(first.keySet()), "a later call must not clear an earlier result");
            assertEquals(2, first.get(7).size());
            assertEquals(List.of(8), List.copyOf(second.keySet()));
        }
    }

    private Connection connection() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        org.mockito.Mockito.doAnswer(i -> {
                    this.requestedUser = i.getArgument(1);
                    return null;
                })
                .when(statement)
                .setInt(anyInt(), anyInt());
        when(statement.executeQuery()).thenReturn(rows);
        int[] left = {2};
        when(rows.next()).thenAnswer(i -> left[0]-- > 0);
        when(rows.getInt("habbo_id")).thenAnswer(i -> this.requestedUser);
        when(rows.getInt("id")).thenAnswer(i -> left[0]);
        when(rows.getString("reason")).thenReturn("reason");
        return connection;
    }
}
