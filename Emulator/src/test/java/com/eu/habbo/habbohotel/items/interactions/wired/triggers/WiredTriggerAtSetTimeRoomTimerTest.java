package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** At Given Time counts from the room timer's last reset; a 0-pulse box fires only after a Reset Timers (AIR). */
class WiredTriggerAtSetTimeRoomTimerTest {
    private static final int ROOM_ID = 9_202;

    @AfterEach
    void forget() {
        WiredRoomTime.forgetRoom(ROOM_ID);
    }

    @Test
    void aBoxPlacedAfterItsTimeCountsAsFiredUntilTheNextReset() {
        Room room = room();
        long now = System.currentTimeMillis();
        WiredRoomTime.markTimersReset(room, now - 11_000L);
        WiredTriggerAtSetTime box = box(10_000);

        try (MockedStatic<WiredManager> wired = allowingTriggers()) {
            box.onRegistered(room, now);
            box.onWiredTick(room, 0L, 50);
            box.onWiredTick(room, 1L, 50);

            wired.verify(() -> WiredManager.triggerTimerTick(any(), any()), never());
        }
    }

    @Test
    void aBoxPlacedBeforeItsTimeFiresWhenTheRoomTimerGetsThere() throws Exception {
        Room room = room();
        long now = System.currentTimeMillis();
        // The room timer is at 10.8 s; the box is placed with 22 pulses (11 s): it fires 200 ms later, not 11 s.
        WiredRoomTime.markTimersReset(room, now - 10_800L);
        when(room.getCycleTimestamp()).thenReturn(now - 10_800L);
        WiredTriggerAtSetTime box = box(11_000);

        try (MockedStatic<WiredManager> wired = allowingTriggers()) {
            box.onRegistered(room, now);
            box.onWiredTick(room, 0L, 50);
            wired.verify(() -> WiredManager.triggerTimerTick(any(), any()), never());
            Thread.sleep(260L);
            box.onWiredTick(room, 1L, 50);

            wired.verify(() -> WiredManager.triggerTimerTick(any(), any()), times(1));
        }
    }

    @Test
    void aZeroPulseBoxFiresOnlyOnTheFirstRoomTickAfterAReset() throws Exception {
        Room room = room();
        WiredTriggerAtSetTime box = box(0);

        try (MockedStatic<WiredManager> wired = allowingTriggers()) {
            box.onRegistered(room, System.currentTimeMillis());
            box.onWiredTick(room, 0L, 50);
            wired.verify(() -> WiredManager.triggerTimerTick(any(), any()), never());

            long reset = System.currentTimeMillis();
            when(room.getCycleTimestamp()).thenReturn(reset - 10L);
            box.resetTimer();
            Thread.sleep(520L);
            box.onWiredTick(room, 1L, 50);
            box.onWiredTick(room, 2L, 50);

            wired.verify(() -> WiredManager.triggerTimerTick(any(), any()), times(1));
        }
    }

    private static WiredTriggerAtSetTime box(int executeTimeMs) {
        WiredTriggerAtSetTime box = new WiredTriggerAtSetTime(1, 1, mock(Item.class), "", 0, 0);
        box.executeTime = executeTimeMs;
        box.setRoomId(ROOM_ID);
        return box;
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static MockedStatic<WiredManager> allowingTriggers() {
        MockedStatic<WiredManager> wired = mockStatic(WiredManager.class);
        wired.when(() -> WiredManager.isTriggerExecutionAllowed(any(), any(), anyLong()))
                .thenReturn(true);
        return wired;
    }
}
