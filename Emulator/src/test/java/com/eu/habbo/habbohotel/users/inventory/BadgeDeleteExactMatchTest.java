package com.eu.habbo.habbohotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * :takebadge on an offline user, the remove-badge wired and the custom badge manager all
 * delete through deleteBadge, which passed the code to a LIKE unescaped: ":takebadge name %"
 * deleted every badge the offline user had, staff and achievement badges included.
 */
class BadgeDeleteExactMatchTest {

    @Test
    void theCodeIsMatchedExactly() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            sql.set(invocation.getArgument(0));
            return statement;
        });

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            BadgesComponent.deleteBadge(7, "%");
        }

        assertEquals("DELETE users_badges FROM users_badges WHERE user_id = ? AND badge_code = ?", sql.get());
        verify(statement).setInt(1, 7);
        verify(statement).setString(2, "%");
    }
}
