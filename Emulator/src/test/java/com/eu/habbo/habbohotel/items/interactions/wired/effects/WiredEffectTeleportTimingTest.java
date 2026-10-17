package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import org.junit.jupiter.api.Test;

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
}
