package polaris.core;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;

/**
 * Game-of-the-week points are paid into the currency type behind
 * {@code seasonal.currency.<name>}. A name merely listed in
 * {@code seasonal.currency.names} used to be enough to pay, which meant paying into
 * type -1 - no currency at all - once per interval for every online user.
 */
class GotwPointsCurrencyTest {

    private static User onlineUser() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Rank rank = mock(Rank.class);
        when(rank.getGotwTimerAmount()).thenReturn(5);
        when(info.getRank()).thenReturn(rank);
        when(info.getCurrentRoom()).thenReturn(mock(Room.class));
        when(user.getUserInfo()).thenReturn(info);
        UserStats stats = mock(UserStats.class);
        when(stats.hasActiveClub()).thenReturn(false);
        when(user.getUserStats()).thenReturn(stats);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.isIdle()).thenReturn(false);
        when(user.getRoomUnit()).thenReturn(unit);
        return user;
    }

    /** Runs one tick with the currency key resolving to {@code currencyType}. */
    private static User tickWith(int currencyType) {
        User user = onlineUser();
        ConcurrentHashMap<Integer, User> online = new ConcurrentHashMap<>();
        online.put(1, user);

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString())).thenReturn(true);
        when(config.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(i -> i.getArgument(1));
        when(config.getInt(anyString())).thenReturn(10);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt(eq("seasonal.currency.gotw"), anyInt())).thenReturn(currencyType);
        when(config.getDouble(anyString(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenAnswer(i -> i.getArgument(1));
        when(config.getValue(anyString())).thenReturn("gotw");

        UserManager userManager = mock(UserManager.class);
        when(userManager.getOnlineUsers()).thenReturn(online);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getUserManager()).thenReturn(userManager);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getThreading).thenReturn(mock(polaris.threading.ThreadPooling.class));

            GotwPointsScheduler scheduler = new GotwPointsScheduler();
            scheduler.tick();
        }

        return user;
    }

    @Test
    void nothingIsPaidWhenTheCurrencyIsNotConfigured() {
        User user = tickWith(-1);

        verify(user, never()).givePoints(anyInt(), anyInt());
    }

    @Test
    void theConfiguredCurrencyIsPaid() {
        User user = tickWith(5);

        verify(user).givePoints(5, 5);
    }
}
