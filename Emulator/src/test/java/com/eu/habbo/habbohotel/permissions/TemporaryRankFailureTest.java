package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.HabboManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * setRank logged a failed UPDATE users SET rank and went on, and the sweep then cleared the temporary rank: a
 * passing database error made a timed VIP or event rank permanent, while the audit recorded a return that never
 * happened.
 */
class TemporaryRankFailureTest {

    @Test
    void aRankThatCouldNotBeStoredIsAFailure() {
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getPermissionsManager().rankExists(2)).thenReturn(true);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<SqlQueries> sql = mockStatic(SqlQueries.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            sql.when(() -> SqlQueries.queryOne(anyString(), any(), any())).thenReturn(Optional.of(5));
            sql.when(() -> SqlQueries.update(anyString(), any(), any()))
                    .thenThrow(new SqlQueries.DataAccessException("db down", new RuntimeException()));

            Exception failure = assertThrows(
                    Exception.class, () -> new HabboManager().setRank(7, 2, 0, "system", TemporaryRanks.VIA_ENDED));
            assertTrue(failure.getMessage().startsWith("Could not store rank 2 for user 7"), failure.getMessage());
        }
    }

    @Test
    void theSweepKeepsTheTimerOfARankItCouldNotGiveBack() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/permissions/TemporaryRanks.java"));
        int sweep = source.indexOf("private void endTemporaryRanks(int now)");
        String body = source.substring(sweep, source.indexOf("void refreshExpiredOverrides", sweep));

        int failure = body.indexOf("Failed to end the temporary rank");
        assertTrue(failure > 0 && body.indexOf("continue;", failure) > failure);
        assertTrue(body.indexOf("continue;", failure) < body.indexOf("clear(row.userId());"));
    }
}
