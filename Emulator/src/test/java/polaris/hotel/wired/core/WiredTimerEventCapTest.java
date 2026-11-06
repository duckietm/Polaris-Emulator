package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;

/** Repeaters are capped per room per second, and never get the room's wired banned. */
class WiredTimerEventCapTest {

    private final AtomicLong clock = new AtomicLong(50_000L);

    @Test
    void aFastRepeaterNeverBansItsRoom() {
        Room room = room(11);
        WiredExecutionGuard guard = guard(100);

        // A 50 ms repeater for 30 seconds: 600 firings, far over the 100 per 10 s event limit.
        for (int i = 0; i < 600; i++) {
            assertTrue(this.enterTimer(guard, room));
            this.clock.addAndGet(50L);
        }

        assertFalse(guard.isRoomBanned(room.getId()));
    }

    @Test
    void timerFiringsOverTheCapAreDroppedForTheRestOfTheSecond() {
        Room room = room(12);
        WiredExecutionGuard guard = guard(100);

        int admitted = 0;
        for (int i = 0; i < 300; i++) {
            if (this.enterTimer(guard, room)) admitted++;
        }

        assertEquals(WiredExecutionGuard.TIMER_EVENTS_PER_SECOND, admitted);
        assertFalse(guard.isRoomBanned(room.getId()));
        this.clock.addAndGet(1_000L);
        assertTrue(this.enterTimer(guard, room));
    }

    private boolean enterTimer(WiredExecutionGuard guard, Room room) {
        boolean admitted =
                guard.tryEnter(room, WiredEvent.Type.TIMER_REPEAT_SHORT, WiredExecutionGuard.EntryKind.SOURCE_ITEM);
        if (admitted) {
            guard.exit(room.getId());
        }
        return admitted;
    }

    private WiredExecutionGuard guard(int eventLimit) {
        return new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(
                        10, eventLimit, 10_000L, 600_000L, 1_000, 100, 10, 50, 150, 70, 5, 2, 60),
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
