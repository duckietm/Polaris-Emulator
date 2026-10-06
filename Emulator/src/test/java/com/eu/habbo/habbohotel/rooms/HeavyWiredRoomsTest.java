package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A room is heavy from the monitor's mark until it has been calm for a while. */
class HeavyWiredRoomsTest {

    @Test
    void staysHeavyUntilCalmLongEnough() {
        int roomId = 970_001;
        assertFalse(HeavyWiredRooms.isHeavy(roomId, 1_000L));

        HeavyWiredRooms.mark(roomId, true, 1_000L);
        assertTrue(HeavyWiredRooms.isHeavy(roomId, 2_000L));

        HeavyWiredRooms.mark(roomId, false, 5_000L);
        assertTrue(HeavyWiredRooms.isHeavy(roomId, 5_000L + HeavyWiredRooms.CALM_BEFORE_RELEASE_MS - 1));
        assertFalse(HeavyWiredRooms.isHeavy(roomId, 5_000L + HeavyWiredRooms.CALM_BEFORE_RELEASE_MS));
        assertFalse(HeavyWiredRooms.isHeavy(roomId, 5_000L + HeavyWiredRooms.CALM_BEFORE_RELEASE_MS + 1));
    }

    @Test
    void heavyAgainBeforeReleaseKeepsIt() {
        int roomId = 970_002;
        HeavyWiredRooms.mark(roomId, true, 1_000L);
        HeavyWiredRooms.mark(roomId, false, 2_000L);
        HeavyWiredRooms.mark(roomId, true, 3_000L);

        assertTrue(HeavyWiredRooms.isHeavy(roomId, 3_000L + 10 * HeavyWiredRooms.CALM_BEFORE_RELEASE_MS));
        HeavyWiredRooms.forget(roomId);
        assertFalse(HeavyWiredRooms.isHeavy(roomId, 3_000L));
    }
}
