package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** The arrival glow of Teleport to Room ends on the linked room's third tick. */
class RoomForwardGlowTest {

    @Test
    void theThirdTickIsTwoTicksAfterTheNextOne() {
        Room room = mock(Room.class);
        when(room.getCycleTimestamp()).thenReturn(1000L);

        assertEquals(300L, RoomForwardGlow.untilTick(room, 1200L, 1));
        assertEquals(1300L, RoomForwardGlow.untilTick(room, 1200L, RoomForwardGlow.ARRIVAL_TICKS));
    }

    @Test
    void aRoomThatHasNotTickedYetCountsFromNow() {
        Room room = mock(Room.class);

        assertEquals(1500L, RoomForwardGlow.untilTick(room, 5000L, RoomForwardGlow.ARRIVAL_TICKS));
    }
}
