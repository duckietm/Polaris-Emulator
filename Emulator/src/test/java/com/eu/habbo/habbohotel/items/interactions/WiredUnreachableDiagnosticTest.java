package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A highscore board placed where no game can end stays empty forever and used to say nothing about
 * why, which is indistinguishable from a broken board.
 */
class WiredUnreachableDiagnosticTest {

    @Test
    @DisplayName("a room with neither timer nor up-counter cannot end a game")
    void aRoomWithNoTimerCannotEndAGame() {
        assertFalse(InteractionWiredHighscore.canEndAGame(room(Set.of(), Set.of())));
    }

    @Test
    @DisplayName("a game timer is enough")
    void aTimerIsEnough() {
        assertTrue(InteractionWiredHighscore.canEndAGame(room(Set.of(mock(InteractionGameTimer.class)), Set.of())));
    }

    @Test
    @DisplayName("an up-counter is enough on its own")
    void anUpCounterIsEnough() {
        assertTrue(InteractionWiredHighscore.canEndAGame(room(Set.of(), Set.of(mock(InteractionGameUpCounter.class)))));
    }

    @Test
    @DisplayName("a room that is not there cannot end a game either, and does not throw")
    void aMissingRoomIsHandled() {
        assertFalse(InteractionWiredHighscore.canEndAGame(null));

        Room roomWithoutTypes = mock(Room.class);
        when(roomWithoutTypes.getRoomSpecialTypes()).thenReturn(null);
        assertFalse(InteractionWiredHighscore.canEndAGame(roomWithoutTypes));
    }

    @Test
    @DisplayName("the note reaches the monitor as a warning, with its reason and source")
    void theNoteReachesTheMonitor() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);

        diagnostics.recordUnreachable(1_000L, "no game timer", "highscore_mostwin*1", 4338);

        WiredRoomDiagnostics.LogEntry entry = unreachableEntry(diagnostics);

        assertNotNull(entry, "the monitor must carry an UNREACHABLE entry");
        assertEquals(WiredRoomDiagnostics.Severity.WARNING, entry.getSeverity(), "nothing has failed");
        assertEquals(1, entry.getCount());
        assertEquals("no game timer", entry.getLatestReason());
        assertEquals("highscore_mostwin*1", entry.getLatestSourceLabel());
        assertEquals(4338, entry.getLatestSourceId());
    }

    @Test
    @DisplayName("repeat notes aggregate into a count rather than a wall of lines")
    void repeatNotesAggregate() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);

        diagnostics.recordUnreachable(1_000L, "no game timer", "board", 1);
        diagnostics.recordUnreachable(1_100L, "no game timer", "board", 2);

        WiredRoomDiagnostics.LogEntry entry = unreachableEntry(diagnostics);

        assertNotNull(entry);
        assertEquals(2, entry.getCount());
        assertEquals(2, entry.getLatestSourceId(), "the latest source wins");
    }

    private static WiredRoomDiagnostics.LogEntry unreachableEntry(WiredRoomDiagnostics diagnostics) {
        return diagnostics.snapshot(0, 10, 0L, 1_200L).getLogs().stream()
                .filter(log -> log.getType() == WiredRoomDiagnostics.Type.UNREACHABLE)
                .filter(log -> log.getCount() > 0)
                .findFirst()
                .orElse(null);
    }

    private static Room room(Set<InteractionGameTimer> timers, Set<InteractionGameUpCounter> counters) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);

        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getItemsOfType(InteractionGameTimer.class)).thenReturn(new LinkedHashSet<HabboItem>(timers));
        when(specialTypes.getItemsOfType(InteractionGameUpCounter.class))
                .thenReturn(new LinkedHashSet<HabboItem>(counters));

        return room;
    }
}
