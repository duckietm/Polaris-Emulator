package polaris.hotel.wheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.database.Database;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * Characterises how the fortune wheel charges a spin, before anything about it changes. The wheel reads and writes the
 * wallet itself (a balance check, then a debit), so these tests pin what a purchase does today.
 */
class WheelManagerTest {
    private static final int USER_ID = 7;
    private static final int TODAY = 20_000;

    /** The static singletons the manager reaches for, stubbed; the database holds one user state and no prizes. */
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        final PreparedStatement writeStatement = mock(PreparedStatement.class);
        final User user = mock(User.class);
        final UserInfo userInfo = mock(UserInfo.class);
        final ConfigurationManager config = mock(ConfigurationManager.class);

        Fixture(Map<String, Integer> settings, Integer storedExtraSpins) throws SQLException {
            when(config.getInt(anyString(), anyInt())).thenAnswer(call -> {
                String key = call.getArgument(0);
                return settings.getOrDefault(key, call.getArgument(1));
            });
            when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));

            Database database = mock(Database.class);
            HikariDataSource dataSource = mock(HikariDataSource.class);
            Connection connection = mock(Connection.class);
            ResultSet noRows = mock(ResultSet.class);
            PreparedStatement readStatement = mock(PreparedStatement.class);
            ResultSet stateRow = mock(ResultSet.class);
            PreparedStatement stateStatement = mock(PreparedStatement.class);

            when(database.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(noRows.next()).thenReturn(false);
            when(readStatement.executeQuery()).thenReturn(noRows);
            when(connection.prepareStatement(anyString())).thenReturn(readStatement);

            // SELECT free_spins, extra_spins, last_reset FROM wheel_user_state
            when(connection.prepareStatement(contains("SELECT free_spins"))).thenReturn(stateStatement);

            if (storedExtraSpins == null) {
                when(stateStatement.executeQuery()).thenReturn(noRows);
            } else {
                when(stateRow.next()).thenReturn(true, false);
                when(stateRow.getInt("free_spins")).thenReturn(1);
                when(stateRow.getInt("extra_spins")).thenReturn(storedExtraSpins);
                when(stateRow.getInt("last_reset")).thenReturn(TODAY);
                when(stateStatement.executeQuery()).thenReturn(stateRow);
            }

            when(connection.prepareStatement(contains("INSERT INTO wheel_user_state")))
                    .thenReturn(writeStatement);

            when(user.getUserInfo()).thenReturn(userInfo);
            when(userInfo.getId()).thenReturn(USER_ID);
            when(user.tryTakeCredits(anyInt())).thenReturn(true);
            when(user.tryTakePoints(anyInt(), anyInt())).thenReturn(true);

            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(TODAY * 86_400);
        }

        @Override
        public void close() {
            emulator.close();
        }
    }

    @Test
    void aSpinPaidInCreditsChecksTheBalanceThenTakesTheCostAndAddsAnExtraSpin() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), null)) {
            when(fixture.userInfo.getCredits()).thenReturn(100);
            WheelManager manager = new WheelManager();

            assertTrue(manager.buySpin(fixture.user));

            verify(fixture.user).tryTakeCredits(50);
            assertEquals(1, manager.getUserState(USER_ID).extraSpins);
            verify(fixture.writeStatement, times(2)).executeUpdate();
        }
    }

    @Test
    void aSpinPaidInPointsTakesThePointsOfTheConfiguredType() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", 5), null)) {
            when(fixture.userInfo.getCurrencyAmount(5)).thenReturn(50);
            WheelManager manager = new WheelManager();

            assertTrue(manager.buySpin(fixture.user));

            verify(fixture.user).tryTakePoints(5, 50);
            verify(fixture.user, never()).tryTakeCredits(anyInt());
        }
    }

    @Test
    void aRefusedDebitGrantsNoSpin() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), null)) {
            when(fixture.userInfo.getCredits()).thenReturn(100);
            when(fixture.user.tryTakeCredits(50)).thenReturn(false);
            WheelManager manager = new WheelManager();

            assertFalse(manager.buySpin(fixture.user));

            assertEquals(0, manager.getUserState(USER_ID).extraSpins);
        }
    }

    @Test
    void aUserWhoLoggedOutIsReadAgainFromTheDatabase() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), null)) {
            when(fixture.userInfo.getCredits()).thenReturn(100);
            WheelManager manager = new WheelManager();

            assertTrue(manager.buySpin(fixture.user));
            assertEquals(1, manager.getUserState(USER_ID).extraSpins);

            manager.forgetUser(USER_ID);

            // The stubbed database holds no row, so a state read again starts from nothing.
            assertEquals(0, manager.getUserState(USER_ID).extraSpins);
        }
    }

    @Test
    void aBalanceBelowTheCostBuysNothing() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), null)) {
            when(fixture.userInfo.getCredits()).thenReturn(49);
            WheelManager manager = new WheelManager();

            assertFalse(manager.buySpin(fixture.user));

            verify(fixture.user, never()).tryTakeCredits(anyInt());
            assertEquals(0, manager.getUserState(USER_ID).extraSpins);
        }
    }

    @Test
    void aCostOfZeroTurnsTheShopOff() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 0), null)) {
            WheelManager manager = new WheelManager();

            assertFalse(manager.buySpin(fixture.user));

            verify(fixture.user, never()).tryTakeCredits(anyInt());
            verify(fixture.user, never()).tryTakePoints(anyInt(), anyInt());
        }
    }

    @Test
    void aUserAtTheExtraSpinCapCannotBuyMore() throws SQLException {
        try (Fixture fixture =
                new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), WheelManager.MAX_EXTRA_SPINS)) {
            when(fixture.userInfo.getCredits()).thenReturn(1_000);
            WheelManager manager = new WheelManager();

            assertFalse(manager.buySpin(fixture.user));

            verify(fixture.user, never()).tryTakeCredits(anyInt());
        }
    }

    @Test
    void aNewUserStartsWithTheDailyFreeSpinsAndTheStateIsWritten() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.free_spins_per_day", 3), null)) {
            WheelManager manager = new WheelManager();

            WheelUserState state = manager.getUserState(USER_ID);

            assertEquals(3, state.freeSpins);
            assertEquals(0, state.extraSpins);
            verify(fixture.writeStatement).setInt(eq(1), eq(USER_ID));
            verify(fixture.writeStatement).executeUpdate();
        }
    }

    /**
     * Current behaviour, to be revisited: the cost is taken before the new state is written, and a failed write is
     * only logged, so the player pays and the extra spin is lost after a restart. If the purchase becomes atomic,
     * this is the test to turn around.
     */
    @Test
    void aFailedStateWriteStillKeepsTheDebit() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of("wheel.spin_cost", 50, "wheel.spin_cost_type", -1), 0)) {
            when(fixture.userInfo.getCredits()).thenReturn(100);
            when(fixture.writeStatement.executeUpdate()).thenThrow(new SQLException("write failed"));
            WheelManager manager = new WheelManager();

            assertTrue(manager.buySpin(fixture.user));

            verify(fixture.user).tryTakeCredits(50);
        }
    }

    @Test
    void theWheelIsOnUnlessWheelEnabledIsZeroAndASwitchCountsAtOnce() throws SQLException {
        try (Fixture fixture = new Fixture(Map.of(), null)) {
            WheelManager manager = new WheelManager();

            assertTrue(manager.isEnabled());

            // :update_config reloads the same configuration; the wheel reads the switch on every request.
            when(fixture.config.getBoolean("wheel.enabled", true)).thenReturn(false);

            assertFalse(manager.isEnabled());
        }
    }
}
