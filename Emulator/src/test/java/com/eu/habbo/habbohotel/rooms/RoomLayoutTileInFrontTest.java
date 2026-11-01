package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The tile in front is offset + 1 steps away. It used to be walked one step at a time, so an offset of
 * Integer.MAX_VALUE (a wired box saved with a huge spacing) never left the loop and held a wired thread.
 */
class RoomLayoutTileInFrontTest {

    private static final RoomTile START = new RoomTile((short) 5, (short) 5, (short) 0, RoomTileState.OPEN, true);

    @Test
    void aHugeOffsetAnswersAtOnceWithNoTile() {
        RoomLayout layout = mock(RoomLayout.class, CALLS_REAL_METHODS);
        doReturn(null).when(layout).getTile(anyShort(), anyShort());

        assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> assertNull(layout.getTileInFront(START, 2, Integer.MAX_VALUE)));
    }

    @Test
    void anOffsetCountsOneStepMoreThanItsValue() {
        RoomLayout layout = mock(RoomLayout.class, CALLS_REAL_METHODS);
        RoomTile ahead = new RoomTile((short) 8, (short) 5, (short) 0, RoomTileState.OPEN, true);
        doReturn(null).when(layout).getTile(anyShort(), anyShort());
        doReturn(ahead).when(layout).getTile((short) 8, (short) 5);

        assertSame(ahead, layout.getTileInFront(START, 2, 2));
    }

    @Test
    void aNegativeOffsetStaysOnTheTile() {
        RoomLayout layout = mock(RoomLayout.class, CALLS_REAL_METHODS);
        doReturn(null).when(layout).getTile(anyShort(), anyShort());
        doReturn(START).when(layout).getTile((short) 5, (short) 5);

        assertSame(START, layout.getTileInFront(START, 2, -1));
    }
}
