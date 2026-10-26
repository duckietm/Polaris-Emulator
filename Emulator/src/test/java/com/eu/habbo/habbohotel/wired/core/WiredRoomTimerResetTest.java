package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Reset Timers restarts the room timer on the first 500 ms room tick after the reset (observed on AIR). */
class WiredRoomTimerResetTest {
    private static final int ROOM_ID = 9_101;
    private final Room room = room();

    @AfterEach
    void forget() {
        WiredRoomTime.forgetRoom(ROOM_ID);
    }

    @Test
    void theOldTimerIsReadUntilTheNextRoomTick() {
        WiredRoomTime.markTimersReset(this.room, 0L);
        // Room ticks at 10 000, 10 500, ...; the reset at 10 150 takes effect at 10 500.
        WiredRoomTime.scheduleTimersReset(this.room, 10_000L, 10_150L);

        assertEquals(20, WiredRoomTime.pulsesSinceTimerReset(this.room, 10_150L + 349L));
        assertEquals(10_499L, WiredRoomTime.millisSinceTimerReset(this.room, 10_499L));
        assertEquals(0L, WiredRoomTime.millisSinceTimerReset(this.room, 10_500L));
        assertEquals(1, WiredRoomTime.pulsesSinceTimerReset(this.room, 11_499L));
        assertEquals(3, WiredRoomTime.pulsesSinceTimerReset(this.room, 12_000L));
    }

    @Test
    void aSecondResetWhileOneWaitsKeepsTheWaitingOne() {
        WiredRoomTime.markTimersReset(this.room, 0L);
        WiredRoomTime.scheduleTimersReset(this.room, 10_000L, 10_050L);
        WiredRoomTime.scheduleTimersReset(this.room, 10_000L, 10_250L);

        assertEquals(10_449L, WiredRoomTime.millisSinceTimerReset(this.room, 10_449L));
        assertEquals(0, WiredRoomTime.pulsesSinceTimerReset(this.room, 10_950L));
        assertEquals(1, WiredRoomTime.pulsesSinceTimerReset(this.room, 11_000L));
    }

    @Test
    void aResetAfterTheWaitingOneTookEffectRestartsAgain() {
        WiredRoomTime.markTimersReset(this.room, 0L);
        WiredRoomTime.scheduleTimersReset(this.room, 10_000L, 10_150L);
        WiredRoomTime.scheduleTimersReset(this.room, 10_000L, 11_050L);

        assertEquals(1, WiredRoomTime.pulsesSinceTimerReset(this.room, 11_200L));
        assertEquals(0, WiredRoomTime.pulsesSinceTimerReset(this.room, 11_500L));
    }

    @Test
    void theFirstTickAfterAnInstantIsOnTheRoomGrid() {
        assertEquals(10_500L, WiredRoomTime.firstTickAfter(10_000L, 10_150L));
        assertEquals(10_500L, WiredRoomTime.firstTickAfter(10_000L, 10_000L));
        assertEquals(650L, WiredRoomTime.firstTickAfter(0L, 150L));
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(ROOM_ID);
        return room;
    }
}
