package com.eu.habbo.habbohotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredFurniStateBatch;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.games.GameUpCounter;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Game counter rules observed on AIR (WIN63-202610151745 and WIN63-202610201032). */
class InteractionGameUpCounterAirRulesTest {

    private static InteractionGameUpCounter counter(String extradata) {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        return new InteractionGameUpCounter(1, 1, base, extradata, 0, 0);
    }

    @Test
    void theCounterCountsOnPastNinetyNineFiftyNine() {
        InteractionGameUpCounter counter = counter("0");
        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            counter.adjustCounter(mock(Room.class), 2, 99, 119);
        }
        assertEquals(5_999_500L, counter.getCurrentTimeMs());

        counter.advanceCounterInMs(500);
        assertEquals(6000, counter.getTimeNow());
        counter.advanceCounterInMs(2000);
        assertEquals(6002, counter.getTimeNow());
        assertTrue(counter.getCurrentTimeMs() < counter.getMaximumTimeMs());
    }

    @Test
    void aStoredValuePastNinetyNineFiftyNineLoadsAsItIs() {
        assertEquals(6_002_000L, counter("6002").getCurrentTimeMs());
    }

    @Test
    void writesOfOneFiringGoOutOnceWithTheLastValue() {
        Room room = mock(Room.class);
        InteractionGameUpCounter counter = counter("0");
        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            WiredFurniStateBatch.open();
            try {
                counter.adjustCounter(room, 2, 0, 10);
                counter.adjustCounter(room, 2, 0, 20);
                verify(room, times(0)).sendComposer(any(ServerMessage.class));
            } finally {
                WiredFurniStateBatch.close();
            }
        }
        verify(room, times(1)).sendComposer(any(ServerMessage.class));
        assertEquals(10, counter.getTimeNow());
    }

    @Test
    void onlyACounterThatNeverRanKeepsASetValueForItsStart() throws Exception {
        InteractionGameUpCounter fresh = counter("0");
        InteractionGameUpCounter ran = counter("3");
        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            fresh.adjustCounter(null, 2, 0, 4);
            ran.adjustCounter(null, 2, 0, 4);
        }
        assertTrue(adjustedSinceStop(fresh));
        assertFalse(adjustedSinceStop(ran));
        assertEquals(2, ran.getTimeNow(), "the Set shows at once");
    }

    @Test
    void aSetOnARunningCounterRestartsItsTickFromTheSet() {
        InteractionGameUpCounter counter = counter("5");
        counter.setRunning(true);
        int chain = counter.getTimerChain();
        ThreadPooling threading = mock(ThreadPooling.class);
        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);

            counter.adjustCounter(null, 0, 0, 2);
            verify(threading, times(0)).run(any(Runnable.class), any(Long.class));

            counter.adjustCounter(null, 2, 0, 2);
        }
        verify(threading).run(any(GameUpCounter.class), eq(1000L));
        assertEquals(chain + 1, counter.getTimerChain(), "the old tick chain is stale");
    }

    private static boolean adjustedSinceStop(InteractionGameUpCounter counter) throws Exception {
        Field field = InteractionGameUpCounter.class.getDeclaredField("adjustedSinceStop");
        field.setAccessible(true);
        return field.getBoolean(counter);
    }
}
