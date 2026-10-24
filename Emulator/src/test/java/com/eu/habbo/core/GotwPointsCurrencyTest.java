package com.eu.habbo.core;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Game-of-the-week points are paid into the currency type behind
 * {@code seasonal.currency.<name>}. A name merely listed in
 * {@code seasonal.currency.names} used to be enough to pay, which meant paying into
 * type -1 - no currency at all - once per interval for every online user.
 */
class GotwPointsCurrencyTest {

    private static Habbo onlineHabbo() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        when(rank.getGotwTimerAmount()).thenReturn(5);
        when(info.getRank()).thenReturn(rank);
        when(info.getCurrentRoom()).thenReturn(mock(Room.class));
        when(habbo.getHabboInfo()).thenReturn(info);
        HabboStats stats = mock(HabboStats.class);
        when(stats.hasActiveClub()).thenReturn(false);
        when(habbo.getHabboStats()).thenReturn(stats);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.isIdle()).thenReturn(false);
        when(habbo.getRoomUnit()).thenReturn(unit);
        return habbo;
    }

    /** Runs one tick with the currency key resolving to {@code currencyType}. */
    private static Habbo tickWith(int currencyType) {
        Habbo habbo = onlineHabbo();
        ConcurrentHashMap<Integer, Habbo> online = new ConcurrentHashMap<>();
        online.put(1, habbo);

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

        HabboManager habboManager = mock(HabboManager.class);
        when(habboManager.getOnlineHabbos()).thenReturn(online);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getHabboManager()).thenReturn(habboManager);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getThreading).thenReturn(mock(com.eu.habbo.threading.ThreadPooling.class));

            GotwPointsScheduler scheduler = new GotwPointsScheduler();
            scheduler.tick();
        }

        return habbo;
    }

    @Test
    void nothingIsPaidWhenTheCurrencyIsNotConfigured() {
        Habbo habbo = tickWith(-1);

        verify(habbo, never()).givePoints(anyInt(), anyInt());
    }

    @Test
    void theConfiguredCurrencyIsPaid() {
        Habbo habbo = tickWith(5);

        verify(habbo).givePoints(5, 5);
    }
}
