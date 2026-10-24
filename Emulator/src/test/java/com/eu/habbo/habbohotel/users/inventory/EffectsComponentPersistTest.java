package com.eu.habbo.habbohotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An effect bought from the catalogue lasts as long as its offer says, and buying a
 * timed copy of one already owned permanently must not turn the permanent one into a
 * 24-hour effect - which a plain {@code duration = VALUES(duration)} did.
 */
class EffectsComponentPersistTest {

    private final List<String> statements = new ArrayList<>();
    private final PreparedStatement upsert = mock(PreparedStatement.class);

    private Connection connection() throws SQLException {
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(row.next()).thenReturn(true);
        when(select.executeQuery()).thenReturn(row);

        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            this.statements.add(sql);
            return sql.startsWith("INSERT") ? this.upsert : select;
        });
        return connection;
    }

    @Test
    void theOffersDurationIsWhatGetsStored() throws Exception {
        EffectsComponent.persistEffect(this.connection(), 7, 42, 3600);

        verify(this.upsert).setInt(3, 3600);
    }

    @Test
    void aPermanentEffectIsStoredAsPermanent() throws Exception {
        EffectsComponent.persistEffect(this.connection(), 7, 42, 0);

        verify(this.upsert).setInt(3, 0);
    }

    @Test
    void aTimedCopyDoesNotDowngradeAPermanentEffect() throws Exception {
        EffectsComponent.persistEffect(this.connection(), 7, 42, 86400);

        String upsertSql = this.statements.get(0);
        assertTrue(
                upsertSql.contains("duration = IF(duration = 0, 0, VALUES(duration))"),
                "a permanent duration must survive a timed purchase: " + upsertSql);
        assertTrue(upsertSql.contains("total = LEAST(total + 1, 100)"), "the stack still grows: " + upsertSql);
        assertEquals(2, this.statements.size());
    }
}
