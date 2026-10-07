package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.HeavyWiredRooms;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The monitor's heavy mark reaches the rooms the wired heavy workers take over. */
class WiredHeavyRoomRoutingTest {

    @Test
    void aRoomMarkedHeavyIsHandedToTheHeavyWorkers() {
        int roomId = 970_201;
        AtomicLong clock = new AtomicLong(10_000L);
        WiredExecutionGuard guard = new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(10, 100, 10_000L, 0L, 1_000, 100, 10, 50, 150, 70, 5, 2, 60),
                clock::get,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});
        try {
            // Six seconds at 80% of the room's budget: heavy after five such windows.
            for (int window = 0; window < 6; window++) {
                guard.diagnostics(roomId).tryConsumeExecutionBudget(80, clock.get(), "test", 0, "busy");
                clock.addAndGet(1_000L);
            }
            guard.diagnostics(roomId).tryConsumeExecutionBudget(1, clock.get(), "test", 0, "roll");
            assertTrue(HeavyWiredRooms.isHeavy(roomId, clock.get()));

            // Room unloaded: forgotten.
            guard.clearRoomDiagnostics(roomId);
            assertFalse(HeavyWiredRooms.isHeavy(roomId, clock.get()));
        } finally {
            HeavyWiredRooms.forget(roomId);
        }
    }
}
