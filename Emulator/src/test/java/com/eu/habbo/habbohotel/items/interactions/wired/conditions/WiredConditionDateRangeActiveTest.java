package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The official date range condition: an end of 0 is not an open range, it never passes. */
class WiredConditionDateRangeActiveTest {
    private static final int NOW = 1_800_000_000;
    private static final int PAST = NOW - 3600;
    private static final int FUTURE = NOW + 3600;

    @Test
    void rangesWithAFutureEndPass() {
        assertTrue(WiredConditionDateRangeActive.isActive(0, FUTURE, NOW));
        assertTrue(WiredConditionDateRangeActive.isActive(PAST, FUTURE, NOW));
    }

    @Test
    void anEndOfZeroNeverPasses() {
        assertFalse(WiredConditionDateRangeActive.isActive(0, 0, NOW));
        assertFalse(WiredConditionDateRangeActive.isActive(PAST, 0, NOW));
        assertFalse(WiredConditionDateRangeActive.isActive(FUTURE, 0, NOW));
    }

    @Test
    void anEndInThePastFails() {
        assertFalse(WiredConditionDateRangeActive.isActive(0, PAST, NOW));
        assertFalse(WiredConditionDateRangeActive.isActive(PAST - 10, PAST, NOW));
    }
}
