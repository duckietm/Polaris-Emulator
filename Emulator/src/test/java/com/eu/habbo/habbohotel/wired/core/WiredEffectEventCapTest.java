package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Signals and stack calls raised by effects are capped per second, and never ban their room. */
class WiredEffectEventCapTest {

    private final AtomicLong clock = new AtomicLong(50_000L);

    @Test
    void oneSignalPerFurniOfASelectionKeepsRunning() {
        Room room = room(21);
        WiredExecutionGuard guard = guard();

        // A 50 ms repeater sending a signal for each of 25 selected furni, for 30 seconds.
        for (int tick = 0; tick < 600; tick++) {
            for (int furni = 0; furni < 25; furni++) {
                assertTrue(this.enter(guard, room, WiredExecutionGuard.EntryKind.EFFECT));
            }
            this.clock.addAndGet(50L);
        }

        assertFalse(guard.isRoomBanned(room.getId()));
    }

    @Test
    void theSameSignalsAsOrdinaryEventsWouldBanTheRoom() {
        Room room = room(22);
        WiredExecutionGuard guard = guard();

        for (int i = 0; i < 125; i++) {
            this.enter(guard, room, WiredExecutionGuard.EntryKind.EVENT);
        }

        assertTrue(guard.isRoomBanned(room.getId()));
    }

    @Test
    void effectEventsOverTheCapAreDroppedForTheRestOfTheSecond() {
        Room room = room(23);
        WiredExecutionGuard guard = guard();

        int admitted = 0;
        for (int i = 0; i < WiredExecutionGuard.EFFECT_EVENTS_PER_SECOND + 200; i++) {
            if (this.enter(guard, room, WiredExecutionGuard.EntryKind.EFFECT)) admitted++;
        }

        assertEquals(WiredExecutionGuard.EFFECT_EVENTS_PER_SECOND, admitted);
        assertFalse(guard.isRoomBanned(room.getId()));
        this.clock.addAndGet(1_000L);
        assertTrue(this.enter(guard, room, WiredExecutionGuard.EntryKind.EFFECT));
    }

    private boolean enter(WiredExecutionGuard guard, Room room, WiredExecutionGuard.EntryKind kind) {
        boolean admitted = guard.tryEnter(room, WiredEvent.Type.SIGNAL_RECEIVED, kind);
        if (admitted) {
            guard.exit(room.getId());
        }
        return admitted;
    }

    private WiredExecutionGuard guard() {
        return new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(10, 100, 10_000L, 600_000L, 1_000, 100, 10, 50, 150, 70, 5, 2, 60),
                this.clock::get,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});
    }

    private static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }
}
