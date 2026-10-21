package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** What the stats page adds up from the active rooms: the slowest room, the wired load and the five busiest rooms. */
class EmulatorStatsRoomSectionTest {

    @Test
    void noRoomsGiveAnEmptySection() {
        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            EmulatorStatsService.RoomSection section = EmulatorStatsService.collectRoomSection(List.of(), null);

            assertTrue(section.activeRooms().isEmpty());
            assertTrue(section.wiredRooms().isEmpty());
            assertTrue(section.wiredTopRooms().isEmpty());
            assertEquals(0D, section.averageRoomCycleMs());
            assertEquals(0D, section.worstRoomCycleMs());
            assertEquals(0, section.worstRoomCycleRoomId());
            assertEquals("-", section.worstRoomCycleRoomName());
        }
    }

    @Test
    void theSlowestRoomAndTheAverageComeFromTheCycleTimes() {
        Room fast = room(1, "Fast", 2.0);
        Room slow = room(2, "Slow", 6.0);
        Room idle = room(3, "Idle", -1.0);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            EmulatorStatsService.RoomSection section =
                    EmulatorStatsService.collectRoomSection(List.of(fast, slow, idle), null);

            assertEquals(3, section.activeRooms().size());
            assertEquals(2, section.worstRoomCycleRoomId());
            assertEquals("Slow", section.worstRoomCycleRoomName());
            assertEquals(6.0, section.worstRoomCycleMs());
            assertEquals((2.0 + 6.0 + 0.0) / 3, section.averageRoomCycleMs(), 1e-9, "a negative time counts as zero");
        }
    }

    @Test
    void aRoomWithoutWiredLoadIsLeftOutOfTheWiredRows() {
        Room quiet = room(1, "Quiet", 1.0);
        Room busy = room(2, "Busy", 1.0);
        WiredTickService ticks = mock(WiredTickService.class);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            WiredRoomDiagnostics.Snapshot quietLoad = diagnostics(0, 0, 0, 0, 0, false);
            WiredRoomDiagnostics.Snapshot busyLoad = diagnostics(8, 20, 50, 100, 3, true);
            wired.when(() -> WiredManager.getDiagnosticsSnapshot(1)).thenReturn(quietLoad);
            wired.when(() -> WiredManager.getDiagnosticsSnapshot(2)).thenReturn(busyLoad);

            EmulatorStatsService.RoomSection section =
                    EmulatorStatsService.collectRoomSection(List.of(quiet, busy), ticks);

            assertEquals(2, section.activeRooms().size());
            assertEquals(1, section.wiredRooms().size());
            assertEquals(2, section.wiredRooms().get(0).roomId);
            assertEquals(50, section.wiredRooms().get(0).usagePercent, "50 of a limit of 100");
            assertEquals(3L, section.totalDelayedEventsPending());
            assertEquals(1, section.heavyWiredRooms());
            assertEquals(50D * 1000 / 1000, section.wiredActivityPerSecond(), 1e-9);
        }
    }

    @Test
    void aRoomOverTheAverageThresholdCountsAsOverloaded() {
        Room over = room(1, "Over", 1.0);
        Room under = room(2, "Under", 1.0);

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            // averageMs, peakMs, usage, limit, delayed, heavy ; the threshold is 5 ms
            WiredRoomDiagnostics.Snapshot atThreshold = diagnostics(5, 9, 1, 10, 0, false);
            WiredRoomDiagnostics.Snapshot belowThreshold = diagnostics(4, 9, 1, 10, 0, false);
            wired.when(() -> WiredManager.getDiagnosticsSnapshot(1)).thenReturn(atThreshold);
            wired.when(() -> WiredManager.getDiagnosticsSnapshot(2)).thenReturn(belowThreshold);

            EmulatorStatsService.RoomSection section =
                    EmulatorStatsService.collectRoomSection(List.of(over, under), null);

            assertEquals(1, section.overloadedWiredRooms());
            assertTrue(section.wiredRooms().get(0).overloaded);
            assertEquals(false, section.wiredRooms().get(1).overloaded);
        }
    }

    @Test
    void theTopListHoldsFiveRoomsBusiestFirst() {
        List<EmulatorStatsService.WiredTopRoomRow> rows = new ArrayList<>();
        rows.add(top(1, 10, 5, 5));
        rows.add(top(2, 90, 1, 1));
        rows.add(top(3, 90, 7, 2));
        rows.add(top(4, 90, 7, 9));
        rows.add(top(5, 50, 3, 3));
        rows.add(top(6, 20, 2, 2));
        rows.add(top(7, 5, 1, 1));

        List<EmulatorStatsService.WiredTopRoomRow> topFive = EmulatorStatsService.topWiredRooms(rows);

        assertEquals(5, topFive.size());
        // the same usage: the slower average first, then the higher peak
        assertEquals(
                List.of(4, 3, 2, 5, 6), topFive.stream().map(row -> row.roomId).toList());
        assertEquals(7, rows.size(), "the list given is left as it was");
    }

    private static Room room(int id, String name, double cycleMs) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getName()).thenReturn(name);
        room.lastCycleCpuMs = cycleMs;
        room.lastCycleThread = "room-" + id;
        return room;
    }

    private static WiredRoomDiagnostics.Snapshot diagnostics(
            int averageMs, int peakMs, int usage, int limit, int delayed, boolean heavy) {
        WiredRoomDiagnostics.Snapshot snapshot = mock(WiredRoomDiagnostics.Snapshot.class);
        when(snapshot.getAverageExecutionMs()).thenReturn(averageMs);
        when(snapshot.getPeakExecutionMs()).thenReturn(peakMs);
        when(snapshot.getUsageCurrentWindow()).thenReturn(usage);
        when(snapshot.getUsageLimitPerWindow()).thenReturn(limit);
        when(snapshot.getUsageWindowMs()).thenReturn(1000);
        when(snapshot.getDelayedEventsPending()).thenReturn(delayed);
        when(snapshot.getOverloadAverageThresholdMs()).thenReturn(5);
        when(snapshot.isHeavy()).thenReturn(heavy);
        return snapshot;
    }

    private static EmulatorStatsService.WiredTopRoomRow top(int id, int usage, int averageMs, int peakMs) {
        return new EmulatorStatsService.WiredTopRoomRow(id, "room " + id, usage, averageMs, peakMs, 0, 0D, false);
    }
}
