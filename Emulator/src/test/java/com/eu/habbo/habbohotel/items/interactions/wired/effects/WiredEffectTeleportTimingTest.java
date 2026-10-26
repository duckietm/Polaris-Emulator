package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredEffectTeleportTimingTest {

    @Test
    void theBoundaryIsTheFirstRoomCycleStrictlyAfterTheFiring() {
        assertEquals(1500L, WiredEffectTeleport.nextRoomBoundary(1000L, 1200L));
        // A firing on a boundary counts from the next one.
        assertEquals(2000L, WiredEffectTeleport.nextRoomBoundary(1000L, 1500L));
        assertEquals(1500L, WiredEffectTeleport.nextRoomBoundary(1000L, 1000L));
        // No cycle yet: half a second from now.
        assertEquals(700L, WiredEffectTeleport.nextRoomBoundary(0L, 200L));
    }

    @Test
    void anOnBoundaryFiringLandsTwoSecondsLaterAndFastOneSecondLater() {
        long firing = 1500L;
        long boundary = WiredEffectTeleport.nextRoomBoundary(1000L, firing);

        assertEquals(2000L, boundary + WiredEffectTeleport.LAND_AFTER_BOUNDARY_MS - firing);
        assertEquals(3500L, boundary + WiredEffectTeleport.EFFECT_OFF_AFTER_BOUNDARY_MS - firing);
        assertEquals(1000L, boundary + WiredEffectTeleport.FAST_LAND_AFTER_BOUNDARY_MS - firing);
    }

    @Test
    void fastModeRepublishesOnTheBoundaryAndNormalModeASecondLater() {
        long[] fast = WiredEffectTeleport.gridDelays(2000L, 1800L, true);
        assertEquals(200L, fast[0]);
        assertEquals(700L, fast[1]);
        assertEquals(1200L, fast[2]);

        long[] normal = WiredEffectTeleport.gridDelays(2000L, 1800L, false);
        assertEquals(1200L, normal[0]);
        assertEquals(1700L, normal[1]);
        assertEquals(3200L, normal[2]);
    }

    @Test
    void aTeleportDuringTheGlowSendsNoNewEffect() {
        RoomUnit unit = new RoomUnit();

        assertTrue(WiredEffectTeleport.tryStartGlow(unit, 10_000L, 3000L));
        assertFalse(WiredEffectTeleport.tryStartGlow(unit, 12_999L, 3000L));
        assertTrue(WiredEffectTeleport.tryStartGlow(unit, 13_000L, 3000L));
    }

    @Test
    void teleportToLandsAUserWhoAlreadyStandsOnTheTargetButTheClassicTimingDoesNot() {
        RoomTile tile = new RoomTile((short) 3, (short) 4, (short) 0, RoomTileState.OPEN, true);

        assertEquals(1, landingsScheduled(tile, true));
        assertEquals(0, landingsScheduled(tile, false));
    }

    @Test
    void normalTeleportToRaisesWalksOnAtTheRepublishAndFastModeAtTheLanding() throws Exception {
        assertWalkOnAtRepublish(false, true);
        assertWalkOnAtRepublish(true, false);
    }

    private static void assertWalkOnAtRepublish(boolean fast, boolean expected) throws Exception {
        RoomTile from = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile to = new RoomTile((short) 3, (short) 4, (short) 0, RoomTileState.OPEN, true);
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        HabboItem target = mock(HabboItem.class);
        when(room.getTopItemAt(3, 4)).thenReturn(target);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(from);
        unit.setInRoom(true);
        ThreadPooling threading = mock(ThreadPooling.class);
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> {
                    tasks.add(invocation.getArgument(0));
                    return null;
                })
                .when(threading)
                .run(any(Runnable.class), anyLong());

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));
            WiredEffectTeleport.teleportUnitToTile(unit, to, fast);
        }

        RoomUnitTeleport landing = (RoomUnitTeleport) tasks.stream()
                .filter(task -> task instanceof RoomUnitTeleport)
                .findFirst()
                .orElseThrow();
        Field walkOn = RoomUnitTeleport.class.getDeclaredField("walkOnAtLanding");
        walkOn.setAccessible(true);
        assertEquals(!expected, walkOn.getBoolean(landing));

        for (Runnable task : tasks) {
            if (!(task instanceof RoomUnitTeleport) && !(task instanceof SendRoomUnitEffectComposer)) {
                try {
                    task.run();
                } catch (RuntimeException ignored) {
                    // the status composer needs a full room; only the walk-on matters here
                }
            }
        }

        verify(target, times(expected ? 1 : 0)).onWalkOn(eq(unit), eq(room), any());
    }

    private static int landingsScheduled(RoomTile tile, boolean onRoomGrid) {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(tile);
        ThreadPooling threading = mock(ThreadPooling.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            if (onRoomGrid) {
                WiredEffectTeleport.teleportUnitToTile(unit, tile, false);
            } else {
                WiredEffectTeleport.teleportUnitToTile(unit, tile);
            }
        }

        return (int) mockingDetails(threading).getInvocations().stream()
                .filter(invocation ->
                        invocation.getArguments().length > 0 && invocation.getArgument(0) instanceof RoomUnitTeleport)
                .count();
    }
}
