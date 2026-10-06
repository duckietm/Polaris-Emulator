package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Without a ban (the default), events over the rate limit are dropped and the room keeps running. */
class WiredRateLimitDropTest {

    @Test
    void overTheLimitTheRestOfTheWindowIsDroppedAndTheRoomKeepsRunning() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(970_301);
        AtomicLong clock = new AtomicLong(10_000L);
        List<Boolean> banDecisions = new ArrayList<>();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(10, 3, 1_000L, 0L, 1_000, 100, 10, 50, 150, 70, 5, 2, 60),
                clock::get,
                (ignoredRoom, eventType, count, limits, banned) -> banDecisions.add(banned),
                (ignoredRoom, eventType, kind, depth, maximum) -> {});

        for (int i = 0; i < 3; i++) {
            assertTrue(enterAndExit(guard, room));
        }
        assertFalse(enterAndExit(guard, room));
        assertFalse(enterAndExit(guard, room));

        assertFalse(guard.isRoomBanned(room.getId()));
        assertEquals(List.of(false), banDecisions);
        var history = guard.snapshot(room.getId()).getHistory();
        assertTrue(history.stream().anyMatch(entry -> entry.getType() == WiredRoomDiagnostics.Type.EXECUTION_CAP));
        assertFalse(history.stream().anyMatch(entry -> entry.getType() == WiredRoomDiagnostics.Type.KILLED));

        clock.addAndGet(1_001L);
        assertTrue(enterAndExit(guard, room));
    }

    private static boolean enterAndExit(WiredExecutionGuard guard, Room room) {
        boolean admitted = guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT);
        if (admitted) {
            guard.exit(room.getId());
        }
        return admitted;
    }
}
