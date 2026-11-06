package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;

/** Events a player raises are throttled per player and can never get the room's wired banned. */
class WiredPlayerEventThrottleTest {

    private final AtomicLong clock = new AtomicLong(10_000L);

    @AfterEach
    void clearMark() {
        WiredExecutionGuard.clearPlayerEvent();
    }

    @Test
    void aPlayerGetsFiveEventsOfATypePerSecond() {
        Room room = room(5);
        WiredExecutionGuard guard = guard(1_000);

        for (int i = 0; i < WiredExecutionGuard.PLAYER_EVENTS_PER_SECOND; i++) {
            assertTrue(enterAsPlayer(guard, room, 77));
        }
        assertFalse(enterAsPlayer(guard, room, 77));
        // Another player, or the same one a second later, is not affected.
        assertTrue(enterAsPlayer(guard, room, 78));
        this.clock.addAndGet(1_000L);
        assertTrue(enterAsPlayer(guard, room, 77));
    }

    @Test
    void playersFloodingARoomAreDroppedButNeverBanIt() {
        Room room = room(6);
        WiredExecutionGuard guard = guard(3);

        int admitted = 0;
        for (int player = 1; player <= 10; player++) {
            if (enterAsPlayer(guard, room, player)) admitted++;
        }

        assertTrue(admitted == 3);
        assertFalse(guard.isRoomBanned(room.getId()));
    }

    @Test
    void aWiredLoopOverTheLimitStillBansTheRoom() {
        Room room = room(7);
        WiredExecutionGuard guard = guard(3);

        for (int i = 0; i < 5; i++) {
            if (guard.tryEnter(room, WiredEvent.Type.USER_CLICKS_FURNI, WiredExecutionGuard.EntryKind.EVENT)) {
                guard.exit(room.getId());
            }
        }

        assertTrue(guard.isRoomBanned(room.getId()));
    }

    private boolean enterAsPlayer(WiredExecutionGuard guard, Room room, int roomUnitId) {
        WiredExecutionGuard.markPlayerEvent(roomUnitId);
        boolean admitted = guard.tryEnter(room, WiredEvent.Type.USER_CLICKS_FURNI, WiredExecutionGuard.EntryKind.EVENT);
        WiredExecutionGuard.clearPlayerEvent();
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
