package com.eu.habbo.habbohotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The clock trigger matches a target expressed in half seconds, so the counter has to visit them.
 * It used to re-align to whole seconds on every tick, which left the odd half of the slider - 60 of
 * its 120 positions - impossible to reach.
 */
class InteractionGameUpCounterTest {

    private static InteractionGameUpCounter counter() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);

        return new InteractionGameUpCounter(1, 1, base, "0\t5999", 0, 0);
    }

    @Test
    void theCounterVisitsEveryHalfSecond() {
        InteractionGameUpCounter clock = counter();
        List<Integer> visited = new ArrayList<>();

        for (int tick = 0; tick < 4; tick++) {
            clock.advanceCounterInMs((int) clock.getNextTickDelayMs());
            visited.add(clock.getCurrentTimeInMs());
        }

        assertEquals(List.of(500, 1000, 1500, 2000), visited);
    }

    @Test
    void wholeSecondTargetsStayReachable() {
        InteractionGameUpCounter clock = counter();
        List<Integer> visited = new ArrayList<>();

        for (int tick = 0; tick < 6; tick++) {
            clock.advanceCounterInMs((int) clock.getNextTickDelayMs());
            visited.add(clock.getCurrentTimeInMs());
        }

        assertEquals(true, visited.contains(1000));
        assertEquals(true, visited.contains(2000));
        assertEquals(true, visited.contains(3000));
    }
}
