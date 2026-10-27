package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder;
import com.eu.habbo.plugin.PluginManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Habbo ignores a walk goal that has no route while the avatar walks: the walk goes on to its old goal. */
class RoomUnitUnreachableGoalTest {

    private final RoomTile start = tile(0, 0);
    private final RoomTile step = tile(1, 0);
    private final RoomTile oldGoal = tile(2, 0);
    private final RoomTile island = tile(6, 3);
    private final RoomTile reachable = tile(0, 1);
    private final Pathfinder pathfinder = mock(Pathfinder.class);

    @Test
    void aGoalWithNoRouteKeepsTheCurrentWalk() {
        this.withUnit(unit -> {
            unit.setStatus(RoomUnitStatus.MOVE, "1,0,0.0");
            Deque<RoomTile> walk = unit.getPath();

            unit.setGoalLocation(this.island);

            assertSame(this.oldGoal, unit.getGoal());
            assertSame(walk, unit.getPath());
            assertEquals(List.of(this.step, this.oldGoal), List.copyOf(unit.getPath()));
        });
    }

    @Test
    void aGoalWithNoRouteWhileStandingStaysPut() {
        this.withUnit(unit -> {
            unit.setPath(new ConcurrentLinkedDeque<>());

            unit.setGoalLocation(this.island);

            assertSame(this.start, unit.getGoal());
            assertTrue(unit.getPath().isEmpty());
        });
    }

    @Test
    void aReachableGoalReplacesTheWalk() {
        this.withUnit(unit -> {
            unit.setStatus(RoomUnitStatus.MOVE, "1,0,0.0");

            unit.setGoalLocation(this.reachable);

            assertSame(this.reachable, unit.getGoal());
            assertEquals(List.of(this.reachable), List.copyOf(unit.getPath()));
        });
    }

    private void withUnit(java.util.function.Consumer<RoomUnit> body) {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(room.getItemsAt(any(RoomTile.class))).thenReturn(Set.of());
        when(layout.getPathfinder()).thenReturn(this.pathfinder);
        when(this.pathfinder.findPath(any(), eq(this.island), any(), any())).thenReturn(new ArrayDeque<>());
        when(this.pathfinder.findPath(any(), eq(this.oldGoal), any(), any()))
                .thenAnswer(i -> new ArrayDeque<>(List.of(this.step, this.oldGoal)));
        when(this.pathfinder.findPath(any(), eq(this.reachable), any(), any()))
                .thenReturn(new ArrayDeque<>(List.of(this.reachable)));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            RoomUnit unit = new RoomUnit();
            unit.setRoom(room);
            unit.setLocation(this.start);
            unit.setGoalLocation(this.oldGoal);
            body.accept(unit);
        }
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }
}
