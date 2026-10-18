package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import org.junit.jupiter.api.Test;

/** The execution limit counts its window in whole wired pulses, as Habbo does. */
class WiredExtraExecutionLimitPulseTest {

    @Test
    void twoPerFourPulsesLetsSixFiringsOneSecondApartAllRun() {
        WiredExtraExecutionLimit limit = limit(2, 4);

        for (long now : new long[] {2, 1005, 2002, 3002, 4000, 5002}) {
            assertTrue(limit.tryAcquireExecutionSlot(now), "at " + now);
        }
    }

    @Test
    void firingsInsideTheSamePulsesAreStillCapped() {
        WiredExtraExecutionLimit limit = limit(2, 4);

        assertTrue(limit.tryAcquireExecutionSlot(0));
        assertTrue(limit.tryAcquireExecutionSlot(400));
        assertFalse(limit.tryAcquireExecutionSlot(900));
        assertFalse(limit.canExecuteAt(1999));
        assertTrue(limit.canExecuteAt(2000));
    }

    private static WiredExtraExecutionLimit limit(int executions, int pulses) {
        WiredExtraExecutionLimit limit = new WiredExtraExecutionLimit(1, 1, mock(Item.class), "", 0, 0);
        limit.saveData(new WiredSettings(new int[] {executions, pulses}, "", new int[0], 0), null);
        return limit;
    }
}
